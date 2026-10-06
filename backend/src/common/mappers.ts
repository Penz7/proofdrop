import { Device, Evidence, Order, User } from '@prisma/client';

const ms = (d: Date | null | undefined): number | null => (d ? d.getTime() : null);

export const orderCode = (codeNumber: number) => `PD-${1000 + codeNumber}`;

export type OrderWithCourier = Order & { courier: Pick<User, 'id' | 'name'> | null };
export type DeviceWithHolder = Device & { holder: Pick<User, 'id' | 'name'> | null };

/** API shape from docs/API.md: timestamps are epoch milliseconds. */
export function toOrderDto(o: OrderWithCourier) {
  return {
    id: o.id,
    code: orderCode(o.codeNumber),
    customerName: o.customerName,
    customerPhone: o.customerPhone,
    address: o.address,
    latitude: o.latitude,
    longitude: o.longitude,
    items: o.items,
    status: o.status,
    beaconId: o.beaconId,
    courierId: o.courierId,
    courierName: o.courier?.name ?? null,
    assignedAt: (o.assignedAt ?? o.createdAt).getTime(),
    deliveredAt: ms(o.deliveredAt),
  };
}
export type OrderDto = ReturnType<typeof toOrderDto>;

export function toDeviceDto(d: DeviceWithHolder) {
  return {
    id: d.id,
    name: d.name,
    type: d.type,
    serial: d.serial,
    batteryPct: d.batteryPct,
    holderId: d.holderId,
    holderName: d.holder?.name ?? null,
    checkedOutAt: ms(d.checkedOutAt),
  };
}

export function toEvidenceRecord(e: Evidence) {
  return {
    id: e.id,
    sequence: e.sequence,
    orderId: e.orderId,
    fileName: e.fileName,
    fileSha256: e.fileSha256,
    capturedAt: Number(e.capturedAt),
    latitude: e.latitude,
    longitude: e.longitude,
    bleVerified: e.bleVerified,
    previousHash: e.previousHash,
    recordHash: e.recordHash,
  };
}

export type EvidenceWithRefs = Evidence & {
  courier: Pick<User, 'id' | 'name'>;
  order: Pick<Order, 'codeNumber'>;
};

export function toEvidenceListItem(e: EvidenceWithRefs) {
  return {
    ...toEvidenceRecord(e),
    courierId: e.courierId,
    courierName: e.courier.name,
    orderCode: orderCode(e.order.codeNumber),
    receivedAt: e.receivedAt.getTime(),
    sizeBytes: e.sizeBytes,
    orderMatched: e.orderMatched,
  };
}

export const courierSelect = { select: { id: true, name: true } } as const;
