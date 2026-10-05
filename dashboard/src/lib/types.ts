// Mirrors docs/API.md. All timestamps are epoch milliseconds.

export type Role = "COURIER" | "DISPATCHER";
export type OrderStatus = "CREATED" | "ASSIGNED" | "PICKED_UP" | "DELIVERED" | "FAILED";
export type DeviceType = "SCANNER" | "BODY_CAM" | "PRINTER" | "VEHICLE";
export type CourierStatus = "IDLE" | "EN_ROUTE" | "DELIVERING" | "OFFLINE";

export interface User {
  id: string;
  email: string;
  name: string;
  role: Role;
}

export interface Order {
  id: string;
  code: string;
  customerName: string;
  customerPhone: string | null;
  address: string;
  latitude: number;
  longitude: number;
  items: string;
  status: OrderStatus;
  beaconId: string | null;
  courierId: string | null;
  courierName: string | null;
  assignedAt: number;
  deliveredAt: number | null;
}

export interface Device {
  id: string;
  name: string;
  type: DeviceType;
  serial: string;
  batteryPct: number;
  holderId: string | null;
  holderName: string | null;
  checkedOutAt: number | null;
}

export interface CourierPosition {
  courierId: string;
  name: string;
  latitude: number;
  longitude: number;
  status: CourierStatus;
  updatedAt: number;
}

export interface Courier {
  id: string;
  name: string;
  email: string;
  online: boolean;
  lastPosition: CourierPosition | null;
  activeOrders: number;
}

export interface EvidenceItem {
  id: string;
  sequence: number;
  orderId: string;
  fileName: string;
  fileSha256: string;
  capturedAt: number;
  latitude: number | null;
  longitude: number | null;
  bleVerified: boolean;
  previousHash: string;
  recordHash: string;
  courierId: string;
  courierName: string;
  orderCode: string;
  receivedAt: number;
  sizeBytes: number;
}

export type ChainVerification =
  | { valid: true; count: number }
  | { valid: false; atSequence: number; reason: string };

export interface NewOrderInput {
  customerName: string;
  customerPhone?: string;
  address: string;
  latitude: number;
  longitude: number;
  items: string;
  beaconId?: string;
  courierId?: string;
}

export interface NewDeviceInput {
  id: string;
  name: string;
  type: DeviceType;
  serial: string;
  batteryPct: number;
}

export const HUB = { latitude: 10.7769, longitude: 106.7009 };
