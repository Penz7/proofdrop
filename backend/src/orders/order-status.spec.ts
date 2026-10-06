import { canCourierTransition } from './order-status';

describe('canCourierTransition', () => {
  it.each([
    ['ASSIGNED', 'PICKED_UP'],
    ['ASSIGNED', 'DELIVERED'],
    ['ASSIGNED', 'FAILED'],
    ['PICKED_UP', 'DELIVERED'],
    ['PICKED_UP', 'FAILED'],
    ['FAILED', 'ASSIGNED'],
  ] as const)('allows %s -> %s', (from, to) => {
    expect(canCourierTransition(from, to)).toBe(true);
  });

  it.each([
    ['DELIVERED', 'ASSIGNED'],
    ['DELIVERED', 'FAILED'],
    ['PICKED_UP', 'ASSIGNED'],
    ['CREATED', 'PICKED_UP'],
    ['FAILED', 'DELIVERED'],
    ['ASSIGNED', 'CREATED'],
    ['CANCELLED', 'ASSIGNED'],
    ['CANCELLED', 'DELIVERED'],
    ['ASSIGNED', 'CANCELLED'],
  ] as const)('rejects %s -> %s', (from, to) => {
    expect(canCourierTransition(from, to)).toBe(false);
  });

  it('treats repeating the current status as idempotent', () => {
    expect(canCourierTransition('DELIVERED', 'DELIVERED')).toBe(true);
    expect(canCourierTransition('PICKED_UP', 'PICKED_UP')).toBe(true);
  });
});
