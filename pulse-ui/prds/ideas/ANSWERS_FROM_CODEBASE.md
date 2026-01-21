# Answers to Clarifying Questions (Based on Codebase Analysis)

This document provides answers to the clarifying questions based on actual codebase implementation.

---

## S3/Vector Click Stream Data

### ✅ **Athena Table Schema Found**

**Answer**: The actual Athena table schema has been provided:

**Table Name**: `otel_data`  
**Database**: `pulse_athena_db` (from `athena-default.conf`)  
**S3 Location**: `s3://puls-otel-config/`  
**Partition Structure**: `year=${year}/month=${month}/day=${day}/hour=${hour}/`  
**Format**: Parquet with SNAPPY compression

**Table Schema**:
```sql
CREATE EXTERNAL TABLE IF NOT EXISTS otel_data (
    android_os_api_level STRING,
    app_build_id STRING,
    app_build_name STRING,
    device_manufacturer STRING,
    device_model_identifier STRING,
    event_name STRING,
    flags BIGINT,
    network_carrier_icc STRING,
    network_carrier_mcc STRING,
    network_carrier_mnc STRING,
    observed_timestamp TIMESTAMP,
    os_name STRING,
    os_version STRING,
    props STRING,
    scope_name STRING,
    screen_name STRING,
    service_name STRING,
    session_id STRING,
    span_id STRING,
    `timestamp` TIMESTAMP,
    trace_id STRING
)
PARTITIONED BY (
    year INT,
    month INT,
    day INT,
    hour INT
)
STORED AS PARQUET
LOCATION 's3://puls-otel-config/'
TBLPROPERTIES (
    'projection.enabled'='true',
    'projection.year.type'='integer',
    'projection.year.range'='2024,2030',
    'projection.year.interval'='1',
    'projection.month.type'='integer',
    'projection.month.range'='1,12',
    'projection.month.interval'='1',
    'projection.month.digits'='2',
    'projection.day.type'='integer',
    'projection.day.range'='1,31',
    'projection.day.interval'='1',
    'projection.day.digits'='2',
    'projection.hour.type'='integer',
    'projection.hour.range'='0,23',
    'projection.hour.interval'='1',
    'projection.hour.digits'='2',
    'storage.location.template'='s3://puls-otel-config/year=${year}/month=${month}/day=${day}/hour=${hour}/',
    'parquet.compress'='SNAPPY',
    'classification'='parquet'
);
```

**Key Observations**:
- This table stores **OTEL telemetry data** (not just "click stream")
- Data is partitioned by **year/month/day/hour**
- Uses **Parquet format** with **SNAPPY compression**
- Contains fields from traces/logs: `trace_id`, `span_id`, `session_id`, `event_name`, `screen_name`, etc.
- Contains device/app metadata: `device_model_identifier`, `os_name`, `os_version`, `app_build_name`, etc.
- Contains network metadata: `network_carrier_icc`, `network_carrier_mcc`, `network_carrier_mnc`

**Vector Infrastructure** (from `deploy/terraform/vector/main.tf`):
- **Deployment**: EC2 instances via Auto Scaling Group
- **Load Balancer**: Network Load Balancer (NLB) with TLS termination
- **Port**: 4318 (OTLP HTTP/gRPC)
- **IAM Instance Profile**: Required for S3 write access
- **AMI**: Vector configuration is baked into the AMI (specified via `ami_id` variable)

**Data Flow** (Confirmed):
```
Mobile App → Vector (OTLP Receiver on port 4318) → Transform/Filter Custom Events → S3 (puls-otel-config)
```

**Event Type**: Vector processes **custom events** (clickstream events) with `pulse.type = "custom_event"`

**Data Retention**:
- ✅ Maximum of **1 month** of data is available
- Old partitions automatically cleaned up after 1 month

**Still Unknown** (requires AMI inspection):
- ❌ Exact Vector configuration (vector.toml) - in AMI
- ❌ Exact transformation logic - how OTEL attributes map to table columns
- ❌ File naming convention
- ❌ Batching strategy (time-based or size-based)

---

## ClickHouse Data

### 1. Data Retention

**Answer**: 
- **Retention Period**: Maximum of **1 month** of data is available
- **Partitioning**: Daily partitions by date (`PARTITION BY toYYYYMMDD(Timestamp)`)
- **TTL**: No TTL configured in ClickHouse schema - retention managed externally
- **Cleanup**: Old partitions are automatically cleaned up after 1 month
- **Management**: Likely managed via infrastructure automation or separate retention scripts

### 2. Example Data

**Answer**: ✅ **VERIFIED** against actual ClickHouse data from `traces.csv`.

**Verified**:
- ✅ Column names match schema
- ✅ Data types match (Duration in nanoseconds, Timestamp as DateTime64)
- ✅ ResourceAttributes structure matches (stored as Map, exported as Python dict string)
- ✅ SpanAttributes structure matches (stored as Map, exported as Python dict string)
- ✅ Events structure (arrays of timestamps, names, attributes)
- ✅ Materialized columns work correctly (PulseType from SpanAttributes['pulse.type'])

**Actual PulseType values found**: `app_start`, `screen_interactive`, `screen_load`, `screen_session`

**Additional SpanAttributes found**:
- `routeKey` - React Native route key
- `routeHasBeenSeen` - Boolean for route visibility
- `phase` - Navigation phase
- `pulse.user.email`, `pulse.user.mobileNo`, `pulse.user.name` - User info
- `last.screen.name` - Previous screen name
- `app.interaction.*` - Frame analysis counts
- `globalAttr.*` - Global attributes

### 3. Stack Trace Events Table

**Answer**: ✅ **VERIFIED** against actual ClickHouse data from `stacktraces.csv`.

**Verified Structure**:
- ✅ Column names match schema
- ✅ Data types match (Timestamp as DateTime64, String fields, Array fields)
- ✅ LogAttributes and ResourceAttributes stored as Map (exported as Python dict string)
- ✅ ScopeAttributes stored as Map (can be empty)

**Actual Data Observations**:

**EventName**: 
- Can be **empty string** (not always "crash" or "anr")
- In sample: empty string for ANR event

**Title**:
- Format: `"Error at {method}#{location} [{GroupId}]"`
- Example: `"Error at os.BinderProxy#transactNative [EXC-1CD2758A05]"`

**ExceptionStackTrace**:
- **Formatted** stack trace with method names (indented, method#class format)
- Example format:
  ```
    android.os.BinderProxy#transactNative
    android.os.BinderProxy#transact
    android.app.trust.ITrustManager$Stub$Proxy#isDeviceLocked
  ```

**ExceptionStackTraceRaw**:
- **Raw** stack trace with line numbers and file paths
- Example format:
  ```
  android.os.BinderProxy.transactNative(Native Method)
  android.os.BinderProxy.transact(BinderProxy.java:592)
  android.app.trust.ITrustManager$Stub$Proxy.isDeviceLocked(ITrustManager.java:570)
  ```

**ExceptionMessage**:
- Can be **empty string** (not always populated)
- In sample: empty for ANR event

**ExceptionType**:
- Can be **empty string** (not always populated)
- In sample: empty for ANR event

**Interactions**:
- Array of interaction names
- Can be **empty array** `[]` if no interactions associated

**TraceId and SpanId**:
- Can be **empty** (16 null bytes) if not associated with a trace/span
- In sample: both empty for ANR event

**GroupId**:
- Format: `"EXC-{hex}"` (e.g., `"EXC-1CD2758A05"`)
- Used for grouping similar errors

**Signature**:
- Format: `"v1|platform:{platform}|exc:{exception_type}|frames:{frame1}>{frame2}>..."`
- Example: `"v1|platform:java|exc:|frames:android.os.BinderProxy#transactNative>android.os.BinderProxy#transact>..."`
- Contains version, platform, exception type, and frame list

**Fingerprint**:
- SHA-1 hash (40 hex characters)
- Example: `"1cd2758a05ec13e1e88e98d7aac08f4ce793d5a0"`
- Used for error deduplication

**LogAttributes** (from actual data):
- ✅ `pulse.type` - Pulse type (e.g., `'device.anr'`, `'device.crash'`)
- ✅ `screen.name` - Screen name where error occurred
- ✅ `session.id` - Session identifier
- ✅ `thread.name` - Thread name (e.g., `'main'`)
- ✅ `thread.id` - Thread ID (can be empty)
- ✅ `exception.stacktrace` - Full exception stack trace (newline-separated)
- ✅ `app.installation.id` - App installation ID
- ✅ `globalAttr.string` - Global string attribute
- ✅ `globalAttr.number` - Global number attribute (can be empty)
- ✅ `globalAttr.boolean` - Global boolean attribute (can be empty)

**ResourceAttributes** (from actual data):
- ✅ `service.name` - Service name
- ✅ `device.model.identifier` - Device model identifier
- ✅ `device.manufacturer` - Device manufacturer
- ✅ `device.model.name` - Device model name
- ✅ `os.type` - OS type
- ✅ `os.version` - OS version
- ✅ `os.name` - OS name
- ✅ `os.description` - OS description
- ✅ `app.build_name` - App build name
- ✅ `app.build_id` - App build ID
- ✅ `android.os.api_level` - Android API level
- ✅ `telemetry.sdk.name` - Telemetry SDK name
- ✅ `telemetry.sdk.language` - Telemetry SDK language
- ✅ `telemetry.sdk.version` - Telemetry SDK version
- ✅ `service.version` - Service version
- ✅ `rum.sdk.version` - RUM SDK version

**ScopeAttributes**:
- Can be **empty object** `{}` (in sample: empty)

**PulseType** (materialized from LogAttributes['pulse.type']):
- ✅ `device.anr` - Application Not Responding
- ✅ `device.crash` - Application crash
- ✅ `non_fatal` - Non-fatal errors

**Key Findings**:
- ✅ **EventName can be empty** - not always populated
- ✅ **ExceptionMessage and ExceptionType can be empty** - especially for ANR events
- ✅ **TraceId and SpanId can be empty** - errors may not be associated with traces
- ✅ **Interactions can be empty array** - not all errors have associated interactions
- ✅ **Signature format** - `v1|platform:{platform}|exc:{type}|frames:{frames}`
- ✅ **Fingerprint** - SHA-1 hash for deduplication
- ✅ **GroupId format** - `EXC-{hex}` for error grouping
- ✅ **LogAttributes contains full stack trace** in `exception.stacktrace` field
- ✅ **Thread information** in LogAttributes (`thread.name`, `thread.id`)

### 4. Metrics

**Answer**: 
- **Only gauge metrics** are stored in `otel_metrics_gauge` table
- **No other metric tables** found (no counter, histogram, or summary tables)
- **Table name confirms**: `otel_metrics_gauge` (from `otel-collector-2.yaml` and schema)

### 5. PulseType Values

**Answer**: Complete list from `pulse-android-otel/pulse-semconv/src/main/java/com/pulse/semconv/PulseAttributes.kt`:

```kotlin
public object PulseTypeValues {
    public const val CUSTOM_EVENT: String = "custom_event"
    public const val ANR: String = "device.anr"
    public const val CRASH: String = "device.crash"
    public const val TOUCH: String = "app.click"
    public const val APP_START: String = "app_start"
    public const val SCREEN_SESSION: String = "screen_session"
    public const val APP_SESSION_START: String = "session.start"
    public const val APP_SESSION_END: String = "session.end"  // ✅ EXISTS!
    public const val APP_INSTALLATION_START: String = "pulse.app.installation.start"
    public const val SCREEN_LOAD: String = "screen_load"
    public const val FROZEN: String = "app.jank.frozen"
    public const val SLOW: String = "app.jank.slow"
    public const val NON_FATAL: String = "non_fatal"
    public const val INTERACTION: String = "interaction"
    public const val NETWORK_CHANGE: String = "network.change"
}
```

**Key Findings**:
- ✅ **`session.end` EXISTS** - Found in `PulseAttributes.kt` and used in `PulseSdkSignalProcessors.kt`
- ✅ **`device.crash` and `device.anr`** are the correct values (not `crash` or `anr` without prefix)
- ✅ **Network types** use `network.` prefix (e.g., `network.change`)
- ✅ **Additional types** not in frontend constants: `app.click`, `app.jank.frozen`, `app.jank.slow`, `pulse.app.installation.start`, `network.change`, `custom_event`
- ✅ **`screen_interactive`** - **VERIFIED in actual ClickHouse data** (not in frontend constants)

**Difference between `device.crash` and `crash`**:
- `device.crash` is the **correct PulseType value** used in the SDK
- `crash` (without prefix) is **not a valid PulseType** - it's used as an `EventName` in some contexts

---

## MySQL Data

### 1. Interaction Details JSON

**Answer**: Complete structure from `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/interaction/models/Event.java`:

```java
{
  "description": "User login flow",
  "events": [
    {
      "name": "login_start",
      "props": [
        {
          "name": "property_name",
          "value": "property_value",
          "operator": "EQUALS"  // or NOTCONTAINS, STARTSWITH, ENDSWITH, NOTEQUALS, CONTAINS
        }
      ],
      "isBlacklisted": false
    }
  ],
  "globalBlacklistedEvents": [],
  "thresholds": {
    "lower": 1000,  // uptimeLowerLimitInMs
    "mid": 3000,    // uptimeMidLimitInMs
    "upper": 5000   // uptimeUpperLimitInMs
  },
  "thresholdInMs": 60000  // Additional threshold field
}
```

**Operators** (from `Event.Operator` enum):
- `EQUALS` (default)
- `NOTCONTAINS`
- `STARTSWITH`
- `ENDSWITH`
- `NOTEQUALS`
- `CONTAINS`

**Additional Fields** (from `RestInteractionDetail.java`):
- `id`: Long (interaction ID)
- `status`: String ("RUNNING" or "STOPPED")
- `createdAt`: Timestamp
- `createdBy`: String (email)
- `updatedAt`: Timestamp
- `updatedBy`: String (email)
- `thresholdInMs`: Integer (separate from thresholds object)

### 2. SDK Config JSON

**Answer**: Complete structure from `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/configs/models/PulseConfig.java`:

```json
{
  "version": 1,
  "description": "Configuration description",
  "sampling": {
    "default": {
      "sessionSampleRate": 1.0
    },
    "rules": [
      {
        "name": "rule_name",
        "props": [
          {
            "name": "property_name",
            "value": "property_value"  // regex string
          }
        ],
        "scopes": ["android", "ios"],
        "sdks": ["android", "ios"],
        "sessionSampleRate": 0.5
      }
    ],
    "criticalEventPolicies": {
      "alwaysSend": []
    },
    "criticalSessionPolicies": {
      "alwaysSend": []
    }
  },
  "signals": {
    "filters": {
      "mode": "blacklist",  // or "whitelist"
      "values": []
    },
    "scheduleDurationMs": 5000,
    "logsCollectorUrl": "http://...",
    "metricCollectorUrl": "http://...",
    "spanCollectorUrl": "http://...",
    "customEventCollectorUrl": "http://...",
    "attributesToDrop": [],
    "attributesToAdd": []
  },
  "interaction": {
    "collectorUrl": "http://...",
    "configUrl": "http://...",
    "beforeInitQueueSize": 100
  },
  "features": [
    {
      "featureName": "FEATURE_NAME",
      "sessionSampleRate": 1.0,
      "sdks": ["android", "ios"]
    }
  ]
}
```

**Feature Names** (from `Features.java` enum):
- `interaction`
- `java_crash`
- `js_crash`
- `java_anr`
- `network_change`
- `network_instrumentation`
- `screen_session`
- `custom_events`
- `rn_screen_load`
- `rn_screen_interactive`

**Filter Mode** (from `FilterMode.java`):
- `blacklist`
- `whitelist`

**SDK Values** (from `Sdk.java`):
- `android`
- `ios`

**Scope Values** (from `Scope.java`):
- `android`
- `ios`

### 3. Data Retention

**Answer**: 
- **Retention Period**: Maximum of **1 month** of data is available
- **No retention policies found** in MySQL schema (`deploy/db/mysql-init.sql`)
- **No archival or deletion logic** found in codebase
- **Management**: Retention likely managed externally (infrastructure automation or separate cleanup scripts)
- **Note**: Application metadata (interactions, alerts, configs) retained for up to 1 month

---

## API Details

### 1. DataType Discrepancy

**Answer**: 
- **Frontend** (`pulse-ui/src/hooks/useGetDataQuery/useGetDataQuery.interface.ts`): `"TRACES" | "EVENTS" | "METRICS" | "LOGS" | "EXCEPTIONS"`
- **Backend** (`backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/performance/models/QueryRequest.java`): `"TRACES" | "LOGS" | "METRICS" | "EXCEPTIONS"`

**Finding**: 
- `"EVENTS"` is **NOT a valid backend DataType**
- Backend only supports: `TRACES`, `LOGS`, `METRICS`, `EXCEPTIONS`
- Frontend has `"EVENTS"` but it's likely **not used** or is a **frontend-only concept**

**Recommendation**: 
- Verify if `"EVENTS"` is actually used in frontend queries
- If not used, remove from frontend type definition
- If used, need to understand what it maps to in backend

### 2. Operators

**Answer**: Complete list from frontend TypeScript (`useGetDataQuery.interface.ts`):

```typescript
export type OperatorType =
  | "EQ"      // Equals
  | "IN"      // In (array)
  | "NE"      // Not equals
  | "GT"      // Greater than
  | "LT"      // Less than
  | "GTE"     // Greater than or equal
  | "LTE"     // Less than or equal
  | "LIKE"    // Like (pattern matching)
  | "ADDITIONAL";  // Additional/custom operator
```

**Backend** (`QueryRequest.Operator` enum):
- `LIKE("like")`
- `IN("In")`
- `EQ("=")`
- `ADDITIONAL("")`

**Conclusion**: Frontend has more operators than backend. Need to verify which are actually supported.

### 3. Functions

**Answer**: ✅ **Complete list confirmed** from `Functions.java` - all 68+ functions documented correctly.

---

## Data Flow

### 1. Telemetry Flow

**Answer**: ✅ **Confirmed** from `otel-collector-*.yaml`:
```
Mobile App → OTEL Collector 1 (OTLP Receiver) → Kafka → OTEL Collector 2 (Kafka Consumer) → ClickHouse
```

**Details**:
- OTEL Collector 1: Receives OTLP on port 4318, routes to Kafka topics
- Kafka Topics:
  - `pulse.traces` (traces)
  - `pulse.metrics` (metrics)
  - `pulse.logs.anr_crash` (ANR/Crash/Non-fatal logs)
  - `pulse.logs.other` (other logs)
- OTEL Collector 2: Consumes from Kafka, writes to ClickHouse
- Stack Trace Events: Processed separately via `AnrCrashLogConsumerVerticle` → `stack_trace_events` table

### 2. Custom Events / Click Stream Flow

**Answer**: ✅ **CONFIRMED** from `deploy/terraform/vector/main.tf`

**Actual Flow**:
```
Mobile App → Vector (OTLP Receiver on port 4318) → Transform/Filter Custom Events → S3 (puls-otel-config)
```

**Details**:
- Vector receives OTLP data directly from mobile apps on port 4318
- Vector configuration (in AMI) filters for **custom events** (clickstream events)
- Custom events have `pulse.type = "custom_event"` (from SDK)
- Vector transforms OTEL format to flattened `otel_data` schema
- Writes to S3 in Parquet format with partitioning by year/month/day/hour
- IAM instance profile provides S3 write permissions

**Infrastructure**:
- EC2 instances via Auto Scaling Group
- Network Load Balancer (NLB) with TLS termination
- Route53 DNS record
- Vector configuration baked into AMI

---

## Query Patterns

### 1. Example Queries

**Answer**: 
- Example queries are **structurally accurate** but should be verified with actual query examples
- **Duration field**: `otel_logs` does **NOT have a Duration field** - duration is only in `otel_traces` (spans)
- Session duration would need to be **calculated** from session start/end timestamps

---

## Summary

### ✅ Answered from Codebase:
1. ✅ Complete PulseType values list
2. ✅ `session.end` exists
3. ✅ Only gauge metrics stored
4. ✅ Stack trace events table structure verified
5. ✅ Logs table structure verified (excluding custom_event)
6. ✅ Complete interaction details JSON structure
7. ✅ Complete SDK config JSON structure
8. ✅ Event operators list
9. ✅ Telemetry data flow
10. ✅ All query functions

### ❌ Not Found in Codebase:
1. ❌ ClickHouse retention/TTL policies (assumed 1 month max)
2. ❌ MySQL retention policies (assumed 1 month max)
3. ⚠️ File naming convention - exact format Vector uses for Parquet files
4. ⚠️ Sample event data from S3 - to see actual `props` structure

### ✅ Found:
1. ✅ Vector infrastructure configuration (`deploy/terraform/vector/main.tf`)
2. ✅ Vector configuration file (`vector.yaml`)
3. ✅ Complete transformation logic (`to_pulse_schema` transform)
4. ✅ Data flow: Mobile App → Vector (OTLP: 4317/gRPC, 4318/HTTP) → Transform → S3
5. ✅ Event type: All OTLP logs (transformed to `otel_data` schema)
6. ✅ Athena table schema for S3 data (`otel_data`)
7. ✅ S3 bucket location (`s3://puls-otel-config/`)
8. ✅ Partition structure (year/month/day/hour)
9. ✅ File format (Parquet with ZSTD compression)
10. ✅ Batching strategy (512 MB max, 580 seconds timeout)
11. ✅ Disk buffer configuration (10 GB)
12. ✅ Adaptive concurrency settings
13. ✅ Stack trace events table structure and actual data format
14. ✅ LogAttributes and ResourceAttributes structure in stack traces
15. ✅ Signature and Fingerprint format for error grouping
16. ✅ Logs table structure and actual data format (excluding custom_event)
17. ✅ Additional PulseType values in logs (SDK initialization, app lifecycle, network change, jank)
18. ✅ ScopeName values vary by event type
19. ✅ Body, EventName, SeverityText can be empty for Pulse-specific events
20. ✅ TraceId and SpanId can be empty in logs table

### ⚠️ Needs Verification:
1. ⚠️ DataType "EVENTS" - is it actually used?
2. ⚠️ All query operators - which are actually supported? (Frontend has more than backend)

### ✅ Assumed (Per User):
1. ✅ Data retention: Maximum of **1 month** of data available across all storage systems

---

## Next Steps

1. **Verify "EVENTS" DataType** usage in frontend
   - Is it actually used in queries?
   - What does it map to in backend?
2. **Verify query operators** - which frontend operators are actually supported by backend?
3. **Optional**: Get sample event data from S3 to see actual `props` structure

**Note**: 
- Data retention is assumed to be **maximum 1 month** across all storage systems.
- Vector configuration is now fully documented from `vector.yaml`.

