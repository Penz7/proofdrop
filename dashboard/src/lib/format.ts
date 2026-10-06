import type { CourierStatus, DeviceType, OrderStatus } from "./types";

export function timeAgo(ms: number, now = Date.now()): string {
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 10) return "just now";
  if (s < 60) return `${s}s ago`;
  const m = Math.round(s / 60);
  if (m < 60) return `${m} min ago`;
  const h = Math.round(m / 60);
  if (h < 24) return `${h} h ago`;
  return new Date(ms).toLocaleDateString();
}

export function dateTime(ms: number): string {
  return new Date(ms).toLocaleString(undefined, {
    day: "2-digit",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

export function shortHash(hash: string, n = 10): string {
  return hash.length > n ? `${hash.slice(0, n)}…` : hash;
}

export function bytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(0)} KB`;
  return `${(n / 1024 / 1024).toFixed(1)} MB`;
}

export const orderStatusLabel: Record<OrderStatus, string> = {
  CREATED: "Unassigned",
  ASSIGNED: "Assigned",
  PICKED_UP: "Picked up",
  DELIVERED: "Delivered",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
};

export const courierStatusLabel: Record<CourierStatus, string> = {
  IDLE: "Idle",
  EN_ROUTE: "En route",
  DELIVERING: "Delivering",
  OFFLINE: "Offline",
};

/** Hex colors used on the map (CSS variables don't reach MapLibre marker internals reliably). */
export const courierStatusColor: Record<CourierStatus, string> = {
  IDLE: "#8a99ab",
  EN_ROUTE: "#3b82c4",
  DELIVERING: "#2e9e5b",
  OFFLINE: "#5b6577",
};

export const deviceTypeLabel: Record<DeviceType, string> = {
  SCANNER: "Scanner",
  BODY_CAM: "Body cam",
  PRINTER: "Printer",
  VEHICLE: "Vehicle",
};
