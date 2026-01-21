# Pulse Data Documentation

**✅ Verified**: This documentation has been verified against actual ClickHouse data. See `DATA_VERIFICATION.md` for verification details.

## Overview

This document provides a comprehensive guide to all data storage, formats, and structures in the Pulse observability platform. It covers:
- **ClickHouse Database** - Telemetry data (traces, logs, metrics, exceptions)
- **S3 Storage (via Vector)** - Click stream data (user interactions, events, navigation)
- **MySQL Database** - Application metadata (interactions, alerts, configs)
- **API Formats** - Request/response structures
- **Frontend Data Structures** - TypeScript interfaces and types

---

## Table of Contents

1. [ClickHouse Database (Telemetry Data)](#clickhouse-database-telemetry-data)
2. [S3 Storage (Click Stream Data via Vector)](#s3-storage-click-stream-data-via-vector)
3. [MySQL Database (Application Metadata)](#mysql-database-application-metadata)
4. [Data Types & Formats](#data-types--formats)
5. [API Request/Response Formats](#api-requestresponse-formats)
6. [Frontend Data Structures](#frontend-data-structures)
7. [PulseType Constants](#pulsetype-constants)
8. [Column Name Conventions](#column-name-conventions)

---

## ClickHouse Database (Telemetry Data)

**Database**: `otel`  
**Engine**: ClickHouse MergeTree  
**Purpose**: Stores all telemetry data (traces, logs, metrics, exceptions) from mobile applications

### 1. `otel_traces` Table

**Purpose**: Stores distributed tracing data including spans, screen sessions, interactions, and network requests.

**Schema**:

| Column | Type | Description |
|--------|------|-------------|
| `Timestamp` | DateTime64(9, 'UTC') | Event timestamp with nanosecond precision |
| `TraceId` | String | Unique trace identifier (hex string) |
| `SpanId` | FixedString(16) | Unique span identifier |
| `ParentSpanId` | FixedString(16) | Parent span identifier |
| `TraceState` | String | Trace state (W3C standard) |
| `SpanName` | LowCardinality(String) | Name of the span (e.g., "screen_session", "http.request") |
| `SpanKind` | LowCardinality(String) | Span kind (CLIENT, SERVER, INTERNAL, etc.) |
| `ServiceName` | LowCardinality(String) | Service name (typically app identifier) |
| `ResourceAttributes` | Map(LowCardinality(String), String) | Resource-level attributes (device, OS, app info) |
| `ScopeName` | LowCardinality(String) | Instrumentation scope name |
| `ScopeVersion` | LowCardinality(String) | Instrumentation scope version |
| `SpanAttributes` | Map(LowCardinality(String), String) | Span-level attributes (screen name, user ID, etc.) |
| `Duration` | Int64 | Span duration in nanoseconds |
| `StatusCode` | LowCardinality(String) | Status code (OK, ERROR, UNSET) |
| `StatusMessage` | String | Status message |
| `Events.Timestamp` | Array(DateTime64(9, 'UTC')) | Array of event timestamps |
| `Events.Name` | Array(LowCardinality(String)) | Array of event names |
| `Events.Attributes` | Array(Map(LowCardinality(String), String)) | Array of event attribute maps |
| `Links.TraceId` | Array(String) | Linked trace IDs |
| `Links.SpanId` | Array(String) | Linked span IDs |
| `Links.TraceState` | Array(String) | Linked trace states |
| `Links.Attributes` | Array(Map(LowCardinality(String), String)) | Linked span attributes |

**Materialized Columns** (computed from attributes):

| Column | Source | Description |
|--------|--------|-------------|
| `PulseType` | `SpanAttributes['pulse.type']` | Pulse event type (interaction, screen_session, etc.) |
| `SessionId` | `SpanAttributes['session.id']` | Session identifier |
| `UserId` | `SpanAttributes['user.id']` | User identifier |
| `AppVersion` | `ResourceAttributes['app.build_name']` | App version/build name |
| `SDKVersion` | `ResourceAttributes['rum.sdk.version']` | SDK version |
| `Platform` | `ResourceAttributes['os.name']` | Platform (Android, iOS) |
| `OsVersion` | `ResourceAttributes['os.version']` | OS version |
| `GeoState` | `SpanAttributes['geo.region.iso_code']` | Geographic state/region |
| `GeoCountry` | `SpanAttributes['geo.country.iso_code']` | Geographic country |
| `DeviceModel` | `ResourceAttributes['device.model.name']` | Device model name |
| `NetworkProvider` | `SpanAttributes['network.carrier.name']` | Network carrier/provider |

**Partitioning**: By date (`toYYYYMMDD(Timestamp)`)  
**Ordering**: `(ServiceName, PulseType, SpanName, Timestamp)`  
**Indexes**: 
- `TraceId` (bloom filter)
- `UserId` (bloom filter)

**Common PulseType Values** (from `pulse-ui/src/constants/PulseOtelSemcov.ts` and actual data):
- `interaction` - Critical user interactions
- `screen_session` - Screen visit sessions
- `screen_load` - Screen load events
- `screen_interactive` - Screen interactive events (✅ **Found in production data**, not in frontend constants)
- `navigation` - Navigation events
- `app_start` - App startup events
- `session.start` - Session start events (in logs)

**✅ Verified from actual data**: `app_start`, `screen_interactive`, `screen_load`, `screen_session` are all used in production.

**Example Data** (from actual ClickHouse data):
```json
{
  "Timestamp": "2026-01-19 11:19:29.640278794",
  "TraceId": "9bdef440e1d951f2f0ceaecaf33de0a7",
  "SpanId": "613705faa0749b69",
  "ParentSpanId": "6cd98be93c0b99a4",
  "TraceState": "",
  "SpanName": "AppStart",
  "SpanKind": "Internal",
  "ServiceName": "FC-Local",
  "PulseType": "app_start",
  "SessionId": "fff90f344b854ef6a9f8afff378d204c",
  "UserId": "",
  "Duration": 29605625,
  "StatusCode": "Unset",
  "ResourceAttributes": {
    "android.os.api_level": "36",
    "app.build_id": "10960287",
    "app.build_name": "8.3.0_10960287",
    "device.manufacturer": "Google",
    "device.model.identifier": "sdk_gphone64_arm64",
    "device.model.name": "sdk_gphone64_arm64",
    "os.description": "BP22.250325.006",
    "os.name": "Android",
    "os.type": "linux",
    "os.version": "16",
    "rum.sdk.version": "0.0.13-alpha-SNAPSHOT",
    "service.name": "FC-Local",
    "service.version": "8.3.0_10960287",
    "telemetry.sdk.language": "java",
    "telemetry.sdk.name": "pulse_android_rn",
    "telemetry.sdk.version": "1.54.1"
  },
  "SpanAttributes": {
    "activity.name": "MainActivity",
    "app.installation.id": "057aedfa-73f7-4db4-bea5-b87e8b2ab89f",
    "app.interaction.analysed_frame_count": "0",
    "app.interaction.frozen_frame_count": "0",
    "app.interaction.slow_frame_count": "0",
    "app.interaction.unanalysed_frame_count": "0",
    "globalAttr.boolean": "true",
    "globalAttr.number": "245",
    "globalAttr.string": "fancode_testing",
    "last.screen.name": "ScreenStackFragment",
    "network.carrier.icc": "us",
    "network.carrier.mcc": "310",
    "network.carrier.mnc": "260",
    "network.carrier.name": "T-Mobile - US",
    "network.connection.type": "wifi",
    "screen.name": "MainActivity",
    "session.id": "fff90f344b854ef6a9f8afff378d204c",
    "start.type": "hot"
  },
  "Events.Timestamp": [
    "2026-01-19 11:19:29.640562419",
    "2026-01-19 11:19:29.641205044",
    "2026-01-19 11:19:29.652620294"
  ],
  "Events.Name": [
    "activityPreStarted",
    "activityStarted",
    "activityPostStarted"
  ],
  "Events.Attributes": [{}, {}, {}]
}
```

**Note**: 
- Duration is in **nanoseconds** (29605625 = 29.6 milliseconds)
- ResourceAttributes and SpanAttributes are stored as `Map(LowCardinality(String), String)` in ClickHouse
- CSV export shows them as Python dict strings with single quotes

---

### 2. `otel_logs` Table

**Purpose**: Stores log events including session starts, app lifecycle events, network changes, SDK initialization events, and jank events. **Note**: `custom_event` entries are stored in S3 via Vector, not in ClickHouse.

**Schema**:

| Column | Type | Description |
|--------|------|-------------|
| `Timestamp` | DateTime64(9) | Event timestamp with nanosecond precision |
| `TraceId` | String | Associated trace ID (can be empty if not associated with trace) |
| `SpanId` | FixedString(16) | Associated span ID (can be empty if not associated with span) |
| `TraceFlags` | UInt32 | Trace flags |
| `SeverityText` | LowCardinality(String) | Log severity (INFO, WARN, ERROR, etc.) - can be empty for Pulse-specific events |
| `SeverityNumber` | Int32 | Numeric severity level - can be 0 for Pulse-specific events |
| `ServiceName` | LowCardinality(String) | Service name |
| `Body` | String | Log message body (can be empty for Pulse-specific events) |
| `ResourceSchemaUrl` | String | Resource schema URL |
| `ResourceAttributes` | Map(LowCardinality(String), String) | Resource attributes |
| `ScopeSchemaUrl` | String | Scope schema URL |
| `ScopeName` | String | Scope name |
| `ScopeVersion` | String | Scope version |
| `ScopeAttributes` | Map(LowCardinality(String), String) | Scope attributes |
| `LogAttributes` | Map(LowCardinality(String), String) | Log-specific attributes |
| `EventName` | LowCardinality(String) | Event name (can be empty, event type is in `pulse.type`) |

**Materialized Columns**:

| Column | Source | Description |
|--------|--------|-------------|
| `SessionId` | `LogAttributes['session.id']` | Session identifier |
| `UserId` | `LogAttributes['user.id']` | User identifier |
| `PulseType` | `LogAttributes['pulse.type']` | Pulse event type (default: 'otel') |
| `AppVersion` | `ResourceAttributes['app.build_name']` | App version |
| `Platform` | `ResourceAttributes['os.name']` | Platform |
| `OsVersion` | `ResourceAttributes['os.version']` | OS version |
| `DeviceModel` | `ResourceAttributes['device.model.name']` | Device model |
| `NetworkProvider` | `LogAttributes['network.carrier.name']` | Network provider |
| `GeoState` | `LogAttributes['geo.region.iso_code']` | Geographic state |
| `GeoCountry` | `LogAttributes['geo.country.iso_code']` | Geographic country |

**Partitioning**: By date (`toYYYYMMDD(Timestamp)`)  
**Ordering**: `(ServiceName, PulseType, EventName, SeverityText, toUnixTimestamp(Timestamp), TraceId)`  
**Indexes**: `TraceId` (bloom filter)

**Common PulseType Values** (from codebase and actual data, excluding custom_event which is stored in S3):
- `session.start` - Session start event (✅ **Verified in actual data**)
- `pulse.user.session.start` - User session start (✅ **Verified in actual data**)
- `session.end` - Session end event (from SDK codebase)
- `device.anr` - Application Not Responding (routed to stack_trace_events table)
- `device.crash` - Application crash (routed to stack_trace_events table)
- `non_fatal` - Non-fatal errors (routed to stack_trace_events table)
- `device.app.lifecycle` - App lifecycle events (✅ **Verified in actual data**)
- `network.change` - Network change events (✅ **Verified in actual data**)
- `app.jank.slow` - Slow frame events (✅ **Verified in actual data**)
- `rum.sdk.init.started` - SDK initialization started (✅ **Verified in actual data**)
- `rum.sdk.init.net.provider` - SDK network provider initialization (✅ **Verified in actual data**)
- `rum.sdk.init.span.exporter` - SDK span exporter initialization (✅ **Verified in actual data**)
- `otel` - Default value when pulse.type is not set

**⚠️ Note**: `custom_event` entries are **NOT stored in ClickHouse** - they are stored in S3 via Vector.

**Example Data** (from actual ClickHouse data):
```json
{
  "Timestamp": "2026-01-19 11:34:13.174925000",
  "TraceId": "",
  "SpanId": "",
  "TraceFlags": 0,
  "SeverityText": "",
  "SeverityNumber": 0,
  "ServiceName": "FC-Local",
  "Body": "",
  "ResourceSchemaUrl": "",
  "ScopeSchemaUrl": "",
  "ScopeName": "otel.session",
  "ScopeVersion": "",
  "EventName": "",
  "PulseType": "session.start",
  "SessionId": "de81d06fb03e012ff716062ad9bcde6d",
  "UserId": "174198919",
  "LogAttributes": {
    "pulse.type": "session.start",
    "session.id": "de81d06fb03e012ff716062ad9bcde6d",
    "user.id": "174198919",
    "screen.name": "unknown",
    "app.installation.id": "057aedfa-73f7-4db4-bea5-b87e8b2ab89f",
    "network.connection.type": "unavailable",
    "globalAttr.boolean": "true",
    "globalAttr.number": "245",
    "globalAttr.string": "fancode_testing"
  },
  "ResourceAttributes": {
    "android.os.api_level": "36",
    "app.build_id": "10960287",
    "app.build_name": "8.3.0_10960287",
    "device.manufacturer": "Google",
    "device.model.identifier": "sdk_gphone64_arm64",
    "device.model.name": "sdk_gphone64_arm64",
    "os.description": "BP22.250325.006",
    "os.name": "Android",
    "os.type": "linux",
    "os.version": "16",
    "rum.sdk.version": "0.0.13-alpha-SNAPSHOT",
    "service.name": "FC-Local",
    "service.version": "8.3.0_10960287",
    "telemetry.sdk.language": "java",
    "telemetry.sdk.name": "pulse_android_rn",
    "telemetry.sdk.version": "1.54.1"
  },
  "ScopeAttributes": {}
}
```

**Notes**:
- **Body** can be empty for Pulse-specific events (event info is in LogAttributes)
- **EventName** can be empty (event type is in `pulse.type`)
- **SeverityText** and **SeverityNumber** can be empty/zero for Pulse-specific events
- **TraceId** and **SpanId** can be empty if event is not associated with a trace/span
- **ScopeName** varies by event type (e.g., `otel.session`, `otel.initialization.events`, `io.opentelemetry.lifecycle`)
- **ScopeAttributes** can be empty object `{}`

---

### 3. `otel_metrics_gauge` Table

**Purpose**: Stores gauge metrics (point-in-time measurements) like frame rates, memory usage, etc.

**⚠️ Question**: Are there other metric types stored? (e.g., counter, histogram) Or only gauge metrics?

**Schema**:

| Column | Type | Description |
|--------|------|-------------|
| `ResourceAttributes` | Map(LowCardinality(String), String) | Resource attributes |
| `ResourceSchemaUrl` | String | Resource schema URL |
| `ScopeName` | String | Scope name |
| `ScopeVersion` | String | Scope version |
| `ScopeAttributes` | Map(LowCardinality(String), String) | Scope attributes |
| `ScopeDroppedAttrCount` | UInt32 | Dropped attribute count |
| `ScopeSchemaUrl` | String | Scope schema URL |
| `ServiceName` | LowCardinality(String) | Service name |
| `MetricName` | String | Metric name |
| `MetricDescription` | String | Metric description |
| `MetricUnit` | String | Metric unit |
| `Attributes` | Map(LowCardinality(String), String) | Metric attributes |
| `StartTimeUnix` | DateTime64(9) | Start time (Unix nanoseconds) |
| `TimeUnix` | DateTime64(9) | Measurement time (Unix nanoseconds) |
| `Value` | Float64 | Metric value |
| `Flags` | UInt32 | Metric flags |
| `Exemplars.FilteredAttributes` | Array(Map(LowCardinality(String), String)) | Exemplar attributes |
| `Exemplars.TimeUnix` | Array(DateTime64(9)) | Exemplar timestamps |
| `Exemplars.Value` | Array(Float64) | Exemplar values |
| `Exemplars.SpanId` | Array(String) | Exemplar span IDs |
| `Exemplars.TraceId` | Array(String) | Exemplar trace IDs |

**Materialized Columns**:

| Column | Source | Description |
|--------|--------|-------------|
| `SessionId` | `Attributes['session.id']` | Session identifier |
| `UserId` | `Attributes['user.id']` | User identifier |
| `AppVersion` | `Attributes['app.build_name']` | App version |
| `Platform` | `ResourceAttributes['os.name']` | Platform |
| `OsVersion` | `ResourceAttributes['os.version']` | OS version |
| `DeviceModel` | `ResourceAttributes['device.model.name']` | Device model |
| `NetworkProvider` | `Attributes['network.carrier.name']` | Network provider |
| `GeoState` | `Attributes['geo.region.iso_code']` | Geographic state |
| `GeoCountry` | `Attributes['geo.country.iso_code']` | Geographic country |

**Partitioning**: By date (`toDate(TimeUnix)`)  
**Ordering**: `(ServiceName, MetricName, Attributes, toUnixTimestamp64Nano(TimeUnix))`

---

### 4. `stack_trace_events` Table

**Purpose**: Stores detailed crash and ANR events with stack traces for error grouping and analysis.

**Schema**:

| Column | Type | Description |
|--------|------|-------------|
| `Timestamp` | DateTime64(9, 'UTC') | Event timestamp |
| `EventName` | LowCardinality(String) | Event name (can be empty, especially for ANR events) |
| `Title` | String | Error title/summary |
| `ExceptionStackTrace` | String | Formatted stack trace (ZSTD compressed) |
| `ExceptionStackTraceRaw` | String | Raw stack trace (ZSTD compressed) |
| `ExceptionMessage` | String | Exception message (can be empty, especially for ANR events) |
| `ExceptionType` | LowCardinality(String) | Exception type/class (can be empty, especially for ANR events) |
| `Interactions` | Array(LowCardinality(String)) | Associated interactions (can be empty array) |
| `ScreenName` | LowCardinality(String) | Screen where error occurred |
| `UserId` | String | User identifier |
| `SessionId` | String | Session identifier |
| `Platform` | LowCardinality(String) | Platform (android/ios) |
| `OsVersion` | LowCardinality(String) | OS version |
| `DeviceModel` | LowCardinality(String) | Device model |
| `AppVersionCode` | LowCardinality(String) | App version code |
| `AppVersion` | LowCardinality(String) | App version name |
| `SdkVersion` | LowCardinality(String) | SDK version |
| `BundleId` | String | App bundle ID |
| `TraceId` | String | Associated trace ID (can be empty if not associated with trace) |
| `SpanId` | FixedString(16) | Associated span ID (can be empty if not associated with span) |
| `GroupId` | String | Error group identifier (format: `EXC-{hex}`, e.g., `EXC-1CD2758A05`) |
| `Signature` | String | Error signature (format: `v1|platform:{platform}|exc:{type}|frames:{frames}`) |
| `Fingerprint` | String | Error fingerprint (SHA-1 hash, 40 hex characters) |
| `ScopeAttributes` | Map(LowCardinality(String), String) | Scope attributes (can be empty object) |
| `LogAttributes` | Map(LowCardinality(String), String) | Log attributes (contains pulse.type, thread info, exception.stacktrace, etc.) |
| `ResourceAttributes` | Map(LowCardinality(String), String) | Resource attributes (device, OS, app, SDK info) |
| `PulseType` | LowCardinality(String) | Pulse type (materialized) |

**Partitioning**: By date (`toYYYYMMDD(Timestamp)`)  
**Ordering**: `(GroupId, ExceptionType, toUnixTimestamp(Timestamp))`

**Example Data** (from actual ClickHouse data):
```json
{
  "Timestamp": "2026-01-19 11:24:20.290584000",
  "EventName": "",
  "Title": "Error at os.BinderProxy#transactNative [EXC-1CD2758A05]",
  "ExceptionStackTrace": "  android.os.BinderProxy#transactNative\n  android.os.BinderProxy#transact\n  android.app.trust.ITrustManager$Stub$Proxy#isDeviceLocked\n  ...",
  "ExceptionStackTraceRaw": "android.os.BinderProxy.transactNative(Native Method)\nandroid.os.BinderProxy.transact(BinderProxy.java:592)\nandroid.app.trust.ITrustManager$Stub$Proxy.isDeviceLocked(ITrustManager.java:570)\n...",
  "ExceptionMessage": "",
  "ExceptionType": "",
  "Interactions": [],
  "ScreenName": "ScreenStackFragment",
  "UserId": "",
  "SessionId": "fff90f344b854ef6a9f8afff378d204c",
  "Platform": "Android",
  "OsVersion": "16",
  "DeviceModel": "sdk_gphone64_arm64",
  "AppVersionCode": "10960287",
  "AppVersion": "8.3.0_10960287",
  "SdkVersion": "0.0.13-alpha-SNAPSHOT",
  "BundleId": "",
  "TraceId": "",
  "SpanId": "",
  "GroupId": "EXC-1CD2758A05",
  "Signature": "v1|platform:java|exc:|frames:android.os.BinderProxy#transactNative>android.os.BinderProxy#transact>...",
  "Fingerprint": "1cd2758a05ec13e1e88e98d7aac08f4ce793d5a0",
  "ScopeAttributes": {},
  "LogAttributes": {
    "pulse.type": "device.anr",
    "screen.name": "ScreenStackFragment",
    "session.id": "fff90f344b854ef6a9f8afff378d204c",
    "thread.name": "main",
    "thread.id": "",
    "exception.stacktrace": "android.os.BinderProxy.transactNative(Native Method)\nandroid.os.BinderProxy.transact(BinderProxy.java:592)\n...",
    "app.installation.id": "057aedfa-73f7-4db4-bea5-b87e8b2ab89f",
    "globalAttr.string": "fancode_testing",
    "globalAttr.number": "",
    "globalAttr.boolean": ""
  },
  "ResourceAttributes": {
    "service.name": "FC-Local",
    "device.model.identifier": "sdk_gphone64_arm64",
    "device.manufacturer": "Google",
    "device.model.name": "sdk_gphone64_arm64",
    "os.type": "linux",
    "os.version": "16",
    "os.name": "Android",
    "os.description": "BP22.250325.006",
    "app.build_name": "8.3.0_10960287",
    "app.build_id": "10960287",
    "android.os.api_level": "36",
    "telemetry.sdk.name": "pulse_android_rn",
    "telemetry.sdk.language": "java",
    "telemetry.sdk.version": "1.54.1",
    "service.version": "8.3.0_10960287",
    "rum.sdk.version": "0.0.13-alpha-SNAPSHOT"
  },
  "PulseType": "device.anr"
}
```

**Notes**:
- **EventName** can be empty (especially for ANR events)
- **ExceptionMessage** and **ExceptionType** can be empty (especially for ANR events)
- **TraceId** and **SpanId** can be empty (errors may not be associated with traces)
- **Interactions** can be empty array if no interactions associated
- **Title format**: `"Error at {method}#{location} [{GroupId}]"`
- **GroupId format**: `"EXC-{hex}"` (e.g., `"EXC-1CD2758A05"`)
- **Signature format**: `"v1|platform:{platform}|exc:{exception_type}|frames:{frame1}>{frame2}>..."`
- **Fingerprint**: SHA-1 hash (40 hex characters) for error deduplication
- **ExceptionStackTrace**: Formatted with method names (indented, method#class format)
- **ExceptionStackTraceRaw**: Raw stack trace with line numbers and file paths
- **LogAttributes** contains full stack trace in `exception.stacktrace` field
- **Thread information** in LogAttributes (`thread.name`, `thread.id`)

---

## S3 Storage (Custom Events / Click Stream Data via Vector)

**Storage**: AWS S3  
**Component**: Vector.dev  
**Purpose**: Stores custom events (clickstream events) directly in S3 for long-term storage and analytics

**✅ Confirmed**: Infrastructure configuration in `deploy/terraform/vector/main.tf` and Athena table schema provided.

### Overview

Vector is a data pipeline tool that receives OTLP data from mobile applications and routes **custom events** (clickstream events) directly to S3. This provides:
- **Long-term storage** for historical custom event analysis
- **Cost-effective** storage compared to ClickHouse for large volumes
- **Query flexibility** via AWS Athena on S3 data
- **Data lake** architecture for advanced analytics
- **Separation of concerns**: Custom events stored in S3, other telemetry in ClickHouse

### Vector Infrastructure (Confirmed)

**Deployment**: AWS EC2 instances via Auto Scaling Group  
**Load Balancer**: Network Load Balancer (NLB) with TLS termination  
**Port**: 4318 (OTLP HTTP/gRPC) - configured in `deploy/terraform/vector/`  
**Health Check**: HTTP endpoint on port 8080  
**Terraform Configuration**: `deploy/terraform/vector/main.tf`

**Infrastructure Details**:
- Auto Scaling Group: `pulse-vector-asg`
- Target Group: `pulse-vector-tg`
- Launch Template: `pulse-vector-lt-*`
- Route53 DNS record configured
- TLS certificate via ACM

### Data Flow (Assumed - Needs Confirmation)

```
Mobile App → Vector (OTLP Receiver) → S3 Bucket (Click Stream Data)
```

**Vector receives** (assumed):
- OTLP traces (HTTP/gRPC on port 4318)
- Click stream events
- User interaction events
- Navigation events
- Custom business events

**Vector processes and routes** (assumed):
- Filters and transforms events
- Batches events for efficient storage
- Writes to S3 in optimized format (JSON, Parquet, or NDJSON)

### S3 Storage Structure

**✅ Confirmed**: Actual S3 structure from Athena table definition.

**S3 Bucket**: `puls-otel-config`  
**Path Structure**: `s3://puls-otel-config/year=%Y/month=%m/day=%d/hour=%H/` (Hive partitioning)  
**File Format**: Parquet with **ZSTD compression** (configured in Vector)  
**Partitioning**: By year, month, day, and hour

**Example Paths**:
- `s3://puls-otel-config/year=2024/month=01/day=15/hour=10/`
- `s3://puls-otel-config/year=2024/month=01/day=15/hour=11/`

**File Naming**: Managed by Vector - files named automatically with timestamps/batch IDs

**Batching Strategy** (from `vector.yaml`):
- **Max Bytes**: 512 MB (uncompressed)
- **Timeout**: 580 seconds (~10 minutes)
- **Strategy**: Size-based with time-based fallback
- **Rationale**: Large batches (512 MB) optimize Parquet compression efficiency
- **Target File Size**: ~150-500 MB compressed files

### Click Stream Event Structure

**✅ Confirmed**: Event structure from Athena table schema and Vector transformation logic.

**Data Format**: Parquet files containing flattened OTEL telemetry data

**Transformation Logic** (from `vector.yaml`):
The `to_pulse_schema` transform:
1. Extracts resource attributes (`res`) and log attributes (`attrs`)
2. Merges them (log attributes override resource attributes)
3. Promotes key fields to top-level columns
4. Stores remaining attributes in `props` field as JSON string

**Event Fields** (from `otel_data` table and Vector transform):

**Promoted Columns** (for fast queries):
- **Tracing**: `trace_id`, `span_id`, `session_id`
- **Event Info**: `event_name` (from log message), `timestamp`, `observed_timestamp`, `flags`
- **Screen/Scope**: `screen_name` (from `attrs."screen.name"`), `scope_name`, `service_name`
- **Device Info**: 
  - `device_manufacturer` (from `res."device.manufacturer"`)
  - `device_model_identifier` (from `res."device.model.identifier"`)
  - `os_name` (from `res."os.name"`)
  - `os_version` (from `res."os.version"`)
  - `android_os_api_level` (from `res."android.os.api_level"`)
- **App Info**: 
  - `app_build_id` (from `res."app.build_id"`)
  - `app_build_name` (from `res."app.build_name"`)
- **Network Info**: 
  - `network_carrier_mcc` (from `attrs."network.carrier.mcc"`)
  - `network_carrier_mnc` (from `attrs."network.carrier.mnc"`)
  - `network_carrier_icc` (from `attrs."network.carrier.icc"`)
- **Pulse State**: `pulse_app_state` (from `attrs."pulse.app_state"`)
- **Properties**: `props` (JSON string containing all merged attributes)

**Transformation Mapping**:
```yaml
# Resource attributes (res)
android_os_api_level: res."android.os.api_level"
os_version: res."os.version"
app_build_id: res."app.build_id"
app_build_name: res."app.build_name"
device_manufacturer: res."device.manufacturer"
device_model_identifier: res."device.model.identifier"
os_name: res."os.name"
service_name: res."service.name"

# Log attributes (attrs)
session_id: attrs."session.id"
screen_name: attrs."screen.name"
network_carrier_mcc: attrs."network.carrier.mcc"
network_carrier_mnc: attrs."network.carrier.mnc"
network_carrier_icc: attrs."network.carrier.icc"
pulse_app_state: attrs."pulse.app_state"

# Direct fields
event_name: .message
span_id: .span_id
trace_id: .trace_id
timestamp: .observed_timestamp
scope_name: .scope.name
flags: .flags

# Catch-all
props: encode_json(merge(res, attrs))
```

**Note**: 
- Events are stored in **Parquet format** with **ZSTD compression** (columnar storage)
- Key attributes are **promoted to top-level columns** for fast queries
- Remaining attributes stored in `props` field as JSON string
- This is a **denormalized/flattened** view of OTEL data for efficient querying
- `user_id` is not explicitly promoted - may be in `props` if present

### Event Types Stored in S3

**✅ Confirmed**: Vector processes **custom events** (clickstream events) and stores them in S3.

**Event Types**:
- **Custom Events** (clickstream events) - Primary focus of Vector ingestion
- Events with `event_name` field populated
- Traces (via `trace_id`, `span_id`)
- Logs (via `event_name`, `scope_name`)
- Metrics (likely via `event_name`)

**Transformation** (from `vector.yaml`):
- Vector receives OTLP logs on ports 4317 (gRPC) and 4318 (HTTP)
- `to_pulse_schema` transform processes all OTLP logs (not just custom events)
- Data is **flattened/denormalized** from OTEL format to match `otel_data` table schema
- Key attributes promoted to top-level columns for fast queries
- Remaining attributes stored in `props` field as JSON string
- Partitioned by timestamp (year/month/day/hour)
- Written as Parquet files with **ZSTD compression** (not SNAPPY)

**PulseType Values**: 
- PulseType is stored in `props` field (as part of merged attributes)
- Custom events have `pulse.type = "custom_event"` (from SDK)
- Vector processes all OTLP logs, not just custom events

**Vector Processing**:
- Receives OTLP logs from mobile apps
- Transforms via `to_pulse_schema` remap transform
- Batches events: 512 MB max or 580 seconds timeout
- Writes to S3 with adaptive concurrency (10-50 connections)
- Uses disk buffer (10 GB) for durability
- Partitions by year/month/day/hour based on event timestamp

### Vector Configuration

**✅ Confirmed**: Actual Vector configuration file (`vector.yaml`) provided.

**Configuration File**: `vector.yaml` (baked into AMI or provided via instance user_data)

**Configuration File**: `vector.yaml` (baked into AMI or provided via instance user_data)

**Infrastructure Configuration**: `deploy/terraform/vector/main.tf`

**Infrastructure Details**:
- **Deployment**: EC2 instances via Auto Scaling Group
- **Instance Type**: Configurable (e.g., `t3.large`)
- **Instance Count**: Configurable
- **Market Type**: Spot instances
- **IAM Instance Profile**: Required for S3 write access
- **Load Balancer**: Network Load Balancer (NLB) with TLS termination
- **Port**: 4318 (OTLP HTTP) and 4317 (OTLP gRPC)
- **Health Check**: HTTP endpoint on port 8080 (configurable)
- **DNS**: Route53 CNAME record pointing to NLB
- **Security**: TLS certificate via ACM, security groups for network access

**Vector Configuration** (`vector.yaml`):

**Sources**:
```yaml
sources:
  otlp_logs:
    type: opentelemetry
    grpc:
      address: "0.0.0.0:4317"
    http:
      address: "0.0.0.0:4318"
```

**Transform** (`to_pulse_schema`):
- Transforms OTEL logs to match `otel_data` table schema
- Merges resource attributes and log attributes
- Promotes key fields to top-level columns for fast queries
- Stores remaining attributes in `props` field as JSON string

**Sink** (`s3_events`):
- **Type**: `aws_s3`
- **Bucket**: `puls-otel-config`
- **Region**: `ap-south-1`
- **Key Prefix**: `year=%Y/month=%m/day=%d/hour=%H/` (Hive partitioning)
- **Encoding**: Parquet with ZSTD compression
- **Batching**: 
  - Max bytes: 512 MB (uncompressed)
  - Timeout: 580 seconds (~10 minutes)
- **Disk Buffer**: 
  - Type: disk
  - Max size: 10 GB
  - When full: block (backpressure)
- **Request**:
  - Concurrency: adaptive (10-50 connections)
  - Timeout: 60 seconds

**Data Flow for Custom Events**:
```
Mobile App → Vector (OTLP Receiver: 4317/gRPC, 4318/HTTP) → to_pulse_schema Transform → S3 (puls-otel-config bucket)
```

**Key Points**:
- Vector receives OTLP logs on ports 4317 (gRPC) and 4318 (HTTP)
- Transforms OTEL format to flattened schema matching `otel_data` table
- Writes to S3 in Parquet format with ZSTD compression
- Partitioned by year/month/day/hour for efficient querying
- Disk buffer provides durability (10 GB limit)
- Adaptive concurrency for optimal throughput
- Large batch sizes (512 MB) for efficient Parquet compression

### Querying S3 Data (Athena Table Schema)

**✅ Confirmed**: Actual Athena table schema provided.

**Database**: `pulse_athena_db` (from `backend/server/src/main/resources/conf/athena-default.conf`)  
**Table Name**: `otel_data`  
**S3 Location**: `s3://puls-otel-config/`  
**Partition Structure**: `year=${year}/month=${month}/day=${day}/hour=${hour}/`  
**File Format**: Parquet with SNAPPY compression

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
    'parquet.compress'='SNAPPY',  # Note: Vector actually uses ZSTD compression (see vector.yaml), but Athena table definition shows SNAPPY
    'classification'='parquet'
);
```

**Column Descriptions**:

| Column | Type | Description |
|--------|------|-------------|
| `android_os_api_level` | STRING | Android OS API level |
| `app_build_id` | STRING | App build ID |
| `app_build_name` | STRING | App build name/version |
| `device_manufacturer` | STRING | Device manufacturer |
| `device_model_identifier` | STRING | Device model identifier |
| `event_name` | STRING | Event name (from traces/logs) |
| `flags` | BIGINT | Event flags |
| `network_carrier_icc` | STRING | Network carrier ICC code |
| `network_carrier_mcc` | STRING | Network carrier MCC code |
| `network_carrier_mnc` | STRING | Network carrier MNC code |
| `observed_timestamp` | TIMESTAMP | Observation timestamp |
| `os_name` | STRING | Operating system name |
| `os_version` | STRING | Operating system version |
| `props` | STRING | Event properties (JSON string) |
| `scope_name` | STRING | Instrumentation scope name |
| `screen_name` | STRING | Screen name |
| `service_name` | STRING | Service name |
| `session_id` | STRING | Session identifier |
| `span_id` | STRING | Span identifier |
| `timestamp` | TIMESTAMP | Event timestamp |
| `trace_id` | STRING | Trace identifier |

**Example Query**:
```sql
SELECT 
    event_name,
    screen_name,
    COUNT(*) as event_count,
    COUNT(DISTINCT session_id) as unique_sessions
FROM pulse_athena_db.otel_data
WHERE year = 2024
  AND month = 1
  AND day = 15
  AND hour BETWEEN 0 AND 23
GROUP BY event_name, screen_name
ORDER BY event_count DESC
```

**Note**: This table stores OTEL telemetry data (traces, logs, metrics) in S3, not just "click stream" events. The data appears to be a flattened/denormalized view of telemetry data.

### Data Retention

**Retention Period**: Maximum of **1 month** of data is available.

**Retention Details**:
- Custom events data in S3 is retained for up to 1 month
- Old partitions are automatically cleaned up after 1 month
- Data is partitioned by year/month/day/hour for efficient deletion
- S3 lifecycle policies may be configured for automatic cleanup

**Note**: For long-term analysis beyond 1 month, data would need to be archived to a different storage tier or external system.

### Integration with Pulse

**Access Methods**:
1. **Direct S3 Access**: Via AWS SDK/CLI
2. **AWS Athena**: SQL queries on S3 data
3. **Backend API**: Backend can query S3 via Athena
4. **Universal Querying**: Frontend can query via universal query interface

**Use Cases**:
- Long-term click stream analysis
- User journey reconstruction
- Behavioral pattern analysis
- Historical trend analysis
- ML model training data
- Compliance and audit trails

### Differences from ClickHouse Data

| Aspect | ClickHouse | S3 (Vector) |
|--------|------------|-------------|
| **Data Type** | Structured telemetry | Raw click stream events |
| **Storage** | Database tables | Object storage |
| **Query** | SQL (ClickHouse) | SQL (Athena) or direct access |
| **Latency** | Real-time | Near real-time (batched) |
| **Retention** | 1 month (maximum) | 1 month (maximum) |
| **Cost** | Higher (compute + storage) | Lower (storage only) |
| **Use Case** | Real-time analytics | Custom events analysis (up to 1 month) |

### Notes

1. **Vector infrastructure is deployed** via Terraform (`deploy/terraform/vector/`)
2. **Vector listens on port 4318** for OTLP data (HTTP/gRPC)
3. **Actual Vector configuration** needs to be located and documented
4. **S3 bucket name and path structure** need to be confirmed from actual implementation
5. **Event schema and format** need to be documented from actual data
6. **Athena table schema** needs to be documented if it exists
7. **IAM permissions** required for Vector instances to write to S3
8. **Data retention policies** need to be documented

### Summary

**✅ Confirmed**:
1. ✅ Infrastructure: Terraform configuration in `deploy/terraform/vector/main.tf`
2. ✅ Vector Configuration: `vector.yaml` file (baked into AMI)
3. ✅ S3 bucket name: `puls-otel-config`
4. ✅ S3 path structure: `year=%Y/month=%m/day=%d/hour=%H/`
5. ✅ Event schema/structure: From `otel_data` Athena table
6. ✅ Athena table schema: `otel_data` table definition
7. ✅ File format: Parquet with ZSTD compression
8. ✅ Data flow: Mobile App → Vector (OTLP: 4317/gRPC, 4318/HTTP) → Transform → S3
9. ✅ Transformation logic: Complete mapping from OTEL to table columns
10. ✅ Batching strategy: 512 MB max bytes, 580 seconds timeout
11. ✅ Disk buffer: 10 GB for durability
12. ✅ Compression: Parquet with ZSTD (not SNAPPY)

**Vector Configuration Details**:
- **Sources**: OTLP logs on ports 4317 (gRPC) and 4318 (HTTP)
- **Transform**: `to_pulse_schema` - remaps OTEL logs to flattened schema
- **Sink**: S3 Parquet with adaptive concurrency, disk buffering, and optimized batching
- **Processing**: All OTLP logs are processed (not just custom events)
- **Partitioning**: Hive-style partitioning by year/month/day/hour

**Still Unknown**:
1. ⚠️ S3 lifecycle/retention policies (assumed 1 month max)
2. ⚠️ Sample event data from S3 - to see actual `props` structure
3. ⚠️ File naming convention - exact format Vector uses for Parquet files

---

## MySQL Database (Application Metadata)

**Database**: `pulse_db`  
**Engine**: MySQL/InnoDB  
**Purpose**: Stores application configuration, interactions, alerts, and metadata

### 1. `interaction` Table

**Purpose**: Stores critical interaction definitions and configurations.

| Column | Type | Description |
|--------|------|-------------|
| `interaction_id` | BIGINT | Primary key (auto-increment) |
| `name` | VARCHAR(255) | Interaction name (PascalCase) |
| `status` | VARCHAR(25) | Status (RUNNING, STOPPED) |
| `details` | JSON | Interaction configuration (events, thresholds, etc.) |
| `is_archived` | TINYINT(1) | Archive flag |
| `created_at` | TIMESTAMP | Creation timestamp |
| `created_by` | VARCHAR(255) | Creator email |
| `last_updated_at` | TIMESTAMP | Last update timestamp |
| `updated_by` | VARCHAR(255) | Last updater email |

**Details JSON Structure** (from `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/interaction/models/Event.java`):
```json
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
    },
    {
      "name": "login_complete",
      "props": [],
      "isBlacklisted": false
    }
  ],
  "globalBlacklistEvents": [],
  "thresholds": {
    "lower": 1000,  // milliseconds
    "mid": 3000,    // milliseconds
    "upper": 5000   // milliseconds
  }
}
```

**⚠️ Question**: Is this JSON structure accurate? Are there any additional fields not shown here?

---

### 2. `alerts` Table

**Purpose**: Stores alert definitions and configurations.

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT | Primary key (auto-increment) |
| `name` | TEXT | Alert name |
| `description` | TEXT | Alert description |
| `scope` | VARCHAR(100) | Scope type (interaction, screen, app_vitals, network_api) |
| `dimension_filter` | TEXT | Dimension filter JSON |
| `condition_expression` | VARCHAR(255) | Condition expression |
| `severity_id` | INT | Severity level (foreign key) |
| `notification_channel_id` | INT | Notification channel (foreign key) |
| `evaluation_period` | INT | Evaluation period (seconds) |
| `evaluation_interval` | INT | Evaluation interval (seconds) |
| `last_snoozed_at` | TIMESTAMP | Last snooze time |
| `snoozed_from` | TIMESTAMP | Snooze start time |
| `snoozed_until` | TIMESTAMP | Snooze end time |
| `created_by` | VARCHAR(255) | Creator email |
| `updated_by` | VARCHAR(255) | Last updater email |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | Last update timestamp |
| `is_active` | BOOLEAN | Active flag |

---

### 3. `alert_scope` Table

**Purpose**: Stores alert scope items (specific interactions, screens, etc. being monitored).

| Column | Type | Description |
|--------|------|-------------|
| `id` | INT | Primary key (auto-increment) |
| `alert_id` | INT | Alert ID (foreign key) |
| `name` | VARCHAR(255) | Scope item name (e.g., interaction name, screen name) |
| `conditions` | JSON | Additional conditions |
| `state` | VARCHAR(50) | Current state (NORMAL, FIRING, SNOOZED, NO_DATA) |
| `is_active` | BOOLEAN | Active flag |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | Last update timestamp |

---

### 4. `alert_evaluation_history` Table

**Purpose**: Stores alert evaluation history and results.

| Column | Type | Description |
|--------|------|-------------|
| `evaluation_id` | INT | Primary key (auto-increment) |
| `scope_id` | INT | Scope ID (foreign key) |
| `evaluation_result` | JSON | Evaluation result data |
| `state` | VARCHAR(50) | Evaluation state |
| `evaluated_at` | TIMESTAMP | Evaluation timestamp |

---

### 5. `pulse_sdk_configs` Table

**Purpose**: Stores SDK configuration versions.

| Column | Type | Description |
|--------|------|-------------|
| `version` | INT UNSIGNED | Version number (primary key, auto-increment) |
| `description` | TEXT | Configuration description |
| `is_active` | BOOLEAN | Active flag |
| `created_at` | TIMESTAMP | Creation timestamp |
| `created_by` | VARCHAR(255) | Creator email |
| `config_json` | JSON | SDK configuration JSON |

**Config JSON Structure**:
```json
{
  "sampling": {
    "default": {
      "sessionSampleRate": 1
    },
    "rules": [],
    "criticalEventPolicies": {
      "alwaysSend": []
    },
    "criticalSessionPolicies": {
      "alwaysSend": []
    }
  },
  "signals": {
    "filters": {
      "mode": "blacklist",
      "values": []
    },
    "scheduleDurationMs": 5000,
    "logsCollectorUrl": "http://...",
    "metricCollectorUrl": "http://...",
    "spanCollectorUrl": "http://...",
    "attributesToDrop": [],
    "attributesToAdd": []
  },
  "interaction": {
    "collectorUrl": "http://...",
    "configUrl": "http://...",
    "beforeInitQueueSize": 100
  },
  "features": [...]
}
```

---

### 6. `symbol_files` Table

**Purpose**: Stores symbol files for crash symbolication.

| Column | Type | Description |
|--------|------|-------------|
| `app_version` | VARCHAR(64) | App version |
| `app_version_code` | INT | App version code |
| `platform` | ENUM('ios','android') | Platform |
| `framework` | ENUM('java','js') | Framework |
| `file_content` | LONGBLOB | Symbol file binary content |
| `bundleid` | VARCHAR(255) | Bundle ID |

**Primary Key**: `(app_version, app_version_code, platform, framework)`

---

### 7. `athena_job` Table

**Purpose**: Tracks Athena query jobs for universal querying feature.

| Column | Type | Description |
|--------|------|-------------|
| `job_id` | VARCHAR(255) | Primary key (UUID) |
| `query_string` | TEXT | SQL query string |
| `user_email` | VARCHAR(255) | User email |
| `query_execution_id` | VARCHAR(255) | AWS Athena execution ID |
| `status` | VARCHAR(50) | Job status (RUNNING, COMPLETED, FAILED, CANCELLED) |
| `result_location` | VARCHAR(500) | S3 result location |
| `error_message` | TEXT | Error message (if failed) |
| `data_scanned_in_bytes` | BIGINT | Data scanned in bytes |
| `execution_time_millis` | BIGINT | Execution time |
| `engine_execution_time_millis` | BIGINT | Engine execution time |
| `query_queue_time_millis` | BIGINT | Queue time |
| `created_at` | TIMESTAMP | Creation timestamp |
| `updated_at` | TIMESTAMP | Last update timestamp |
| `completed_at` | TIMESTAMP | Completion timestamp |

---

## Data Types & Formats

### Timestamp Formats

- **ClickHouse**: `DateTime64(9, 'UTC')` - Nanosecond precision, UTC timezone
- **MySQL**: `TIMESTAMP` - Second precision, timezone-aware
- **API**: ISO 8601 strings (e.g., `"2024-01-15T10:30:45.123Z"`)
- **Frontend**: Unix milliseconds (number) or ISO 8601 strings

### Attribute Maps

Attributes are stored as `Map(LowCardinality(String), String)` in ClickHouse:
- Keys: Low cardinality strings (optimized for repeated values)
- Values: String (all values converted to strings)
- Example: `{"user.id": "user-123", "screen.name": "HomeScreen"}`

### Arrays

Arrays are used for:
- Events: `Array(DateTime64(9))`, `Array(String)`, `Array(Map(...))`
- Links: `Array(String)` for trace/span IDs
- Interactions: `Array(LowCardinality(String))` for interaction names

### JSON Fields

MySQL JSON fields store structured data:
- Interaction details
- Alert conditions
- SDK configurations
- Evaluation results

---

## API Request/Response Formats

### Data Query Request

**Endpoint**: `POST /api/v1/data/query`

**Request Body**:
```typescript
{
  dataType: "TRACES" | "LOGS" | "METRICS" | "EXCEPTIONS",  // Note: Frontend also has "EVENTS" - needs clarification
  timeRange: {
    start: string,  // ISO 8601 timestamp
    end: string     // ISO 8601 timestamp
  },
  select: [
    {
      function: FunctionType,  // See complete list below
      param: {
        field?: string,
        bucket?: string,
        expression?: string,
        [key: string]: any  // Additional params vary by function
      },
      alias: string
    }
  ],
  filters?: [
    {
      field: string,
      operator: "EQ" | "IN" | "NE" | "GT" | "LT" | "GTE" | "LTE" | "LIKE" | "ADDITIONAL",
      value: string | string[] | number | number[] | boolean | boolean[]
    }
  ],
  groupBy?: string[],
  orderBy?: [
    {
      field: string,
      direction: "ASC" | "DESC"
    }
  ],
  limit?: number,
  offset?: number
}
```

**Available Functions** (from `backend/server/src/main/java/org/dreamhorizon/pulseserver/resources/performance/models/Functions.java`):
- `APDEX` - Application Performance Index
- `CRASH` - Crash count
- `ANR` - Application Not Responding count
- `FROZEN_FRAME` - Frozen frame count
- `ANALYSED_FRAME` - Analysed frame count
- `UNANALYSED_FRAME` - Unanalysed frame count
- `DURATION_P99` - 99th percentile duration
- `DURATION_P50` - 50th percentile duration
- `DURATION_P95` - 95th percentile duration
- `COL` - Select column directly (requires `param.field`)
- `CUSTOM` - Custom ClickHouse expression (requires `param.expression`)
- `TIME_BUCKET` - Time bucket grouping (requires `param.bucket` and `param.field`)
- `INTERACTION_SUCCESS_COUNT` - Successful interaction count
- `INTERACTION_ERROR_COUNT` - Failed interaction count
- `INTERACTION_ERROR_DISTINCT_USERS` - Distinct users with errors
- `USER_CATEGORY_EXCELLENT` - Excellent user category count
- `USER_CATEGORY_GOOD` - Good user category count
- `USER_CATEGORY_AVERAGE` - Average user category count
- `USER_CATEGORY_POOR` - Poor user category count
- `NET_0` - Connection error count
- `NET_2XX` - 2XX status code count
- `NET_3XX` - 3XX status code count
- `NET_4XX` - 4XX status code count
- `NET_5XX` - 5XX status code count
- `NET_COUNT` - Total network request count
- `CRASH_RATE` - Crash rate percentage
- `ANR_RATE` - ANR rate percentage
- `CRASH_FREE_USERS_PERCENTAGE` - Crash-free users percentage
- `CRASH_FREE_SESSIONS_PERCENTAGE` - Crash-free sessions percentage
- `CRASH_USERS` - Users with crashes
- `CRASH_SESSIONS` - Sessions with crashes
- `ALL_USERS` - Total users count
- `ALL_SESSIONS` - Total sessions count
- `ANR_FREE_USERS_PERCENTAGE` - ANR-free users percentage
- `ANR_FREE_SESSIONS_PERCENTAGE` - ANR-free sessions percentage
- `ANR_USERS` - Users with ANRs
- `ANR_SESSIONS` - Sessions with ANRs
- `NON_FATAL_FREE_USERS_PERCENTAGE` - Non-fatal free users percentage
- `NON_FATAL_FREE_SESSIONS_PERCENTAGE` - Non-fatal free sessions percentage
- `NON_FATAL_USERS` - Users with non-fatal errors
- `NON_FATAL_SESSIONS` - Sessions with non-fatal errors
- `FROZEN_FRAME_RATE` - Frozen frame rate
- `ERROR_RATE` - Error rate
- `POOR_USER_RATE` - Poor user rate
- `AVERAGE_USER_RATE` - Average user rate
- `GOOD_USER_RATE` - Good user rate
- `EXCELLENT_USER_RATE` - Excellent user rate
- `LOAD_TIME` - Load time metric
- `SCREEN_TIME` - Screen time metric
- `SCREEN_DAILY_USERS` - Screen daily users
- `NET_4XX_RATE` - 4XX error rate
- `NET_5XX_RATE` - 5XX error rate
- `NET_0_BY_PULSE_TYPE` - Connection errors by pulse type
- `NET_2XX_BY_PULSE_TYPE` - 2XX by pulse type
- `NET_3XX_BY_PULSE_TYPE` - 3XX by pulse type
- `NET_4XX_BY_PULSE_TYPE` - 4XX by pulse type
- `NET_5XX_BY_PULSE_TYPE` - 5XX by pulse type
- `NET_COUNT_BY_PULSE_TYPE` - Network count by pulse type
- `ARR_TO_STR` - Array to string conversion

**⚠️ Question**: Frontend TypeScript shows `DataType = "TRACES" | "EVENTS" | "METRICS" | "LOGS" | "EXCEPTIONS"` but backend Java shows `"TRACES" | "LOGS" | "METRICS" | "EXCEPTIONS"`. Which is correct? Is "EVENTS" a valid data type?

**Response**:
```typescript
{
  fields: string[],      // Column names
  rows: string[][]       // Row data (all values as strings)
}
```

### Universal Query Request

**Endpoint**: `POST /api/v1/query`

**Request Body**:
```typescript
{
  queryString: string,  // SQL query string
  timestamp?: string    // Optional timestamp filter
}
```

**Response**:
```typescript
{
  jobId: string,
  status: "COMPLETED" | "RUNNING" | "FAILED" | "CANCELLED",
  message: string,
  queryExecutionId?: string,
  resultLocation?: string,
  resultData?: Array<Record<string, any>>,
  nextToken?: string,
  dataScannedInBytes?: number,
  createdAt: number,
  completedAt?: number
}
```

---

## Frontend Data Structures

### Session Timeline Event

```typescript
interface SessionTimelineEvent {
  id: string;
  name: string;
  type: "crash" | "anr" | "frozen_frame" | "trace" | "span" | "log";
  timestamp: number;  // milliseconds from session start
  absoluteTimestamp?: number;  // absolute timestamp (Unix epoch ms)
  duration?: number;  // milliseconds
  children?: SessionTimelineEvent[];  // nested events
  attributes?: Record<string, any>;
}
```

### User Engagement Data

```typescript
interface UserEngagementData {
  dailyUsers: number | null;
  weeklyUsers: number | null;
  monthlyUsers: number | null;
  trendData: Array<{
    timestamp: number;  // Unix milliseconds
    dau: number;
  }>;
  hasData: boolean;
}
```

### Session Data

```typescript
interface Session {
  SessionId: string;
  DeviceModel: string;
  UserId: string;
  duration: number;  // milliseconds
  hasAnr: boolean;
  hasCrash: boolean;
  hasNetwork: boolean;
  hasFrozen: boolean;
  Timestamp: string;  // ISO 8601
}
```

### Problematic Interaction Data

```typescript
interface ProblematicInteractionData {
  trace_id: string;
  id: string;
  user_id: string;
  phone_number: string;
  device: string;
  os_version: string;
  start_time: string;
  duration_ms: number;
  event_count: number;
  screen_count: number;
  event_type: "crash" | "anr" | "networkError" | "frozenFrame" | "nonFatal" | "completed";
  event_names?: string;
  interaction_name: string;
  screens_visited: string;
}
```

---

## PulseType Constants

**Location**: `pulse-ui/src/constants/PulseOtelSemcov.ts`

```typescript
enum PulseType {
  INTERACTION = "interaction",
  SCREEN_SESSION = "screen_session",
  SCREEN_LOAD = "screen_load",
  SCREEN_INTERACTIVE = "screen_interactive",  // ⚠️ Used in production but not in frontend constants
  NAVIGATION = "navigation",
  APP_START = "app_start",
  SCREEN_NAME = "screen.name",
  SESSION_START = "session.start"
}
```

**⚠️ Note**: `screen_interactive` is used in production data but is **not** in the frontend constants file (`pulse-ui/src/constants/PulseOtelSemcov.ts`).

**Usage**:
- Filter telemetry data by event type
- Group related events
- Identify event categories in queries

---

## Column Name Conventions

**Location**: `pulse-ui/src/constants/PulseOtelSemcov.ts`

```typescript
enum COLUMN_NAME {
  EXCEPTION_TYPE = "ExceptionType",
  DEVICE_MODEL = "DeviceModel",
  NETWORK_PROVIDER = "NetworkProvider",
  OS_VERSION = "OsVersion",
  PLATFORM = "Platform",
  STATE = "GeoState",
  COUNTRY = "GeoCountry",
  APP_VERSION = "AppVersion",
  APP_VERSION_CODE = "AppVersionCode",
  DURATION = "Duration",
  USER_ID = "UserId",
  TIMESTAMP = "Timestamp",
  SPAN_ID = "SpanId",
  TRACE_ID = "TraceId",
  SESSION_ID = "SessionId",
  PULSE_TYPE = "PulseType",
  SPAN_NAME = "SpanName",
  DEVICE_MANUFACTURER = "device.manufacturer",
  OS_TYPE = "os.type",
  OS_DESCRIPTION = "os.description",
  FROZEN_FRAME_COUNT = "app.interaction.frozen_frame_count",
  IS_ERROR = "isError",
  EVENTS_NAME = "Events.Name",
  EVENTS_TIMESTAMP = "Events.Timestamp"
}
```

**Naming Conventions**:
- **PascalCase**: Materialized columns (e.g., `DeviceModel`, `UserId`)
- **camelCase**: Attribute keys (e.g., `device.model.name`, `user.id`)
- **UPPER_SNAKE_CASE**: Constants and enums
- **dot.notation**: Nested attribute paths (e.g., `SpanAttributes['screen.name']`)

---

## Data Flow

### 1. Data Ingestion (Telemetry) - Confirmed from otel-collector configs

**Actual Flow** (from `backend/ingestion/otel-collector-*.yaml`):
```
Mobile App → OTEL Collector 1 (OTLP Receiver) → Kafka → OTEL Collector 2 (Kafka Consumer) → ClickHouse
```

**Details**:
- **OTEL Collector 1**: Receives OTLP (HTTP/gRPC on port 4318), routes to Kafka topics
  - Traces → `pulse.traces` topic
  - Metrics → `pulse.metrics` topic
  - Logs (ANR/Crash/Non-fatal) → `pulse.logs.anr_crash` topic
  - Logs (other) → `pulse.logs.other` topic
- **OTEL Collector 2**: Consumes from Kafka, writes to ClickHouse
  - Writes to `otel_traces`, `otel_logs`, `otel_metrics_gauge` tables
- **Stack Trace Events**: Processed separately (see `AnrCrashLogConsumerVerticle`) → `stack_trace_events` table

### 2. Data Ingestion (Custom Events / Click Stream) - Confirmed

**Actual Flow** (from `deploy/terraform/vector/main.tf`):
```
Mobile App → Vector (OTLP Receiver on port 4318) → Transform/Filter Custom Events → S3 Bucket (puls-otel-config)
```

**Details**:
- **Vector**: Receives OTLP data directly from mobile apps on port 4318 (HTTP/gRPC)
- **Filtering**: Vector configuration (in AMI) filters for custom events (clickstream events)
- **Transformation**: Transforms OTEL format to flattened schema matching `otel_data` table
- **Storage**: Writes to S3 bucket `puls-otel-config` in Parquet format
- **Partitioning**: Data partitioned by `year/month/day/hour` based on event timestamp
- **Load Balancer**: NLB with TLS termination routes traffic to Vector instances
- **IAM**: Instance profile provides S3 write permissions

**Custom Events**:
- Custom events are tracked with `pulse.type = "custom_event"` (from SDK)
- Mobile apps send custom events to Vector's OTLP endpoint
- Vector processes and stores these events in S3 for long-term analytics

### 3. Data Query (ClickHouse)
```
Frontend → Backend API → ClickHouse Query → Response → Frontend
```

### 4. Data Query (S3/Athena)
```
Frontend → Backend API → AWS Athena → S3 Data → Response → Frontend
```

### 5. Metadata Management
```
Frontend → Backend API → MySQL (interactions, alerts, configs) → Response → Frontend
```

### 6. Universal Querying
```
Frontend → Backend API → AWS Athena → S3/ClickHouse Results → Backend → Frontend
```

---

## Common Query Patterns

**⚠️ Note**: These are example query patterns. Actual queries may vary based on requirements.

### Get User Sessions
```sql
SELECT SessionId, UserId, Timestamp
FROM otel.otel_logs
WHERE PulseType = 'session.start'
  AND Timestamp >= '2024-01-15 00:00:00'
  AND Timestamp <= '2024-01-15 23:59:59'
  AND UserId = 'user-123'
```

**⚠️ Question**: Is there a `Duration` field in `otel_logs` for sessions, or is duration calculated differently?

### Get Screen Sessions
```sql
SELECT SessionId, SpanAttributes['screen.name'] as ScreenName, Duration
FROM otel.otel_traces
WHERE PulseType = 'screen_session'
  AND Timestamp >= '2024-01-15 00:00:00'
  AND Timestamp <= '2024-01-15 23:59:59'
```

### Get Crashes
```sql
SELECT GroupId, ExceptionType, COUNT(*) as Count
FROM otel.stack_trace_events
WHERE EventName = 'crash'
  AND Timestamp >= '2024-01-15 00:00:00'
  AND Timestamp <= '2024-01-15 23:59:59'
GROUP BY GroupId, ExceptionType
```

**⚠️ Question**: Are these query patterns accurate? Should I verify with actual query examples from the codebase?

---

## Data Retention

**Retention Policy**: Maximum of **1 month** of data is available in the system.

**Storage-Specific Retention**:
- **ClickHouse**: Partitioned by date (`toYYYYMMDD(Timestamp)`), data retained for up to 1 month
- **MySQL**: Application metadata retained for up to 1 month
- **S3 (Custom Events)**: Custom events data retained for up to 1 month
- **S3 (Athena results)**: Query results retained per AWS S3 lifecycle policies (typically shorter than 1 month)

**Note**: 
- No TTL configured in ClickHouse schema - retention managed externally (likely via partition deletion)
- Old partitions are automatically cleaned up after 1 month
- Historical analysis beyond 1 month requires data archival or external storage

---

## Notes

1. **All timestamps in ClickHouse are UTC** - Convert to user timezone in frontend ✅ (confirmed from schema)
2. **All attribute values are strings** - Type conversion happens in application layer ✅ (confirmed from schema: `Map(LowCardinality(String), String)`)
3. **LowCardinality optimization** - Used for frequently repeated string values ✅ (confirmed from schema)
4. **Materialized columns** - Computed from attributes for faster queries ✅ (confirmed from schema)
5. **Partitioning** - Daily partitions for efficient time-range queries ✅ (confirmed: `PARTITION BY toYYYYMMDD(Timestamp)`)
6. **Compression** - ZSTD compression used for large text fields (stack traces) ✅ (confirmed from schema: `CODEC(ZSTD(12))`)
7. **S3 click stream data** - ⚠️ Assumed - needs actual implementation details (see S3 section)
8. **Vector batching** - ⚠️ Assumed - needs actual Vector configuration
9. **S3 data format** - ⚠️ Assumed - needs actual Vector configuration
10. **Athena integration** - ✅ Confirmed (backend uses Athena for universal querying)

## Clarifying Questions

To complete this documentation accurately, please provide answers to:

### ClickHouse Questions
1. ✅ Schema confirmed from `clickhouse-otel-schema.sql`
2. ✅ Materialized columns confirmed from schema
3. ⚠️ **What is the actual data retention period?** Is there a TTL or partition deletion policy?
4. ⚠️ **Are the example data structures accurate?** Should I verify with actual sample data?

### S3/Vector Questions (See S3 section above)
1. Vector configuration file location
2. S3 bucket name and path structure
3. Event schema and format
4. Athena table schema
5. Retention policies

### MySQL Questions
1. ✅ Schema confirmed from `mysql-init.sql`
2. ⚠️ **Is the interaction `details` JSON structure accurate?** Are there additional fields?
3. ⚠️ **Is the SDK config `config_json` structure accurate?** Are there additional fields?
4. ⚠️ **What is the data retention policy?** Is data archived or deleted?

### API Questions
1. ✅ Functions list confirmed from `Functions.java`
2. ✅ Operators confirmed from frontend TypeScript
3. ⚠️ **DataType discrepancy**: Frontend has `"EVENTS"` but backend has `"EXCEPTIONS"`. Which is correct?
4. ⚠️ **Are all operators supported?** (EQ, IN, NE, GT, LT, GTE, LTE, LIKE, ADDITIONAL)

### Data Flow Questions
1. ✅ Telemetry flow confirmed from otel-collector configs
2. ⚠️ **How does Vector fit into the data flow?** Does it receive data directly or from Kafka?
3. ⚠️ **What data goes to Vector vs OTEL Collector?** Is it all data or filtered subset?

### Frontend Data Structures
1. ✅ Confirmed from actual TypeScript interfaces
2. ⚠️ **Are there other important data structures not documented?**

---

## References

- ClickHouse Schema: `backend/ingestion/clickhouse-otel-schema.sql`
- MySQL Schema: `deploy/db/mysql-init.sql`
- Frontend Constants: `pulse-ui/src/constants/PulseOtelSemcov.ts`
- API Documentation: `backend/server/README.md`
- OTEL Collector Configs: `backend/ingestion/otel-collector-*.yaml`
- Vector Infrastructure: `deploy/terraform/vector/`
- Vector Configuration: `vector-configuration.yaml` (this directory)
- Data Verification: `DATA_VERIFICATION.md` (this directory) - Verification against actual ClickHouse traces data
- Stack Trace Verification: `STACK_TRACE_VERIFICATION.md` (this directory) - Verification against actual ClickHouse stack trace data
- Logs Verification: `LOGS_VERIFICATION.md` (this directory) - Verification against actual ClickHouse logs data
- Complete Understanding Summary: `DATA_UNDERSTANDING_SUMMARY.md` (this directory) - Complete overview of data architecture

---

## Summary of Assumptions & Clarifying Questions

### ✅ Confirmed (from actual code/schema)
- ClickHouse table schemas and structures
- MySQL table schemas
- Materialized columns and their sources
- Partitioning strategy
- Compression settings
- Frontend TypeScript interfaces
- PulseType constants
- Column name constants
- Available query functions (from `Functions.java`)
- Query operators (from frontend TypeScript)
- Data flow for telemetry (OTEL Collector → Kafka → ClickHouse)

### ⚠️ Needs Confirmation

1. **S3/Vector** (see S3 section for detailed questions)
   - Vector configuration
   - S3 bucket name and path structure
   - Event schema and format
   - Athena table schema
   - Retention policies

2. **Data Retention**
   - ClickHouse retention period and TTL policies
   - MySQL retention/archival policies
   - S3 lifecycle policies

3. **Data Flow**
   - How Vector fits into the data flow
   - What data goes to Vector vs OTEL Collector

4. **API Details**
   - DataType discrepancy: "EVENTS" vs "EXCEPTIONS"
   - Complete operator support list

5. **Data Structures**
   - Interaction `details` JSON - complete structure?
   - SDK config `config_json` - complete structure?
   - Are example data structures accurate?

6. **PulseType Values**
   - Complete list of all PulseType values used
   - Difference between `device.crash` and `crash`

7. **Metrics**
   - Are only gauge metrics stored, or other types too?

8. **Query Patterns**
   - Are the example queries accurate?
   - Should I verify with actual query examples?

Please review the questions marked with ⚠️ throughout this document and provide the actual implementation details.

