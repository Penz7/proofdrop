# Architecture

Three deployables share one contract ([API.md](API.md)):

| Part | Where | Role |
|---|---|---|
| Android courier app | `app/`, `feature/*`, `core/*` | Deliveries, proof capture, shifts, device checkout |
| Backend | `backend/` (NestJS + Prisma + PostgreSQL + S3) | Auth, orders, evidence verification and storage, realtime fan-out |
| Dispatcher dashboard | `dashboard/` (React + Vite) | Create/assign orders, live fleet map, evidence audit, devices |

## Android modules

| Module | Responsibility |
|---|---|
| `app` | `Application` (Hilt + WorkManager factory), `MainActivity`, session-driven NavHost + bottom bar |
| `feature:auth` | Login (server URL, credentials) and demo mode |
| `feature:orders` | Delivery list (live assignments, shift toggle, logout guard), order detail (navigate, call, status) |
| `feature:capture` | Camera capture + BLE beacon check + sealing; evidence ledger and verification |
| `feature:checkout` | Device list, ML Kit QR scanner, check-out/return |
| `feature:fleet` | MapLibre/OSM fleet map fed by the WebSocket |
| `core:data` | Repositories (offline-first), `AuthRepository`, `SyncWorker`, `ShiftService` (foreground), `LocationTracker` |
| `core:database` | Room entities, DAOs, merge rules |
| `core:network` | Retrofit API, `FleetSocket` (WS), `AssignmentStream` (SSE), `SessionStore` + Keystore `TokenCipher` |
| `core:ble` | BLE scanning as a `Flow`, beacon matching |
| `core:evidence` | SHA-256, hash chain `seal`/`verify` with anchors. Pure Kotlin |
| `core:model` | Serializable models matching the API contract, plus demo data |
| `core:designsystem` | Theme, shared components, permission gate, camera selection |

Feature modules only see repository interfaces in `core:data`; Room, Retrofit and OkHttp stay internal.

## Data flow (app)

```
 UI (Compose) ──events──▶ ViewModel ──▶ Repository ──▶ Room (source of truth) ◀── merge ── Network
      ▲                      │                              │
      └──── StateFlow ◀──────┘                 WorkManager pushes queued changes (status, checkout, evidence)
```

- Screens read from Room only, so they keep working offline.
- Writes go to Room first and are flagged (`pendingSync`, `pendingAction`, `uploadState`). `SyncWorker` uploads them in order when the device is online, with exponential backoff.
- Evidence uploads are strictly sequential: the server answers `409` until record `n-1` is stored, and the worker retries.
- A `401` anywhere clears the session and returns the user to login.

## Realtime

| Channel | Transport | Producer → consumer |
|---|---|---|
| Assignments | SSE `/api/assignments` | backend → courier app (shared stream for the Orders screen and the shift service; notifications in the background) |
| Dispatcher events | SSE `/api/dispatch/events` | backend → dashboard (order changes, new evidence) |
| Fleet | WebSocket `/fleet` | courier app → backend (`position` every 5 s while on shift) → dashboard + apps (`fleet` snapshots, max 1/s) |

The backend fans events out through an in-process event bus. Running more than one instance would need Redis pub/sub.

## Evidence chain

- **App:** `EvidenceChain.seal(draft, anchor)` links each record to the previous one. The anchor is the last local record, or the server's chain head fetched at login. Sealing runs under a mutex and `sequence` is `UNIQUE`.
- **Server:** for every upload it re-hashes the photo, recomputes the seal, checks the link to the stored record `n-1`, and only then writes to S3 and Postgres and marks the order delivered.
- **Audit:** the dashboard's "Verify chain" re-downloads every stored photo for a courier and walks the whole chain.

Kotlin and TypeScript share the canonical string format and a test vector, so a mismatch fails CI on both sides.

## Security notes

- The JWT is stored AES-GCM encrypted with a key held in the Android Keystore. Tokens passed as query parameters (WebSocket, SSE, `<img>`) are redacted from the app's HTTP logs.
- Role guards on every backend route: couriers can only see and act on their own orders and chain.
- Evidence photos live in app-private storage on the phone and in a private bucket on the server. The dashboard loads them through the authenticated API.
- Cleartext HTTP is allowed only for the local demo setup; production must use HTTPS.

## Permissions (Android)

| Permission | Why | Required? |
|---|---|---|
| `CAMERA` | Proof photo, QR scanning | For those screens |
| `ACCESS_FINE/COARSE_LOCATION` | GPS tag on evidence, live position during a shift | To start a shift |
| `FOREGROUND_SERVICE_LOCATION` | Shift service keeps sharing location in the background | With shifts |
| `POST_NOTIFICATIONS` | New-assignment alerts | Optional |
| `BLUETOOTH_SCAN` (`neverForLocation`) | Drop-off beacon check | Optional |
