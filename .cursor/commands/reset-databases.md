Reset all databases to a clean state. This is destructive -- confirm before proceeding.

1. Ask for explicit confirmation that the user wants to wipe all data
2. Change to `deploy/`
3. Run `./scripts/reset-databases.sh`
4. Wait for MySQL and ClickHouse to reinitialize
5. Verify tables exist:
   - MySQL: `docker exec pulse-mysql mysql -upulse_user -ppulse_password pulse_db -e "SHOW TABLES;"`
   - ClickHouse: `docker exec pulse-clickhouse clickhouse-client --query "SHOW TABLES FROM otel"`
6. Report the result
