# Deploying ProofDrop to a server

This puts the API, the dispatcher dashboard, PostgreSQL and photo storage on one Linux VPS behind
[Caddy](https://caddyserver.com/), which gets and renews the HTTPS certificate automatically.

```
Internet ──443──▶ Caddy ──/api, /fleet──▶ backend (NestJS) ──▶ PostgreSQL, MinIO
                       └──everything else──▶ dashboard (nginx, static)
```

Only Caddy is reachable from outside; the database and MinIO have no published ports.

## 1. Requirements

- A VPS with 2 GB RAM or more (Ubuntu 24.04 LTS is used below) and a public IP.
- A domain or subdomain you control, e.g. `dispatch.example.com`.
- Ports 80 and 443 open in the VPS firewall / security group (80 is needed for the certificate challenge).

## 2. DNS

Create an **A record** (and AAAA if the server has IPv6) for your hostname pointing at the server's IP.
Check it before continuing:

```bash
dig +short dispatch.example.com    # must print the server's IP
```

## 3. Install Docker

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER      # log out and back in afterwards
docker compose version             # Compose v2 is included
```

## 4. Get the code and configure it

```bash
git clone https://github.com/Penz7/proofdrop.git
cd proofdrop
cp .env.prod.example .env
openssl rand -hex 32    # run once per secret below
nano .env               # set DOMAIN, ACME_EMAIL, POSTGRES_PASSWORD, JWT_SECRET, S3_SECRET_KEY
chmod 600 .env
```

`docker compose` refuses to start if any required value is missing.

## 5. Start

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml ps                 # all services "running" / "healthy"
curl -fsS https://dispatch.example.com/api/health            # {"status":"ok","db":"ok","storage":"ok"}
```

The backend applies database migrations on every start. Demo accounts are **not** created in
production (`SEED_DEMO_DATA=false`), and Swagger is off.

The first HTTPS request can take a few seconds while Caddy obtains the certificate. If it fails,
check `docker compose -f docker-compose.prod.yml logs caddy` (usually DNS not pointing at the server
yet, or port 80 blocked).

## 6. Create the first accounts

```bash
# Dispatcher (you'll be asked for the password twice; nothing is echoed)
docker compose -f docker-compose.prod.yml exec backend npm run create-user -- \
  --email you@example.com --name "Your Name" --role DISPATCHER

# A courier
docker compose -f docker-compose.prod.yml exec backend npm run create-user -- \
  --email courier@example.com --name "Courier Name" --role COURIER
```

Add `--force` to reset an existing account's name, role and password. Passwords must be at least 8 characters.

Then sign in at `https://dispatch.example.com`. In the Android app, enter `https://dispatch.example.com`
as the server URL on the login screen.

The public privacy policy (needed for the Play Store listing) is at `https://dispatch.example.com/privacy`.

## 7. Backups

Everything worth keeping lives in two Docker volumes: the database and the evidence photos.
Back up both, at the same time, and copy them off the server.

```bash
mkdir -p ~/backups && cd ~/proofdrop
STAMP=$(date +%F-%H%M)

# Database (consistent dump while running)
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U proofdrop -d proofdrop -Fc > ~/backups/db-$STAMP.dump

# Evidence photos
docker run --rm -v proofdrop-prod_miniodata:/data:ro -v ~/backups:/backup alpine \
  tar czf /backup/photos-$STAMP.tar.gz -C /data .
```

Run that from cron (e.g. nightly) and sync `~/backups` to off-site storage.

**Restore** onto a fresh install (after step 5, before anyone uses it):

```bash
docker compose -f docker-compose.prod.yml exec -T postgres \
  pg_restore -U proofdrop -d proofdrop --clean --if-exists < ~/backups/db-<STAMP>.dump
docker compose -f docker-compose.prod.yml stop minio
docker run --rm -v proofdrop-prod_miniodata:/data -v ~/backups:/backup alpine \
  sh -c "rm -rf /data/* && tar xzf /backup/photos-<STAMP>.tar.gz -C /data"
docker compose -f docker-compose.prod.yml start minio
```

After restoring, use **Evidence → Verify chain** in the dashboard to confirm every photo still matches its record.

## 8. Updating

```bash
cd ~/proofdrop
git pull
docker compose -f docker-compose.prod.yml up -d --build    # migrations run automatically
docker image prune -f
```

Take a backup first if the release notes mention a database migration.

## 9. Operations notes

- Logs: `docker compose -f docker-compose.prod.yml logs -f backend`
- Rotating `JWT_SECRET` signs everyone out. Couriers' unsynced proof stays on their phones and uploads after they sign in again.
- The fleet map uses OpenStreetMap's public tile servers, which are meant for light use. For real traffic, switch to a tile provider (MapTiler, Stadia, or your own) in the dashboard's `MapView` and the app's `FleetMap`.
- To use AWS S3 or Cloudflare R2 instead of MinIO, remove the `minio` service and set `S3_ENDPOINT`, `S3_REGION`, `S3_ACCESS_KEY`, `S3_SECRET_KEY` and `S3_BUCKET` for the backend.
