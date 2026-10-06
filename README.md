# ProofDrop

**Tamper-evident proof of delivery, end to end.** It has three parts:

- An **Android app for couriers**. They photograph each drop-off, and the app hashes the photo, tags it with GPS and an optional BLE beacon check, and seals it into an append-only hash chain.
- A **NestJS backend**. It re-verifies every record and photo before accepting it.
- A **React dashboard for dispatchers**. They create and assign orders, watch the fleet live on a map, and audit any courier's evidence chain.

> The same chain-of-custody idea that digital-evidence systems use, applied to last-mile delivery.

[![CI](https://github.com/Penz7/proofdrop/actions/workflows/ci.yml/badge.svg)](https://github.com/Penz7/proofdrop/actions/workflows/ci.yml)

```
┌────────────────────┐   REST · SSE · WebSocket   ┌──────────────────────┐   ┌─────────────┐
│ Android courier app│ ─────────────────────────▶ │ NestJS API  :3000    │──▶│ PostgreSQL  │
│ Kotlin · Compose   │ ◀───── assignments (SSE)   │ JWT · Prisma · WS    │   └─────────────┘
└────────────────────┘                            │                      │   ┌─────────────┐
┌────────────────────┐   REST · SSE · WebSocket   │                      │──▶│ MinIO / S3  │
│ Dispatcher web app │ ─────────────────────────▶ │                      │   │ (photos)    │
│ React · MapLibre   │ ◀───── live fleet (WS)     └──────────────────────┘   └─────────────┘
└────────────────────┘
```

## What it does

| | Courier app (Android) | Backend / Dashboard |
|---|---|---|
| **Orders** | Offline-first list. New assignments are pushed over SSE and shown as notifications even when the app is in the background | Dispatcher creates an order (clicks the map to set the location) and assigns, reassigns or unassigns it |
| **Proof of delivery** | CameraX photo → SHA-256 → sealed into the courier's hash chain → queued upload (WorkManager) | Re-hashes the uploaded photo, checks the seal and the link to the previous record, and stores the photo in S3 |
| **BLE beacon** | While the camera is open, scans for the drop-off beacon and marks the delivery *beacon verified* when it's close enough | Badge on each evidence record |
| **Shift** | A foreground service streams GPS to dispatch over a WebSocket | Live fleet map (MapLibre + OpenStreetMap) with online/offline state |
| **Devices** | Scan a QR sticker to check out or return a scanner, body cam or scooter | Add devices and print QR sticker sheets. Conflicts are resolved server-side |
| **Audit** | Ledger screen re-verifies the local chain and photos | "Verify chain" re-downloads every photo of a courier, re-hashes it, and points to the first broken record |

### How the evidence chain works

```
record_n.hash = SHA256( seq | id | orderId | file | fileSha256 | time | latE7 | lngE7 | ble | record_{n-1}.hash )
```

Kotlin (app) and TypeScript (server) must produce **byte-identical** hashes. Coordinates are encoded as integer 1e-7 degrees to avoid float-formatting differences, and both test suites assert the same test vector ([docs/API.md](docs/API.md)).

| Attack | Caught by |
|---|---|
| Edit any field of a record | its hash no longer matches its contents (app and server) |
| Re-seal a forged record | the next record's `previousHash` link breaks |
| Delete a record | gap in sequence numbers |
| Swap the photo on the phone | Ledger re-hash ≠ `fileSha256` |
| Upload a different photo | server re-hash ≠ `fileSha256` → `422` |
| Tamper with a photo in storage | dashboard "Verify chain" re-hashes it from S3 |

Each courier has one continuous chain on the server. After login, the app fetches the chain head and seals new records on top of it, so a reinstall or a new phone doesn't fork the chain.

## Tech stack

| Part | Stack |
|---|---|
| Android | Kotlin 2.2, Jetpack Compose (Material 3), Hilt, Coroutines/Flow, Room, WorkManager, Retrofit + OkHttp (REST, WebSocket, SSE), kotlinx.serialization, CameraX, ML Kit, Bluetooth LE, MapLibre, type-safe Navigation, Android Keystore (encrypted token), foreground service |
| Backend | NestJS 11, TypeScript, PostgreSQL 16 + Prisma, JWT (role guards), WebSocket (`ws`), SSE, S3 API (MinIO locally), Swagger, Jest + Supertest |
| Dashboard | React 19, Vite, TypeScript, Tailwind CSS v4, TanStack Query, React Router, MapLibre GL, QR code generation |
| Infra | Docker Compose, GitHub Actions (3 jobs: Android, backend with Postgres, dashboard) |

## Run it locally

**Requirements:** Docker Desktop, Node 24, JDK 17, Android Studio.

### 1. Backend + dashboard

Option A: everything in Docker:

```bash
docker compose up -d --build
# API        http://localhost:3000/api      (Swagger: /api/docs)
# Dashboard  http://localhost:8080
```

Option B: infrastructure in Docker, code running locally (hot reload):

```bash
docker compose up -d postgres minio
cd backend && cp .env.example .env && npm install
npx prisma migrate deploy && npx prisma db seed
npm run start:dev                       # http://localhost:3000

cd ../dashboard && npm install && npm run dev    # http://localhost:5173
```

### 2. Android app

```bash
./gradlew :app:installDebug
```

| Device | Server URL on the login screen |
|---|---|
| Emulator | `http://10.0.2.2:3000` (default) |
| Phone over USB | run `adb reverse tcp:3000 tcp:3000`, then use `http://127.0.0.1:3000` |
| Phone on the same Wi-Fi | `http://<your-PC-LAN-IP>:3000` |

No server? Tap **Try demo mode** for sample data and a simulated fleet on the phone.

### Seed accounts (local only)

| Role | Email | Password |
|---|---|---|
| Dispatcher (dashboard) | `dispatcher@proofdrop.dev` | `dispatch123` |
| Courier (app) | `courier1@proofdrop.dev` (also `courier2`, `courier3`) | `courier123` |

### Demo script

1. Sign in on the dashboard as the dispatcher and on the app as courier1. Tap **Start shift** in the app; the courier appears live on the dashboard map.
2. Dashboard → **New order** → click the map, pick courier1 → the phone gets a notification within a second.
3. App → open the order → **Capture proof of delivery**. The record is sealed, uploaded and verified, and the order turns *Delivered* on the dashboard without a reload.
4. Dashboard → **Evidence** → open the photo → **Verify chain** → intact.
5. App → **Ledger** → **Simulate tampering** (debug builds) → the app flags the exact record.

## Testing

```bash
./gradlew test                       # Android: hash chain, beacon matching, ViewModels (fakes + Turbine)
cd backend && npm test               # chain (incl. shared test vector), order state machine
cd backend && npm run test:e2e       # login → orders → evidence upload (valid + tampered) on real Postgres
cd dashboard && npm run typecheck && npm run build
```

## Repository layout

```
app/                Android app module (navigation, Hilt, WorkManager setup)
feature/*           auth · orders · capture (camera + ledger) · checkout (QR) · fleet (map)
core/*              model · evidence (hash chain) · data · database · network · ble · designsystem
build-logic/        Gradle convention plugins
backend/            NestJS API + Prisma schema/migrations/seed
dashboard/          React dispatcher dashboard
docs/API.md         API contract shared by all three
docs/ARCHITECTURE.md
```

## Known limitations

- Single backend instance: realtime fan-out is in memory (Redis pub/sub is needed to scale out).
- No refresh tokens (7-day JWT), and the role in the token isn't re-checked against the database.
- Server-side chain verification loads a courier's photos into memory; fine for an MVP, should stream for large chains.
- Swagger (`/api/docs`) is always on; disable it for production.
- Public OpenStreetMap tiles are for demos only; use a tile provider in production.
- Release builds use the debug signing key until a Play upload key is configured.
