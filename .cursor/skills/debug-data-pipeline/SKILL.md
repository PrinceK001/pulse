---
name: debug-data-pipeline
description: Systematic debugging checklist for the OTEL data pipeline when traces, logs, or metrics are missing or incomplete. Use when data is not showing up in ClickHouse, dashboards are empty, or the ingestion pipeline seems broken.
---

# Debug Data Pipeline

## Pipeline Architecture

```
Mobile SDK → OTEL Collector 1 (:4317 gRPC, :4318 HTTP)
  → Kafka (:9094)
  → OTEL Collector 2
  → ClickHouse (:8123)
```

## Diagnostic Checklist

```
- [ ] Step 1: Verify OTEL Collector 1 is receiving data
- [ ] Step 2: Verify Kafka has messages
- [ ] Step 3: Verify OTEL Collector 2 is consuming and exporting
- [ ] Step 4: Verify ClickHouse has data
- [ ] Step 5: Verify backend can query ClickHouse
- [ ] Step 6: Verify UI is fetching from backend
```

## Step 1: OTEL Collector 1

```bash
# Health check
curl http://localhost:13133

# Internal metrics
curl http://localhost:8888/metrics | grep otelcol_receiver_accepted

# Logs
cd deploy && ./scripts/logs.sh pulse-otel-collector-1
```

If zero `otelcol_receiver_accepted`: SDK is not sending data, or port is wrong.

## Step 2: Kafka

Open Kafka UI: http://localhost:8081

Check:
- Topics exist (e.g., `otlp_spans`, `otlp_logs`)
- Messages are flowing (message count increasing)
- Consumer groups are connected

```bash
cd deploy && ./scripts/logs.sh kafka
```

## Step 3: OTEL Collector 2

```bash
curl http://localhost:13134    # health
curl http://localhost:8889/metrics | grep otelcol_exporter_sent

cd deploy && ./scripts/logs.sh pulse-otel-collector-2
```

If zero `otelcol_exporter_sent`: Kafka consumer or ClickHouse exporter config issue.

## Step 4: ClickHouse

```bash
# Count recent traces
docker exec pulse-clickhouse clickhouse-client --query \
  "SELECT count() FROM otel.otel_traces WHERE Timestamp > now() - INTERVAL 1 HOUR"

# Count recent logs
docker exec pulse-clickhouse clickhouse-client --query \
  "SELECT count() FROM otel.otel_logs WHERE Timestamp > now() - INTERVAL 1 HOUR"

# Check tables exist
docker exec pulse-clickhouse clickhouse-client --query "SHOW TABLES FROM otel"
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
| No data in ClickHouse | Collector 2 not consuming from Kafka |
| Kafka topics empty | Collector 1 not receiving from SDK |
| Backend 500 on queries | ClickHouse connection or schema mismatch |
| UI shows empty charts | Time range filter too narrow, or backend error |
