Set up and start the full Pulse stack for a new developer.

1. Check prerequisites: Docker running, `node` (>=18), `yarn`, `java` (17), `mvn`, `python` (>=3.10)
2. Set up env files:
   - `deploy/.env`: copy from `deploy/.env.example` if missing
   - `pulse_ai/.env`: copy from `.env.example` if missing
   - `pulse-ui/.env`: copy from `.env.example` if missing
3. Build services: `cd deploy && ./scripts/build.sh all`
4. Start services: `cd deploy && ./scripts/start.sh -d`
5. Read credentials from `deploy/.env` for health check commands
6. Wait for health checks:
   - MySQL: `docker exec pulse-mysql mysqladmin ping -h localhost`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client -u <OTEL_CLICKHOUSE_USER> --password <OTEL_CLICKHOUSE_PASSWORD> --query "SELECT 1"`
   - pulse-server: `curl http://localhost:8080/healthcheck`
   - pulse-ui: `curl http://localhost:3000/healthcheck.txt`
   - pulse-alerts-cron: `curl http://localhost:4000/healthcheck`
7. Report status of all services as a table
8. Start pulse-ai: `cd pulse_ai && ./setup.sh` (Docker, port 8000). Health check: `curl http://localhost:8000`
9. Remind user to set API keys in env files if any service failed
