# ProofDrop API contract (v1)

The NestJS backend (`backend/`), the React dispatcher dashboard (`dashboard/`) and the Android app all follow this document. **Change it first, then the code.**

## Conventions

- Base URL: `http://<host>:3000`. REST lives under `/api`. The WebSocket is at `/fleet` (no `/api` prefix).
- JSON everywhere. **All timestamps are epoch milliseconds (numbers)**, never ISO strings. The Android models use `Long`.
- Auth: `Authorization: Bearer <JWT>`. `?access_token=<JWT>` is accepted **only** where clients cannot set headers: the two SSE streams (`/api/assignments`, `/api/dispatch/events`), the evidence photo (`<img>`), and the `/fleet` WebSocket. Everywhere else a query token is ignored (`401`), so tokens stay out of access logs.
- Roles: `COURIER` (Android app) and `DISPATCHER` (web dashboard). A wrong role gets `403`, a missing or invalid token gets `401`.
- Errors use NestJS's default shape: `{ "statusCode": 409, "message": "...", "error": "Conflict" }`.
- Ids: users and orders use UUIDs. Devices use human ids (`DEV-001`). Evidence ids are client-generated UUIDs.

## Shared types

```ts
type Role = "COURIER" | "DISPATCHER";
type OrderStatus = "CREATED" | "ASSIGNED" | "PICKED_UP" | "DELIVERED" | "FAILED"; // CREATED = not assigned yet, never sent to couriers
type DeviceType = "SCANNER" | "BODY_CAM" | "PRINTER" | "VEHICLE";
type CourierStatus = "IDLE" | "EN_ROUTE" | "DELIVERING" | "OFFLINE";

interface User { id: string; email: string; name: string; role: Role }

interface Order {
  id: string;            // uuid
  code: string;          // human code, "PD-1001"
  customerName: string;
  customerPhone: string | null;
  address: string;
  latitude: number;
  longitude: number;
  items: string;
  status: OrderStatus;
  beaconId: string | null;   // BLE beacon name at drop-off
  courierId: string | null;
  courierName: string | null;
  assignedAt: number;        // epoch ms; createdAt if never assigned
  deliveredAt: number | null;
}

interface Device {
  id: string; name: string; type: DeviceType; serial: string; batteryPct: number;
  holderId: string | null; holderName: string | null; checkedOutAt: number | null;
}

interface EvidenceRecord {      // exactly core/model Evidence.kt
  id: string; sequence: number; orderId: string; fileName: string; fileSha256: string;
  capturedAt: number; latitude: number | null; longitude: number | null; bleVerified: boolean;
  previousHash: string; recordHash: string;
}

interface CourierPosition {
  courierId: string; name: string; latitude: number; longitude: number;
  status: CourierStatus; updatedAt: number;
}
```

## Evidence hash (must match Kotlin `EvidenceChain.canonical` byte for byte)

```
canonical = [sequence, id, orderId, fileName, fileSha256, capturedAt,
             latE7 ?? "", lngE7 ?? "", bleVerified ("true"/"false"), previousHash].join("|")
latE7 = Math.round(latitude * 1e7)      // integer 1e-7 degrees (Kotlin: (lat * 1e7).roundToLong())
recordHash = sha256_hex(utf8(canonical))
GENESIS = "0" * 64                      // previousHash of a courier's first record
```

Test vector (both test suites assert it):

```
id="ev-vector" orderId="ord-vector" fileName="ev-vector.jpg" fileSha256=sha256("abc")
capturedAt=1791000000000 latitude=10.7743 longitude=106.7038 bleVerified=true sequence=1 previousHash=GENESIS
canonical = "1|ev-vector|ord-vector|ev-vector.jpg|ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad|1791000000000|107743000|1067038000|true|0000000000000000000000000000000000000000000000000000000000000000"
recordHash = "7dba06ac87db559e5033d279f1c9b78232b21e101040bf7f1cc8e177ebdd7e38"
```

Each courier has **one continuous chain on the server**, numbered from 1, across reinstalls and phones. After login the app fetches the chain head and seals its next record on top of it.

---

## Auth

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/login` | `{ email, password }` | `200 { accessToken, user: User }` · `401` · `429` after 10 attempts per minute for the same email + IP |
| GET | `/api/auth/me` | | `200 User` |

JWT lifetime: 7 days (MVP, no refresh tokens).

## Health

`GET /api/health` (no auth) → `200 { status: "ok", db: "ok", storage: "ok" }`

## Courier endpoints (role `COURIER`)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/orders` | Orders assigned to me (status ≠ `CREATED`), newest `assignedAt` first |
| POST | `/api/orders/:id/status` | Body `{ status, at }`. Allowed: `ASSIGNED→PICKED_UP`, `ASSIGNED/PICKED_UP→DELIVERED/FAILED`, `FAILED→ASSIGNED`. Same status again = `200` (idempotent). Other transitions → `409`. Not mine → `404`. Returns `Order` |
| GET | `/api/devices` | All devices |
| POST | `/api/devices/:id/checkout` | Body `{ courierId, courierName, at }`. The server **ignores** courierId/courierName and uses the token's user. If someone else holds the device, returns `200` with the device unchanged (the client detects the conflict). Unknown id → `404` |
| POST | `/api/devices/:id/return` | Holder returns → `200 Device`. Already free → `200` unchanged (so offline retries succeed). Held by someone else → `409` |
| GET | `/api/evidence/head` | `{ sequence: number, recordHash: string }` for my chain (`0` + GENESIS if empty) |
| POST | `/api/evidence` | `multipart/form-data` with parts `record` (JSON string of `EvidenceRecord`, no filename) and `file` (JPEG). See below |
| GET | `/api/assignments` | **SSE**, see below |

### `POST /api/evidence` rules, checked in this order

1. Missing part → `422 { accepted:false, message:"Missing record or file" }`. Malformed record → `422 "Invalid record"`: `id`/`orderId` must be UUIDs, hashes 64 lowercase hex, `fileName` `[A-Za-z0-9._-]{1,128}`, `sequence` 1…2³¹−1, `capturedAt` a positive integer, and lat/lng in range. A file that isn't JPEG/PNG/WebP by its magic bytes → `422 "Unsupported media type…"` (the declared MIME type is ignored)
2. Same `id` already stored with the same `recordHash` → `200 { accepted:true, message:"Already stored" }` (idempotent retry, also when two identical uploads race)
3. `sha256(file) != record.fileSha256` → `422 "File hash mismatch"`
4. Recomputed hash ≠ `record.recordHash` → `422 "Record seal is invalid"`
5. Order not assigned to me → `422 "Order not assigned to you"`
6. `sequence > 1` and my record `sequence-1` does not exist yet → `409 "Previous record not uploaded yet"` (the client retries later)
7. `previousHash` ≠ my record `sequence-1`'s `recordHash` (or GENESIS when `sequence == 1`) → `422 "Chain link mismatch"`
8. A different record already holds this `sequence` → `422 "Sequence already used"`
9. Otherwise store the file in object storage and the row in Postgres, set the order to `DELIVERED` (with `deliveredAt`), and return `200 { accepted:true, message:"Stored evidence #<sequence>" }`

### `GET /api/assignments` (SSE, courier)

- `event: assignment`, `data: Order`: an order was assigned to me, or a dispatcher changed one of my orders
- `event: unassigned`, `data: { "id": "<orderId>" }`: an order was taken away from me
- A comment line `: ping` every 25 s keeps proxies from closing the connection

## Dispatcher endpoints (role `DISPATCHER`)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/dispatch/orders?status=` | All orders, newest first (`courierName` filled in) |
| POST | `/api/dispatch/orders` | `{ customerName, customerPhone?, address, latitude, longitude, items, beaconId?, courierId? }` → `201 Order`. Status is `ASSIGNED` if `courierId` is given, else `CREATED`. Code = next `PD-xxxx` |
| POST | `/api/dispatch/orders/:id/assign` | `{ courierId: string \| null }` → `Order`. `null` unassigns (status back to `CREATED`). Delivered orders → `409` |
| GET | `/api/dispatch/couriers` | `[{ id, name, email, online, lastPosition: CourierPosition \| null, activeOrders }]` (`online` = position within the last 2 min) |
| GET | `/api/dispatch/devices` | Same as the courier list |
| POST | `/api/dispatch/devices` | `{ id, name, type, serial, batteryPct }` → `201 Device` · duplicate id → `409` |
| GET | `/api/dispatch/evidence?courierId=&orderId=` | `EvidenceRecord & { courierId, courierName, orderCode, receivedAt, sizeBytes }[]`, newest first |
| GET | `/api/dispatch/evidence/:id/photo` | The image bytes (`?access_token=` allowed so `<img src>` works). Served with `X-Content-Type-Options: nosniff` and `Content-Security-Policy: default-src 'none'; sandbox` |
| GET | `/api/dispatch/evidence/verify?courierId=` | Re-downloads and re-hashes every photo and walks the chain → `{ valid: true, count }` or `{ valid: false, atSequence, reason }` |
| GET | `/api/dispatch/events` | **SSE**: `event: order` (`Order`) on any order change, `event: evidence` (the evidence list item) on each accepted upload, `: ping` every 25 s |

## WebSocket `/fleet?access_token=<JWT>`

Raw WebSocket (NestJS `WsAdapter`) with JSON messages in the form `{ "event": string, "data": any }`.

- Server → client on connect and whenever positions change (at most once per second):
  `{ "event": "fleet", "data": CourierPosition[] }`, covering couriers seen in the last 10 minutes. A courier silent for more than 2 minutes is reported as `OFFLINE`.
- Courier → server: `{ "event": "position", "data": { "latitude": number, "longitude": number, "status"?: CourierStatus } }`. The server fills `courierId`, `name` and `updatedAt` from the token and also stores the last position on the user row.
- Dispatcher connections only receive messages.
- An invalid token closes the socket with code `4401`. Frames over 4 KB close it with `1009`. Position reports arriving less than 1 s apart are ignored.

## Seed accounts (local dev only)

The Docker image only creates these when `SEED_DEMO_DATA=true` (set in `docker-compose.yml`), so a production deployment never ships known passwords. `npx prisma db seed` always runs the seed.

| Role | Email | Password |
|---|---|---|
| Dispatcher | `dispatcher@proofdrop.dev` | `dispatch123` |
| Courier | `courier1@proofdrop.dev` · `courier2@…` · `courier3@…` | `courier123` |

Seed data: devices `DEV-001`…`DEV-006`, and 6 orders around District 1, HCMC (hub 10.7769, 106.7009). Four are assigned to courier1 (two with `beaconId: "PD-BEACON-01"`) and two are `CREATED`.

## Local ports

| Service | Port |
|---|---|
| Backend (NestJS) | 3000 |
| Dashboard dev server (Vite, proxies `/api` and `/fleet` to 3000) | 5173 |
| Dashboard in docker compose (nginx, same proxying) | 8080 |
| PostgreSQL (host port; 5432 inside the compose network) | 5433 |
| MinIO API / console | 9000 / 9001 |
