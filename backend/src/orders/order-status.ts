import { OrderStatus } from '@prisma/client';

/** Status changes a courier may make. Dispatcher assignment is handled separately. */
const COURIER_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  CREATED: [],
  ASSIGNED: ['PICKED_UP', 'DELIVERED', 'FAILED'],
  PICKED_UP: ['DELIVERED', 'FAILED'],
  FAILED: ['ASSIGNED'],
  DELIVERED: [],
  CANCELLED: [],
};

/** Same status again is allowed so offline retries are idempotent. */
export function canCourierTransition(from: OrderStatus, to: OrderStatus): boolean {
  return from === to || COURIER_TRANSITIONS[from].includes(to);
}
