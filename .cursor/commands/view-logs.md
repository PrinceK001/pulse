View logs for a specific Pulse service.

1. Ask which service to view logs for if not specified: pulse-server, pulse-ui, pulse-ai, pulse-alerts-cron, pulse-otel-collector-1, pulse-otel-collector-2, pulse-mysql, pulse-clickhouse, kafka
2. Run `docker logs --tail 100 <container-name>`
3. Scan for errors or warnings and highlight them
4. If errors found, suggest potential causes and fixes
