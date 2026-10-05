# ProofDrop backend

NestJS 11 + PostgreSQL (Prisma 6) + S3-compatible evidence storage. It implements [docs/API.md](../docs/API.md): REST, Server-Sent Events, and a raw WebSocket at `/fleet`.

## Run everything with Docker

```bash
docker compose up -d --build      # from the repo root
```

- API: http://localhost:3000/api (Swagger: http://localhost:3000/api/docs)
- Dashboard: http://localhost:8080
- MinIO console: http://localhost:9001 (`proofdrop` / `proofdrop-secret`)

Migrations and seed data are applied automatically when the backend container starts.

## Develop locally

```bash
docker compose up -d postgres minio   # Postgres on host port 5433, MinIO on 9000
cd backend
cp .env.example .env
npm install
npx prisma migrate deploy && npx prisma db seed
npm run start:dev
```

Seed accounts: `dispatcher@proofdrop.dev` / `dispatch123` and `courier1..3@proofdrop.dev` / `courier123`.

## Tests

```bash
npm test                                   # unit: evidence chain (incl. the Kotlin cross-language vector), status rules
DATABASE_URL=... npm run test:e2e          # e2e against a migrated + seeded database
```

## Layout

| Path | What |
|---|---|
| `src/auth` | Login, JWT guard (header or `?access_token=`), role guard |
| `src/orders` | Courier order list/status, dispatcher create/assign, both SSE streams |
| `src/evidence` | Hash chain (`evidence-chain.ts`), upload rules, photo download, server-side chain verification |
| `src/devices` | Device checkout/return with a conditional update so two couriers can't both win |
| `src/fleet` | `/fleet` WebSocket gateway; in-memory positions with the last one persisted |
| `src/storage` | `local` or `s3` storage driver (MinIO / AWS S3 / R2 by config) |
| `prisma/` | Schema, migrations, idempotent seed |
