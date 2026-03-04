Run a ClickHouse query against the local OTEL database.

1. Ask for the query if not provided
2. Validate it's a SELECT-only query (block any DDL/DML)
3. Execute via: `docker exec pulse-clickhouse clickhouse-client --query "<the query>"`
4. Format and display results
5. If the query fails, explain the error and suggest corrections

Useful reference tables: `otel.otel_traces`, `otel.otel_logs`, `otel.otel_metrics_gauge`, `otel.stack_trace_events`
