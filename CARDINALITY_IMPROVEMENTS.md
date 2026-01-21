# Cardinality Improvements for Churn Analytics

## Problem

With 100k+ MAU, high cardinality dimensions cause:
- **Performance issues**: Too many segments to process
- **Memory problems**: Large maps and lists
- **Unusable dashboards**: Hundreds of segments to display
- **Noise**: Small segments with 1-2 users aren't actionable

## Solution Implemented

### 1. **Segment Cardinality Control**

**Constants:**
- `MAX_SEGMENTS_PER_DIMENSION = 15` - Maximum segments returned
- `MIN_SEGMENT_SIZE_ABSOLUTE = 50` - Minimum users per segment
- `MIN_SEGMENT_PERCENTAGE = 0.5%` - Minimum percentage of sample

**Strategy:**
- Segments with <0.5% of users are aggregated into "Other" bucket
- Top 15 segments by user count + risk score
- Prevents dashboard from showing 100+ device models

**Example:**
```
Before: 150 device models (Samsung Galaxy S10, S10+, S10e, S20, S20+, ...)
After: 15 segments (Samsung Galaxy S10 Series, Samsung Galaxy S20 Series, iPhone 12 Series, ... + "Other")
```

### 2. **Risk Factor Normalization**

**Constants:**
- `MAX_RISK_FACTORS = 15` - Maximum risk factors returned
- `MIN_USERS_PER_RISK_FACTOR = 10` - Minimum users affected
- `MIN_RISK_FACTOR_PERCENTAGE = 0.1%` - Minimum percentage

**Normalization Rules:**
- "No session in 30 days" + "No session in 31 days" → "No session in 30+ days"
- "3 crashes" + "4 crashes" → "3+ crashes in last 7 days"
- "Session declined by 70%" + "Session declined by 75%" → "Session frequency declined by 70%+"

**Example:**
```
Before: 50+ unique risk factor strings
After: 15 normalized risk factors
```

### 3. **Device/OS/App Version Normalization**

**Device Normalization:**
- iPhone variants: "iPhone 12 Pro Max" → "iPhone 12 Series"
- Samsung Galaxy: "Galaxy S10", "Galaxy S10+" → "Samsung Galaxy S10 Series"
- Groups similar devices together

**OS Version Normalization:**
- "Android 11.0.1", "Android 11.0.2" → "Android 11"
- "iOS 15.1", "iOS 15.2" → "iOS 15"
- Groups by major version

**App Version Normalization:**
- "v1.2.3", "v1.2.4" → "v1.2.x"
- Groups by major.minor version

### 4. **Stratified Sampling**

**For datasets >50k users:**
- Applies stratified sampling to 10k users
- Ensures representation across risk levels (HIGH/MEDIUM/LOW)
- Maintains statistical validity while reducing processing time

**Algorithm:**
1. Group users by risk level
2. Sample proportionally from each level
3. Ensures all risk levels are represented

### 5. **"Other" Bucket**

**Small segments aggregated:**
- All segments with <0.5% of users go into "Other"
- Shows total count and average metrics
- Prevents noise from tiny segments

**Frontend Display:**
- Clearly labeled as "Other (aggregated)"
- Shows tooltip: "Segments with <0.5% of users"

## Impact

### Before (High Cardinality):
- 150 device models
- 50+ unique risk factors
- 30+ OS versions
- Dashboard shows 200+ rows
- Slow performance
- Unusable for analysis

### After (Controlled Cardinality):
- Max 15 device segments
- Max 15 risk factors
- Max 15 OS segments
- Dashboard shows ~45 rows total
- Fast performance
- Actionable insights

## Configuration

All constants are configurable in `ChurnAnalyticsService.java`:

```java
private static final int MAX_SEGMENTS_PER_DIMENSION = 15;
private static final int MIN_SEGMENT_SIZE_ABSOLUTE = 50;
private static final double MIN_SEGMENT_PERCENTAGE = 0.5;
private static final int MAX_RISK_FACTORS = 15;
private static final int MIN_USERS_PER_RISK_FACTOR = 10;
private static final double MIN_RISK_FACTOR_PERCENTAGE = 0.1;
```

## Testing

To verify cardinality control:

1. **Test with high cardinality data:**
   - Create test data with 200+ unique devices
   - Verify only 15 segments returned
   - Verify "Other" bucket exists

2. **Test normalization:**
   - "iPhone 12 Pro" and "iPhone 12 Pro Max" → "iPhone 12 Series"
   - "No session in 30 days" and "No session in 31 days" → "No session in 30+ days"

3. **Test sampling:**
   - Create 100k users
   - Verify stratified sampling to 10k
   - Verify risk level representation

## Benefits

✅ **Performance**: Faster queries and processing
✅ **Usability**: Dashboard shows actionable segments
✅ **Scalability**: Works with 100k, 1M, 10M users
✅ **Insights**: Focus on segments that matter (>0.5% of users)
✅ **Memory**: Reduced memory footprint

## Example Output

**Device Segments (Before):**
- Samsung Galaxy S10 (1,200 users)
- Samsung Galaxy S10+ (800 users)
- Samsung Galaxy S10e (400 users)
- Samsung Galaxy S20 (1,500 users)
- ... (150 more devices)

**Device Segments (After):**
- Samsung Galaxy S10 Series (2,400 users, 2.4%)
- Samsung Galaxy S20 Series (1,500 users, 1.5%)
- iPhone 12 Series (3,200 users, 3.2%)
- ... (12 more segments)
- Other (aggregated) (15,000 users, 15.0%)

This makes the dashboard **actually usable** for large user bases!

