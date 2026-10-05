import type {
  ChainVerification,
  Courier,
  Device,
  EvidenceItem,
  NewDeviceInput,
  NewOrderInput,
  Order,
  User,
} from "./types";
import { session } from "./session";

export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

/** Called on any 401 from an authenticated request (token expired or revoked). */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function request<T>(path: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  const token = session.token();
  const headers: Record<string, string> = { Accept: "application/json" };
  if (init.body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method: init.method ?? "GET",
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
    });
  } catch {
    throw new ApiError(0, "Cannot reach the ProofDrop server");
  }

  if (response.status === 401 && token) onUnauthorized();
  if (!response.ok) {
    let message = `${response.status} ${response.statusText}`;
    try {
      const body = (await response.json()) as { message?: string | string[] };
      if (body.message) message = Array.isArray(body.message) ? body.message.join(", ") : body.message;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(response.status, message);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** URL usable in <img src>, EventSource and WebSocket, where headers can't be set. */
export function withToken(path: string): string {
  const token = session.token() ?? "";
  const sep = path.includes("?") ? "&" : "?";
  return `${path}${sep}access_token=${encodeURIComponent(token)}`;
}

export const api = {
  login: (email: string, password: string) =>
    request<{ accessToken: string; user: User }>("/auth/login", { method: "POST", body: { email, password } }),
  me: () => request<User>("/auth/me"),

  orders: () => request<Order[]>("/dispatch/orders"),
  createOrder: (input: NewOrderInput) => request<Order>("/dispatch/orders", { method: "POST", body: input }),
  assignOrder: (id: string, courierId: string | null) =>
    request<Order>(`/dispatch/orders/${id}/assign`, { method: "POST", body: { courierId } }),

  couriers: () => request<Courier[]>("/dispatch/couriers"),

  devices: () => request<Device[]>("/dispatch/devices"),
  createDevice: (input: NewDeviceInput) => request<Device>("/dispatch/devices", { method: "POST", body: input }),

  evidence: (courierId?: string) =>
    request<EvidenceItem[]>(`/dispatch/evidence${courierId ? `?courierId=${encodeURIComponent(courierId)}` : ""}`),
  verifyChain: (courierId: string) =>
    request<ChainVerification>(`/dispatch/evidence/verify?courierId=${encodeURIComponent(courierId)}`),
  photoUrl: (id: string) => withToken(`/api/dispatch/evidence/${id}/photo`),
};
