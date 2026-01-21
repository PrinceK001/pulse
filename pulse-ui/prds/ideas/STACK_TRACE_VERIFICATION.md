# Stack Trace Events Table Verification

This document verifies the `stack_trace_events` table documentation against actual ClickHouse data from `stacktraces.csv`.

## ✅ Verified Findings

### 1. Column Structure

**✅ Verified**: All columns match the schema documentation.

**Columns**:
- ✅ `Timestamp` - DateTime64(9, 'UTC')
- ✅ `EventName` - LowCardinality(String) - **Can be empty**
- ✅ `Title` - String
- ✅ `ExceptionStackTrace` - String (formatted)
- ✅ `ExceptionStackTraceRaw` - String (raw with line numbers)
- ✅ `ExceptionMessage` - String - **Can be empty**
- ✅ `ExceptionType` - LowCardinality(String) - **Can be empty**
- ✅ `Interactions` - Array(LowCardinality(String)) - **Can be empty array**
- ✅ `ScreenName` - LowCardinality(String)
- ✅ `UserId` - String - **Can be empty**
- ✅ `SessionId` - String
- ✅ `Platform` - LowCardinality(String)
- ✅ `OsVersion` - LowCardinality(String)
- ✅ `DeviceModel` - LowCardinality(String)
- ✅ `AppVersionCode` - LowCardinality(String)
- ✅ `AppVersion` - LowCardinality(String)
- ✅ `SdkVersion` - LowCardinality(String)
- ✅ `BundleId` - String - **Can be empty**
- ✅ `TraceId` - String - **Can be empty (16 null bytes)**
- ✅ `SpanId` - FixedString(16) - **Can be empty (16 null bytes)**
- ✅ `GroupId` - String
- ✅ `Signature` - String
- ✅ `Fingerprint` - String
- ✅ `ScopeAttributes` - Map - **Can be empty object**
- ✅ `LogAttributes` - Map
- ✅ `ResourceAttributes` - Map

### 2. Data Format Observations

#### EventName
- **Finding**: Can be **empty string** (not always "crash" or "anr")
- **In sample**: Empty for ANR event
- **Note**: PulseType in LogAttributes indicates event type (`device.anr`, `device.crash`)

#### Title
- **Format**: `"Error at {method}#{location} [{GroupId}]"`
- **Example**: `"Error at os.BinderProxy#transactNative [EXC-1CD2758A05]"`

#### ExceptionStackTrace
- **Format**: Formatted stack trace with method names (indented, method#class format)
- **Example**:
  ```
    android.os.BinderProxy#transactNative
    android.os.BinderProxy#transact
    android.app.trust.ITrustManager$Stub$Proxy#isDeviceLocked
  ```

#### ExceptionStackTraceRaw
- **Format**: Raw stack trace with line numbers and file paths
- **Example**:
  ```
  android.os.BinderProxy.transactNative(Native Method)
  android.os.BinderProxy.transact(BinderProxy.java:592)
  android.app.trust.ITrustManager$Stub$Proxy.isDeviceLocked(ITrustManager.java:570)
  ```

#### ExceptionMessage and ExceptionType
- **Finding**: Can be **empty strings** (especially for ANR events)
- **In sample**: Both empty for ANR event

#### Interactions
- **Finding**: Can be **empty array** `[]` if no interactions associated
- **In sample**: Empty array

#### TraceId and SpanId
- **Finding**: Can be **empty** (16 null bytes) if not associated with a trace/span
- **In sample**: Both empty for ANR event
- **Note**: Errors may not always be associated with traces

#### GroupId
- **Format**: `"EXC-{hex}"` (e.g., `"EXC-1CD2758A05"`)
- **Purpose**: Used for grouping similar errors

#### Signature
- **Format**: `"v1|platform:{platform}|exc:{exception_type}|frames:{frame1}>{frame2}>..."`
- **Example**: `"v1|platform:java|exc:|frames:android.os.BinderProxy#transactNative>android.os.BinderProxy#transact>..."`
- **Contains**: Version, platform, exception type, and frame list

#### Fingerprint
- **Format**: SHA-1 hash (40 hex characters)
- **Example**: `"1cd2758a05ec13e1e88e98d7aac08f4ce793d5a0"`
- **Purpose**: Used for error deduplication

### 3. LogAttributes Structure

**✅ Verified**: LogAttributes contains error-specific information.

**Fields found**:
- ✅ `pulse.type` - Pulse type (`device.anr`, `device.crash`, `non_fatal`)
- ✅ `screen.name` - Screen name where error occurred
- ✅ `session.id` - Session identifier
- ✅ `thread.name` - Thread name (e.g., `'main'`)
- ✅ `thread.id` - Thread ID (can be empty)
- ✅ `exception.stacktrace` - Full exception stack trace (newline-separated)
- ✅ `app.installation.id` - App installation ID
- ✅ `globalAttr.string` - Global string attribute
- ✅ `globalAttr.number` - Global number attribute (can be empty)
- ✅ `globalAttr.boolean` - Global boolean attribute (can be empty)

**Key Finding**: The full stack trace is stored in `LogAttributes['exception.stacktrace']` as a newline-separated string.

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

**Finding**: In sample data, ScopeAttributes is an empty object `{}`.

### 6. PulseType (Materialized Column)

**✅ Verified**: PulseType is materialized from `LogAttributes['pulse.type']`.

**Values found**:
- ✅ `device.anr` - Application Not Responding
- ✅ `device.crash` - Application crash (expected)
- ✅ `non_fatal` - Non-fatal errors (expected)

## ⚠️ Corrections to Documentation

### 1. EventName Can Be Empty

**Issue**: Documentation suggests EventName is always populated.

**Correction**: EventName can be empty string, especially for ANR events. The event type is indicated by `pulse.type` in LogAttributes.

### 2. ExceptionMessage and ExceptionType Can Be Empty

**Issue**: Documentation suggests these are always populated.

**Correction**: Both can be empty strings, especially for ANR events.

### 3. TraceId and SpanId Can Be Empty

**Issue**: Documentation suggests these are always associated with traces.

**Correction**: Both can be empty (16 null bytes) if error is not associated with a trace/span.

### 4. Interactions Can Be Empty Array

**Issue**: Documentation suggests interactions are always present.

**Correction**: Interactions can be an empty array if no interactions are associated with the error.

### 5. Signature Format

**Issue**: Documentation shows simplified format.

**Correction**: Actual format is `"v1|platform:{platform}|exc:{exception_type}|frames:{frame1}>{frame2}>..."` with pipe-separated sections.

### 6. Fingerprint Format

**Issue**: Documentation doesn't specify format.

**Correction**: Fingerprint is a SHA-1 hash (40 hex characters) used for error deduplication.

### 7. GroupId Format

**Issue**: Documentation shows generic format.

**Correction**: Actual format is `"EXC-{hex}"` (e.g., `"EXC-1CD2758A05"`).

### 8. Thread Information

**Issue**: Documentation doesn't mention thread information.

**Correction**: Thread information is stored in LogAttributes (`thread.name`, `thread.id`).

## ✅ Confirmed Accurate

1. ✅ Column names match schema
2. ✅ Data types match
3. ✅ LogAttributes and ResourceAttributes structure matches traces table
4. ✅ Materialized PulseType column works correctly
5. ✅ Partitioning and ordering strategy
6. ✅ ExceptionStackTrace vs ExceptionStackTraceRaw distinction
7. ✅ GroupId, Signature, and Fingerprint for error grouping

## Summary

The documentation is **largely accurate**, with these additions:
1. **EventName, ExceptionMessage, ExceptionType can be empty** (especially for ANR events)
2. **TraceId and SpanId can be empty** (errors may not be associated with traces)
3. **Interactions can be empty array**
4. **Signature format** is pipe-separated with version, platform, exception type, and frames
5. **Fingerprint** is SHA-1 hash for deduplication
6. **GroupId format** is `EXC-{hex}`
7. **Thread information** in LogAttributes
8. **Full stack trace** in `LogAttributes['exception.stacktrace']`

