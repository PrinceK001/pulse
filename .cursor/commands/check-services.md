Check the health of all Pulse services and infrastructure.

Run health checks on all services and report status:

1. **MySQL**: `docker exec pulse-mysql mysqladmin ping -h localhost -u root -ppulse_root_password`
2. **ClickHouse**: `docker exec pulse-clickhouse clickhouse-client --query "SELECT 1"`
3. **Kafka**: Check via Kafka UI at http://localhost:8081
4. **OTEL Collector 1**: `curl http://localhost:13133`
5. **OTEL Collector 2**: `curl http://localhost:13134`
6. **pulse-server**: `curl http://localhost:8080/healthcheck`
7. **pulse-ui**: `curl http://localhost:3000/healthcheck.txt`
8. **pulse-ai**: `curl http://localhost:8001/list-apps`
9. **pulse-alerts-cron**: `curl http://localhost:4000/healthcheck`

Present results as a table with service name, status (healthy/unhealthy/not running), and port.
