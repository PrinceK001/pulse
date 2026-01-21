# Logs Table Verification from ClickHouse Sample

This document verifies the `otel_logs` table documentation against actual ClickHouse data from `logs_clickhouse_table.csv` (excluding custom_event entries which are stored in S3).

## ✅ Verified Findings

### 1. PulseType Values (From Actual Data, Excluding custom_event)

**Found in logs.csv**:
- ✅ `session.start` - Session start event (4 entries)
- ✅ `pulse.user.session.start` - User session start (1 entry)
- ✅ `device.app.lifecycle` - App lifecycle events (6 entries)
- ✅ `network.change` - Network change events (6 entries)
- ✅ `app.jank.slow` - Slow frame events (2 entries)
- ✅ `rum.sdk.init.started` - SDK initialization started (4 entries)
- ✅ `rum.sdk.init.net.provider` - SDK network provider initialization (4 entries)
- ✅ `rum.sdk.init.span.exporter` - SDK span exporter initialization (4 entries)

**Not found in this sample** (but documented in SDK):
- `session.end` - Session end event
- `device.anr` - Application Not Responding (likely in stack_trace_events)
- `device.crash` - Application crash (likely in stack_trace_events)
- `non_fatal` - Non-fatal errors (likely in stack_trace_events)

**Note**: `custom_event` entries are **NOT stored in ClickHouse** - they are stored in S3 via Vector.

### 2. Data Structure Observations

#### Body
- **Finding**: Can be **empty string** (most entries in sample)
- **Note**: Event information is primarily in LogAttributes, not Body

#### EventName
- **Finding**: Can be **empty string** (most entries in sample)
- **Note**: Event type is indicated by `pulse.type` in LogAttributes

#### SeverityText
- **Finding**: Can be **empty string** (most entries in sample)
- **Note**: Not always populated for Pulse-specific events

#### SeverityNumber
- **Finding**: Can be **0** (most entries in sample)
- **Note**: Not always populated for Pulse-specific events

#### TraceId and SpanId
- **Finding**: Can be **empty** (16 null bytes for SpanId)
- **Note**: Not all log events are associated with traces/spans
- **Example**: `session.start` events have empty TraceId and SpanId

#### ScopeName
- **Finding**: Varies by event type
- **Examples**:
  - `otel.session` - Session events
  - `otel.initialization.events` - SDK initialization events
  - `io.opentelemetry.lifecycle` - Lifecycle events
  - `io.opentelemetry.network` - Network events
  - `app.jank` - Jank/slow frame events
  - `com.pulse.android.sdk` - Pulse SDK events

### 3. LogAttributes Structure

**✅ Verified**: LogAttributes contains event-specific information.

**Common fields found** (varies by PulseType):
- ✅ `pulse.type` - Pulse event type
- ✅ `session.id` - Session identifier
- ✅ `user.id` - User identifier (can be empty)
- ✅ `screen.name` - Screen name (can be 'unknown')
- ✅ `app.installation.id` - App installation ID
- ✅ `network.connection.type` - Connection type (wifi, unavailable)
- ✅ `network.carrier.name` - Network carrier name
- ✅ `network.carrier.mcc` - Network carrier MCC code
- ✅ `network.carrier.mnc` - Network carrier MNC code
- ✅ `network.carrier.icc` - Network carrier ICC code
- ✅ `network.status` - Network status (for network.change events)
- ✅ `globalAttr.string` - Global string attribute
- ✅ `globalAttr.number` - Global number attribute
- ✅ `globalAttr.boolean` - Global boolean attribute

**Event-specific fields**:
- **app.jank.slow**: `app.jank.frame_count`, `app.jank.threshold`, `app.jank.period`
- **device.app.lifecycle**: `android.app.state`
- **rum.sdk.init.span.exporter**: `span.exporter`

### 4. ResourceAttributes Structure

**✅ Verified**: ResourceAttributes matches the structure from traces table.

**Fields found**:
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

### 5. ScopeAttributes

**✅ Verified**: ScopeAttributes can be empty.

**Finding**: In sample data, ScopeAttributes is an empty object `{}` for most entries.

## ⚠️ Corrections to Documentation

### 1. Body Can Be Empty

**Issue**: Documentation suggests Body contains log message.

**Correction**: Body can be empty string for Pulse-specific events. Event information is primarily in LogAttributes.

### 2. EventName Can Be Empty

**Issue**: Documentation suggests EventName is always populated.

**Correction**: EventName can be empty string. Event type is indicated by `pulse.type` in LogAttributes.

### 3. SeverityText and SeverityNumber Can Be Empty/Zero

**Issue**: Documentation suggests these are always populated.

**Correction**: Both can be empty/zero for Pulse-specific events.

### 4. TraceId and SpanId Can Be Empty

**Issue**: Documentation suggests these are always associated with traces.

**Correction**: Both can be empty if event is not associated with a trace/span (e.g., `session.start` events).

### 5. Additional PulseType Values

**Issue**: Documentation doesn't list all PulseType values found in logs.

**Correction**: Add the following PulseType values:
- `pulse.user.session.start` - User session start
- `device.app.lifecycle` - App lifecycle events
- `app.jank.slow` - Slow frame events
- `rum.sdk.init.started` - SDK initialization started
- `rum.sdk.init.net.provider` - SDK network provider initialization
- `rum.sdk.init.span.exporter` - SDK span exporter initialization

### 6. ScopeName Values

**Issue**: Documentation doesn't list ScopeName values.

**Correction**: Add common ScopeName values:
- `otel.session` - Session events
- `otel.initialization.events` - SDK initialization events
- `io.opentelemetry.lifecycle` - Lifecycle events
- `io.opentelemetry.network` - Network events
- `app.jank` - Jank/slow frame events
- `com.pulse.android.sdk` - Pulse SDK events

### 7. Event-Specific LogAttributes

**Issue**: Documentation doesn't mention event-specific attributes.

**Correction**: Add event-specific LogAttributes:
- **app.jank.slow**: `app.jank.frame_count`, `app.jank.threshold`, `app.jank.period`
- **device.app.lifecycle**: `android.app.state`
- **network.change**: `network.status`
- **rum.sdk.init.span.exporter**: `span.exporter`

## ✅ Confirmed Accurate

1. ✅ Column names match schema
2. ✅ Data types match
3. ✅ LogAttributes and ResourceAttributes structure matches traces table
4. ✅ Materialized columns work correctly (PulseType from LogAttributes['pulse.type'])
5. ✅ Partitioning and ordering strategy
6. ✅ ResourceAttributes fields match traces table

## Summary

The documentation is **largely accurate**, with these additions:
1. **Body, EventName, SeverityText, SeverityNumber can be empty/zero** for Pulse-specific events
2. **TraceId and SpanId can be empty** (events may not be associated with traces)
3. **Additional PulseType values** for SDK initialization and app lifecycle events
4. **ScopeName values** vary by event type
5. **Event-specific LogAttributes** for different PulseType values
6. **custom_event entries are NOT in ClickHouse** - they are stored in S3

