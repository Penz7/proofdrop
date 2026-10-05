import { Global, Injectable, Module } from '@nestjs/common';
import { Response } from 'express';
import { filter, Observable, Subject } from 'rxjs';

/** Something one courier's `/api/assignments` stream should hear about. */
export interface CourierEvent {
  courierId: string;
  type: 'assignment' | 'unassigned';
  data: unknown;
}

/** Something every dispatcher's `/api/dispatch/events` stream should hear about. */
export interface DispatchEvent {
  type: 'order' | 'evidence';
  data: unknown;
}

/** In-process event bus. A multi-instance deployment would back this with Redis pub/sub. */
@Injectable()
export class EventsService {
  private readonly courier$ = new Subject<CourierEvent>();
  private readonly dispatch$ = new Subject<DispatchEvent>();

  emitCourier(event: CourierEvent) {
    this.courier$.next(event);
  }

  emitDispatch(event: DispatchEvent) {
    this.dispatch$.next(event);
  }

  forCourier(courierId: string): Observable<CourierEvent> {
    return this.courier$.pipe(filter((e) => e.courierId === courierId));
  }

  forDispatchers(): Observable<DispatchEvent> {
    return this.dispatch$.asObservable();
  }
}

const PING_INTERVAL_MS = 25_000;

/**
 * Streams `events` to `res` as Server-Sent Events. Written by hand rather than with
 * Nest's @Sse so we can send `: ping` comment lines (see docs/API.md).
 */
export function streamSse(res: Response, events: Observable<{ type: string; data: unknown }>) {
  res.status(200);
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache, no-transform');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no'); // disable nginx buffering
  res.flushHeaders();
  res.write(': connected\n\n');

  const subscription = events.subscribe((event) => {
    res.write(`event: ${event.type}\ndata: ${JSON.stringify(event.data)}\n\n`);
  });
  const ping = setInterval(() => res.write(': ping\n\n'), PING_INTERVAL_MS);

  res.on('close', () => {
    clearInterval(ping);
    subscription.unsubscribe();
  });
}

@Global()
@Module({ providers: [EventsService], exports: [EventsService] })
export class EventsModule {}
