# Architecture

## Modules

| Module | Type | Responsibility |
|---|---|---|
| `app` | Android app | `Application` (Hilt + WorkManager factory), `MainActivity`, NavHost + bottom bar |
| `feature:orders` | Android lib | Delivery list (pull-to-refresh, live assignment snackbar), order detail and status actions |
| `feature:capture` | Android lib | Camera capture + BLE beacon check + sealing; evidence ledger and verification |
| `feature:checkout` | Android lib | Device list, QR scanner, check-out/return |
| `feature:fleet` | Android lib | Live courier map (Canvas), connection state, server URL setting |
| `core:data` | Android lib | Repositories (offline-first), sync worker, location, demo seeding |
| `core:database` | Android lib | Room entities, DAOs, merge rules |
| `core:network` | Android lib | Retrofit API, WebSocket (`FleetSocket`), SSE (`AssignmentStream`), runtime base URL |
| `core:ble` | Android lib | BLE scanning as a `Flow`, beacon matching |
| `core:evidence` | JVM lib | SHA-256 and the hash chain (`seal` / `verify`). No Android deps, so it is easy to test |
| `core:model` | JVM lib | Serializable domain models + demo data, shared with the server |
| `core:designsystem` | Android lib | Theme, shared components, permission gate |
| `server` | Ktor app | In-memory dispatch backend used for demos and integration |

## Data flow

```
 UI (Compose)  ──events──▶  ViewModel  ──calls──▶  Repository (core:data)
      ▲                         │                    │        │
      └──── StateFlow ◀─────────┘           Room (truth)   Network
                                                 ▲            │
                                                 └── merge ◀──┘
                                       WorkManager pushes queued changes
```

- **Reads** always come from Room, so screens work offline and update reactively.
- **Writes** go to Room first and are flagged (`pendingSync`, `pendingAction`, `uploadState`). Then `SyncScheduler.requestSync()` enqueues a unique `SyncWorker` with a network constraint and exponential backoff.
- **Refresh** merges server data, but rows with unsynced local changes win (`mergeFromServer`).

## Real-time channels

| Channel | Transport | Direction | Failure handling |
|---|---|---|---|
| Fleet positions | WebSocket `/fleet` | both ways: server pushes snapshots, client reports its own position every 5 s | `IOException` → 10 s of local simulation → reconnect |
| Order assignments | SSE `/assignments` | server → client | `retryWhen` with linear backoff, capped at 30 s |

## Evidence chain

`EvidenceChain.seal(draft, previous)` assigns the next sequence number, links to `previous.recordHash` (or 64 zeros for the first record) and hashes a canonical, `|`-joined field list.

`EvidenceChain.verify(records, fileHashOf)` walks the records in order and reports the first broken record with a reason. Sealing runs under a `Mutex` so two captures can never claim the same sequence number, and the `sequence` column is `UNIQUE` as a second guard.

On upload, the server re-hashes the received bytes and checks the record's own seal before accepting it.

## Permissions

| Permission | Why | Required? |
|---|---|---|
| `CAMERA` | Proof photo, QR scanning | Yes, for those screens |
| `ACCESS_FINE/COARSE_LOCATION` | GPS tag on evidence, own position on fleet map | Optional |
| `BLUETOOTH_SCAN` (`neverForLocation`, API 31+) | Drop-off beacon check | Optional |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` (API ≤ 30) | Legacy BLE scanning | Optional |
