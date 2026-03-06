---
name: debug-data-pipeline
description: Systematic debugging checklist for the OTEL data pipeline when traces, logs, or metrics are missing or incomplete. Use when data is not showing up in ClickHouse, dashboards are empty, or the ingestion pipeline seems broken.
---

# Debug Data Pipeline

## Pipeline Architecture

```
Mobile SDK → OTEL Collector (:4317 gRPC, :4318 HTTP)
  → Kafka (:9094)
  → ClickHouse (:8123)
```

## Diagnostic Checklist

```
- [ ] Step 1: Discover running services
- [ ] Step 2: Verify OTEL Collector is receiving data
- [ ] Step 3: Verify Kafka has messages
- [ ] Step 4: Verify ClickHouse has data
- [ ] Step 5: Verify backend can query ClickHouse
- [ ] Step 6: Verify UI is fetching from backend
```

## Step 1: Discover Running Services

```bash
docker ps --format "table {{.Names}}\t{{.Ports}}\t{{.Status}}"
```

Use the output to confirm which services are running and on which ports.

## Step 2: OTEL Collector

```bash
curl http://localhost:13133

curl http://localhost:8888/metrics | grep otelcol_receiver_accepted

cd deploy && ./scripts/logs.sh otel-collector
```

If zero `otelcol_receiver_accepted`: SDK is not sending data, or port is wrong.

## Step 3: Kafka

Open Kafka UI: http://localhost:8081

Check:
- Topics exist (e.g., `otlp_spans`, `otlp_logs`)
- Messages are flowing (message count increasing)
- Consumer groups are connected

```bash
cd deploy && ./scripts/logs.sh kafka
```

## Step 4: ClickHouse

Read ClickHouse credentials from `deploy/.env` (variables: `OTEL_CLICKHOUSE_USER` / `OTEL_CLICKHOUSE_PASSWORD`, defaults: `pulse_user` / `pulse_password`):

```bash
# Count recent traces
docker exec pulse-clickhouse clickhouse-client \
  -u $OTEL_CLICKHOUSE_USER --password $OTEL_CLICKHOUSE_PASSWORD \
  --query "SELECT count() FROM otel.otel_traces WHERE Timestamp > now() - INTERVAL 1 HOUR"

# Count recent logs
docker exec pulse-clickhouse clickhouse-client \
  -u $OTEL_CLICKHOUSE_USER --password $OTEL_CLICKHOUSE_PASSWORD \
  --query "SELECT count() FROM otel.otel_logs WHERE Timestamp > now() - INTERVAL 1 HOUR"

# Check tables exist
docker exec pulse-clickhouse clickhouse-client \
  -u $OTEL_CLICKHOUSE_USER --password $OTEL_CLICKHOUSE_PASSWORD \
  --query "SHOW TABLES FROM otel"
```

## Step 5: Backend

```bash
curl http://localhost:8080/healthcheck
cd deploy && ./scripts/logs.sh pulse-server | grep -i error
```

Check ClickHouse connectivity: look for R2DBC or connection errors in logs.

## Step 6: Frontend

- Open browser DevTools → Network tab
- Check API calls to `localhost:8080` for errors
- Verify `REACT_APP_PULSE_SERVER_URL` in environment

## Common Issues

| Symptom | Likely Cause |
|---------|-------------|
| No data in ClickHouse | Kafka consumer or ClickHouse exporter config issue |
| Kafka topics empty | OTEL Collector not receiving from SDK |
| Backend 500 on queries | ClickHouse connection or schema mismatch |
| UI shows empty charts | Time range filter too narrow, or backend error |
