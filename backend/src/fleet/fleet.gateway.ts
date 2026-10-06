import { Logger } from '@nestjs/common';
import {
  ConnectedSocket,
  MessageBody,
  OnGatewayConnection,
  OnGatewayDisconnect,
  SubscribeMessage,
  WebSocketGateway,
} from '@nestjs/websockets';
import { IncomingMessage } from 'http';
import { WebSocket } from 'ws';
import { AuthService } from '../auth/auth.module';
import { AuthUser, extractToken } from '../auth/auth.types';
import { CourierPosition, CourierStatus, FleetService } from './fleet.service';

const UNAUTHORIZED = 4401;
const STATUSES: CourierStatus[] = ['IDLE', 'EN_ROUTE', 'DELIVERING', 'OFFLINE'];

type FleetSocket = WebSocket & { user?: AuthUser; unsubscribe?: () => void };

/**
 * Raw WebSocket at `/fleet?access_token=<JWT>` (NestJS WsAdapter, `{event, data}` envelopes).
 * Everyone receives `fleet` snapshots; couriers send `position` reports.
 */
// Position reports are tiny; cap frames so a client can't make us buffer megabytes.
@WebSocketGateway({ path: '/fleet', maxPayload: 4 * 1024 })
export class FleetGateway implements OnGatewayConnection, OnGatewayDisconnect {
  private readonly logger = new Logger(FleetGateway.name);

  constructor(
    private readonly auth: AuthService,
    private readonly fleet: FleetService,
  ) {}

  async handleConnection(client: FleetSocket, request: IncomingMessage) {
    // Browsers cannot set headers on a WebSocket, so the query token is allowed here.
    const token = extractToken(request.headers as Record<string, unknown>, request.url, true);
    try {
      if (!token) throw new Error('missing token');
      client.user = await this.auth.authenticate(token);
    } catch {
      client.close(UNAUTHORIZED, 'Unauthorized');
      return;
    }
    const send = (snapshot: CourierPosition[]) => {
      if (client.readyState === WebSocket.OPEN) client.send(JSON.stringify({ event: 'fleet', data: snapshot }));
    };
    send(this.fleet.snapshot());
    client.unsubscribe = this.fleet.subscribe(send);
    this.logger.debug(`Fleet socket connected: ${client.user.email}`);
  }

  handleDisconnect(client: FleetSocket) {
    client.unsubscribe?.();
  }

  @SubscribeMessage('position')
  async onPosition(@ConnectedSocket() client: FleetSocket, @MessageBody() data: unknown) {
    const user = client.user;
    if (!user || user.role !== 'COURIER') return; // dispatchers only listen
    const d = (data ?? {}) as { latitude?: unknown; longitude?: unknown; status?: unknown };
    const lat = Number(d.latitude);
    const lng = Number(d.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) return;
    const status = STATUSES.includes(d.status as CourierStatus) ? (d.status as CourierStatus) : 'EN_ROUTE';
    await this.fleet.report(user, lat, lng, status);
  }
}
