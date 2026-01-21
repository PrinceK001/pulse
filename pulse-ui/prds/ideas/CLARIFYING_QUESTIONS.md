# Clarifying Questions for Data Documentation

This document lists all assumptions and questions that need to be answered to complete the data documentation accurately.

## S3/Vector Click Stream Data

### Critical Questions (High Priority)

1. **Vector Configuration**
   - Where is the Vector configuration file located?
   - What is the actual Vector configuration (vector.toml or equivalent)?
   - How does Vector route data to S3?

2. **S3 Storage Details**
   - What is the actual S3 bucket name for click stream data?
   - What is the actual S3 path structure? (e.g., `s3://bucket/clickstream/year=YYYY/month=MM/...`)
   - What file format is used? (NDJSON, Parquet, JSON)
   - What is the file naming convention?
   - What is the batching strategy? (time-based, size-based, batch size, timeout)

3. **Event Structure**
   - What is the actual event schema/structure stored in S3?
   - What fields are included in each event?
   - How are events serialized? (JSON, Protobuf, etc.)
   - What metadata is included? (user_id, session_id, device info, etc.)
   - How are nested attributes structured?
   - Can you provide a sample event from actual S3 data?

4. **Event Types**
   - Which event types are routed to S3 by Vector?
   - What filtering/transformation logic is applied?
   - Are events transformed before storage?
   - What PulseType values are included in S3?

5. **Athena Integration**
   - Does an Athena table exist for click stream data?
   - What is the Athena database name?
   - What is the table name?
   - What is the table schema? (columns, data types)
   - What is the S3 location path in the table definition?
   - What partition structure is used?
   - What file format? (Parquet, JSON, NDJSON)

6. **Data Retention**
   - ✅ **Answer**: Maximum of **1 month** of data is available
   - Old partitions are automatically cleaned up after 1 month
   - S3 lifecycle policies may be configured for automatic cleanup

7. **Data Flow**
   - How does Vector fit into the data flow?
   - Does Vector receive data directly from mobile apps?
   - Or does Vector consume from Kafka?
   - Or is there a different flow entirely?
   - What data goes to Vector vs OTEL Collector? (all data or filtered subset?)

---

## ClickHouse Data

### Questions

1. **Data Retention**
   - ✅ **Answer**: Maximum of **1 month** of data is available
   - No TTL configured in schema - retention managed externally
   - Old partitions are automatically cleaned up after 1 month

2. **Example Data**
   - Are the example data structures I provided accurate?
   - Should I verify with actual sample data from ClickHouse?

3. **Metrics**
   - Are only gauge metrics stored in `otel_metrics_gauge`?
   - Or are there other metric types (counter, histogram, summary)?
   - Are there other metric tables?

4. **PulseType Values**
   - Are there other PulseType values used in production that aren't in the constants file?
   - What's the difference between `device.crash` and `crash`?
   - Does `session.end` exist, or only `session.start`?

---

## MySQL Data

### Questions

1. **Interaction Details JSON**
   - Is the `details` JSON structure I documented accurate?
   - Are there any additional fields not shown?
   - What are the actual possible values for event operators?

2. **SDK Config JSON**
   - Is the `config_json` structure I documented accurate?
   - Are there any additional fields not shown?
   - What are all the possible feature names?

3. **Data Retention**
   - ✅ **Answer**: Maximum of **1 month** of data is available
   - Application metadata (interactions, alerts, configs) retained for up to 1 month
   - Old data is automatically cleaned up after 1 month

---

## API Details

### Questions

1. **DataType Discrepancy**
   - Frontend TypeScript shows: `"TRACES" | "EVENTS" | "METRICS" | "LOGS" | "EXCEPTIONS"`
   - Backend Java shows: `"TRACES" | "LOGS" | "METRICS" | "EXCEPTIONS"`
   - **Which is correct?** Is "EVENTS" a valid data type?

2. **Operators**
   - Are all operators I listed supported? (EQ, IN, NE, GT, LT, GTE, LTE, LIKE, ADDITIONAL)
   - Are there any other operators?

3. **Functions**
   - ✅ Confirmed from `Functions.java` - complete list documented

---

## Data Flow

### Questions

1. **Telemetry Flow**
   - ✅ Confirmed: Mobile App → OTEL Collector 1 → Kafka → OTEL Collector 2 → ClickHouse

2. **Click Stream Flow**
   - ⚠️ **How does Vector fit in?**
   - Does Vector receive data directly from mobile apps?
   - Or does Vector consume from Kafka?
   - What is the actual end-to-end flow?

---

## Query Patterns

### Questions

1. **Example Queries**
   - Are the example query patterns I provided accurate?
   - Should I verify with actual query examples from the codebase?
   - Is there a `Duration` field in `otel_logs` for sessions?

---

## General Questions

1. **Missing Information**
   - Are there other important data structures, tables, or formats not documented?
   - Are there other data storage systems (Redis, Elasticsearch, etc.)?
   - Are there other data pipelines or ETL processes?

2. **Data Quality**
   - What are the data quality checks in place?
   - How is data validated before storage?
   - What happens to malformed data?

3. **Performance**
   - What are the typical data volumes? (events per second, storage per day)
   - What are the query performance characteristics?
   - Are there any performance optimizations not documented?

---

## Next Steps

Please provide:
1. Vector configuration file
2. S3 bucket name and sample data structure
3. Athena table schema (if exists)
4. Answers to the questions above
5. Any additional documentation or examples

Once provided, I will update the data documentation with accurate, implementation-based information.

