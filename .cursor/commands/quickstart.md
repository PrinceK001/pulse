Set up and start the full Pulse stack for a new developer.

1. Check prerequisites: Docker running, `node` (>=18), `yarn`, `java` (17), `mvn`, `python` (>=3.10)
2. Set up env files:
   - `deploy/.env`: copy from `deploy/.env.example` if missing
   - `pulse_ai/pulse_agent/.env`: copy from `.env.example` if missing
   - `pulse-ui/.env`: copy from `.env.example` if missing
3. Build services: `cd deploy && ./scripts/build.sh all`
4. Start services: `cd deploy && ./scripts/start.sh -d`
5. Wait for health checks:
   - MySQL: `docker exec pulse-mysql mysqladmin ping -h localhost -u root -ppulse_root_password`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client --query "SELECT 1"`
   - pulse-server: `curl http://localhost:8080/healthcheck`
   - pulse-ui: `curl http://localhost:3000/healthcheck.txt`
   - pulse-ai: `curl http://localhost:8001/list-apps`
6. Report status of all services as a table
7. Remind user to set API keys in env files if any service failed
