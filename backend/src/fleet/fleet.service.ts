import { Injectable, Logger, OnModuleDestroy, OnModuleInit } from '@nestjs/common';
import { PrismaService } from '../prisma/prisma.service';

export type CourierStatus = 'IDLE' | 'EN_ROUTE' | 'DELIVERING' | 'OFFLINE';

export interface CourierPosition {
  courierId: string;
  name: string;
  latitude: number;
  longitude: number;
  status: CourierStatus;
  updatedAt: number;
}

const OFFLINE_AFTER_MS = 2 * 60_000;
const VISIBLE_FOR_MS = 10 * 60_000;
const BROADCAST_INTERVAL_MS = 1_000;
// Re-broadcast now and then even without new reports so stale couriers flip to OFFLINE.
const STALENESS_REFRESH_MS = 30_000;
const MIN_REPORT_INTERVAL_MS = 1_000;

/** Live courier positions: in memory for speed, last position persisted on the user row. */
@Injectable()
export class FleetService implements OnModuleInit, OnModuleDestroy {
  private readonly logger = new Logger(FleetService.name);
  private readonly positions = new Map<string, CourierPosition>();
  private readonly listeners = new Set<(snapshot: CourierPosition[]) => void>();
  private dirty = false;
  private lastBroadcast = 0;
  private timer?: NodeJS.Timeout;

  constructor(private readonly prisma: PrismaService) {}

  async onModuleInit() {
    const since = new Date(Date.now() - VISIBLE_FOR_MS);
    const couriers = await this.prisma.user.findMany({
      where: { role: 'COURIER', lastSeenAt: { gte: since }, lastLat: { not: null }, lastLng: { not: null } },
    });
    for (const c of couriers) {
      this.positions.set(c.id, {
        courierId: c.id,
        name: c.name,
        latitude: c.lastLat!,
        longitude: c.lastLng!,
        status: (c.lastStatus as CourierStatus) ?? 'EN_ROUTE',
        updatedAt: c.lastSeenAt!.getTime(),
      });
    }
    this.timer = setInterval(() => this.tick(), BROADCAST_INTERVAL_MS);
  }

  onModuleDestroy() {
    clearInterval(this.timer);
  }

  async report(courier: { id: string; name: string }, latitude: number, longitude: number, status: CourierStatus) {
    const now = Date.now();
    // Each report also writes the user row; ignore bursts faster than the app's 5 s cadence allows.
    const previous = this.positions.get(courier.id);
    if (previous && now - previous.updatedAt < MIN_REPORT_INTERVAL_MS) return;
    this.positions.set(courier.id, { courierId: courier.id, name: courier.name, latitude, longitude, status, updatedAt: now });
    this.dirty = true;
    try {
      await this.prisma.user.update({
        where: { id: courier.id },
        data: { lastLat: latitude, lastLng: longitude, lastStatus: status, lastSeenAt: new Date(now) },
      });
    } catch (e) {
      this.logger.warn(`Could not persist position for ${courier.id}: ${(e as Error).message}`);
    }
  }

  /** Couriers seen in the last 10 minutes; silent for over 2 minutes = OFFLINE. */
  snapshot(now = Date.now()): CourierPosition[] {
    const result: CourierPosition[] = [];
    for (const p of this.positions.values()) {
      const age = now - p.updatedAt;
      if (age > VISIBLE_FOR_MS) continue;
      result.push(age > OFFLINE_AFTER_MS ? { ...p, status: 'OFFLINE' } : p);
    }
    return result.sort((a, b) => a.name.localeCompare(b.name));
  }

  lastPosition(courierId: string): CourierPosition | null {
    const p = this.positions.get(courierId);
    if (!p) return null;
    return Date.now() - p.updatedAt > OFFLINE_AFTER_MS ? { ...p, status: 'OFFLINE' } : p;
  }

  isOnline(courierId: string): boolean {
    const p = this.positions.get(courierId);
    return !!p && Date.now() - p.updatedAt <= OFFLINE_AFTER_MS;
  }

  subscribe(listener: (snapshot: CourierPosition[]) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private tick() {
    const now = Date.now();
    if (!this.dirty && now - this.lastBroadcast < STALENESS_REFRESH_MS) return;
    this.dirty = false;
    this.lastBroadcast = now;
    const snapshot = this.snapshot(now);
    for (const listener of this.listeners) listener(snapshot);
  }
}
