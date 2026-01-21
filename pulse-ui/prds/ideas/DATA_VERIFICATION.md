# Data Verification from ClickHouse Sample

This document verifies the data documentation against actual ClickHouse trace data from `traces.csv`.

## ✅ Verified Findings

### 1. PulseType Values (From Actual Data)

**Found in traces.csv**:
- ✅ `app_start` - App startup events (lines 7, 8, 9)
- ✅ `screen_interactive` - Screen interactive events (lines 10, 11, 12, 13, 14)
- ✅ `screen_load` - Screen load events (lines 20, 21, 22, 23, 24)
- ✅ `screen_session` - Screen session events (lines 50, 51, 52, 53, 54)

**Not found in this sample** (but documented in SDK):
- `interaction` - Critical user interactions
- `navigation` - Navigation events
- `device.crash` - Crashes
- `device.anr` - ANRs
- `custom_event` - Custom events

**Note**: `screen_interactive` is **NOT** in the frontend constants file but **IS** used in production data!

### 2. ResourceAttributes Structure

**✅ Verified**: Structure matches documentation

**Actual format**: Python dict-like string with single quotes
```
{'android.os.api_level':'36','app.build_id':'10960287','app.build_name':'8.3.0_10960287',...}
```

**Fields found**:
- ✅ `android.os.api_level`
- ✅ `app.build_id`
- ✅ `app.build_name`
- ✅ `device.manufacturer`
- ✅ `device.model.identifier`
- ✅ `device.model.name`
- ✅ `os.description`
- ✅ `os.name`
- ✅ `os.type`
- ✅ `os.version`
- ✅ `rum.sdk.version`
- ✅ `service.name`
- ✅ `service.version`
- ✅ `telemetry.sdk.language`
- ✅ `telemetry.sdk.name`
- ✅ `telemetry.sdk.version`

**Note**: ClickHouse stores these as Map, but CSV export shows Python dict format.

### 3. SpanAttributes Structure

**✅ Verified**: Structure matches documentation

**Actual format**: Python dict-like string with single quotes

**Fields found**:
- ✅ `activity.name` - Activity name (e.g., 'MainActivity')
- ✅ `app.installation.id` - Installation ID
- ✅ `app.interaction.analysed_frame_count` - Frame analysis count
- ✅ `app.interaction.frozen_frame_count` - Frozen frame count
- ✅ `app.interaction.slow_frame_count` - Slow frame count
- ✅ `app.interaction.unanalysed_frame_count` - Unanalysed frame count
- ✅ `globalAttr.boolean` - Global boolean attribute
- ✅ `globalAttr.number` - Global number attribute
- ✅ `globalAttr.string` - Global string attribute
- ✅ `last.screen.name` - Last screen name
- ✅ `network.carrier.icc` - Network carrier ICC code
- ✅ `network.carrier.mcc` - Network carrier MCC code
- ✅ `network.carrier.mnc` - Network carrier MNC code
- ✅ `network.carrier.name` - Network carrier name
- ✅ `network.connection.type` - Connection type (wifi, unavailable)
- ✅ `pulse.type` - Pulse type (app_start, screen_interactive, screen_load, screen_session)
- ✅ `screen.name` - Screen name (e.g., 'MainActivity', 'com.fc.home')
- ✅ `session.id` - Session identifier
- ✅ `user.id` - User identifier
- ✅ `start.type` - Start type (cold, hot)
- ✅ `routeKey` - Route key for React Native navigation
- ✅ `routeHasBeenSeen` - Whether route has been seen (true/false)
- ✅ `phase` - Navigation phase (start)
- ✅ `platform` - Platform (android)
- ✅ `pulse.user.email` - User email (when available)
- ✅ `pulse.user.mobileNo` - User mobile number (when available)
- ✅ `pulse.user.name` - User name (when available)

### 4. SpanName Values

**Found in actual data**:
- ✅ `AppStart` - App startup
- ✅ `Paused` - Activity paused
- ✅ `Resumed` - Activity resumed
- ✅ `Stopped` - Activity stopped
- ✅ `ScreenInteractive` - Screen became interactive
- ✅ `Created` - Activity/screen created
- ✅ `Navigated` - Navigation event
- ✅ `ActivitySession` - Activity session
- ✅ `ScreenSession` - Screen session

### 5. Events Structure

**✅ Verified**: Events are stored as arrays

**Events.Timestamp**: Array of DateTime64 timestamps
```
['2026-01-19 11:19:29.640562419','2026-01-19 11:19:29.641205044',...]
```

**Events.Name**: Array of event names
```
['activityPreStarted','activityStarted','activityPostStarted',...]
```

**Events.Attributes**: Array of Maps (mostly empty in sample)
```
[{},{},{},...]
```

**Event Names found**:
- `activityPreStarted`, `activityStarted`, `activityPostStarted`
- `activityPrePaused`, `activityPaused`, `activityPostPaused`
- `activityPreResumed`, `activityResumed`, `activityPostResumed`
- `activityPreStopped`, `activityStopped`, `activityPostStopped`
- `activityPreCreated`, `activityCreated`, `activityPostCreated`

### 6. Duration

**✅ Verified**: Duration is in nanoseconds (Int64)
- Example: `29605625` = 29.6 milliseconds
- Example: `176682917` = 176.7 milliseconds
- Example: `1287758792` = 1.29 seconds

### 7. StatusCode

**✅ Verified**: StatusCode values
- `Unset` - Most common in sample

### 8. ParentSpanId

**✅ Verified**: 
- Can be empty (16 null bytes: `                `)
- Or contain actual span ID (e.g., `6cd98be93c0b99a4`)

### 9. TraceState

**✅ Verified**: 
- Mostly empty strings in sample
- Can contain trace state information

### 10. Links

**✅ Verified**: All Links arrays are empty in sample
- `Links.TraceId`: `[]`
- `Links.SpanId`: `[]`
- `Links.TraceState`: `[]`
- `Links.Attributes`: `[]`

## ⚠️ Corrections Needed

### 1. Missing PulseType: `screen_interactive`

**Issue**: `screen_interactive` is used in production but **NOT** in frontend constants file.

**Found in data**: Lines 10-19 show `pulse.type='screen_interactive'` for `ScreenInteractive` spans.

**Action**: Add to documentation and frontend constants.

### 2. Additional SpanAttributes Fields

**Found but not fully documented**:
- `routeKey` - React Native route key
- `routeHasBeenSeen` - Boolean for route visibility
- `phase` - Navigation phase
- `pulse.user.email` - User email
- `pulse.user.mobileNo` - User mobile number
- `pulse.user.name` - User name
- `last.screen.name` - Previous screen name

### 3. ResourceAttributes Format

**Note**: In ClickHouse, ResourceAttributes is stored as `Map(LowCardinality(String), String)`, but CSV export shows Python dict format with single quotes. This is just a representation difference.

### 4. SpanAttributes Format

**Note**: Same as ResourceAttributes - stored as Map in ClickHouse, shown as Python dict in CSV.

## ✅ Confirmed Accurate

1. ✅ Column names match schema
2. ✅ Data types match (Duration as Int64 nanoseconds, Timestamp as DateTime64)
3. ✅ Materialized columns work correctly (PulseType from SpanAttributes['pulse.type'])
4. ✅ Events structure (arrays of timestamps, names, attributes)
5. ✅ ResourceAttributes fields match documentation
6. ✅ Core SpanAttributes fields match documentation
7. ✅ Duration values are in nanoseconds
8. ✅ StatusCode values (Unset)

## Summary

The documentation is **largely accurate**, with these additions:
1. **`screen_interactive`** PulseType is used in production (not in frontend constants)
2. **Additional SpanAttributes** fields for React Native navigation and user info
3. **Format note**: Maps are stored as Map type in ClickHouse but exported as Python dict strings in CSV

