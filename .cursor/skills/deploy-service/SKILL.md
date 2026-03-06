---
name: deploy-service
description: Workflow for building and deploying Pulse services locally via Docker. Use when building Docker images, starting/stopping services, or managing the local development environment.
---

# Deploy Service

## Quick Start (All Services)

```bash
cd deploy
cp .env.example .env    # first time only, then edit values
./scripts/quickstart.sh
```

## Build Specific Service

```bash
cd deploy
./scripts/build.sh ui         # pulse-ui only
./scripts/build.sh server     # pulse-server only
./scripts/build.sh alerting   # pulse-alerts-cron only
./scripts/build.sh all        # everything
```

## Start/Stop

```bash
./scripts/start.sh -d ui        # start detached
./scripts/start.sh -d server
./scripts/start.sh -d alerting
./scripts/stop.sh                # stop all
./scripts/stop.sh -v             # stop + remove volumes
```

## View Logs

```bash
./scripts/logs.sh                # all services
./scripts/logs.sh pulse-server   # specific service
```

## AI Agent (own Docker Compose)

```bash
cd pulse_ai && cp .env.example .env   # first time — set GOOGLE_API_KEY
cd pulse_ai && ./setup.sh             # build + start (Docker, port 8000)
curl http://localhost:8000             # health check
```

## Reset Databases

```bash
./scripts/reset-databases.sh     # drops volumes, reinitializes
```

## Health Checks

Use `docker ps` to verify actual ports, then:

| Service | Health Check |
|---------|-------------|
| pulse-server | `curl http://localhost:8080/healthcheck` |
| pulse-ui | `curl http://localhost:3000/healthcheck.txt` |
| pulse-alerts-cron | `curl http://localhost:4000/healthcheck` |
| OTEL Collector | `curl http://localhost:13133` |
| pulse-ai (Docker) | `curl http://localhost:8000` |

## Troubleshooting

- **Port conflict**: check if another service uses the port (`lsof -i :8080`)
- **Build failure**: try `./scripts/build.sh --no-cache <service>`
- **DB not ready**: wait for health check or run `./scripts/reset-databases.sh`
- **Missing env vars**: compare `deploy/.env` with `deploy/.env.example`
