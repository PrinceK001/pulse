# Scalable Churn Analytics Dashboard

## Overview

This dashboard is designed for **large user bases (100k+ MAU)** and provides **aggregated insights** instead of individual user listings.

## Key Features

### 1. **Aggregate Metrics** (Not Individual Users)
- Overall health percentage
- Total at-risk users (counts, not lists)
- Average churn risk across all users
- Risk score distribution (0-20, 20-40, 40-60, 60-80, 80-100)

### 2. **Risk Factor Analysis**
- **Top 10 most common risk factors** across all users
- Frequency and percentage of users affected
- Severity classification (HIGH/MEDIUM/LOW)
- Helps identify **systemic issues** affecting many users

### 3. **Segment Analysis**
- **Device segments**: Which devices have highest churn risk?
- **OS version segments**: Which OS versions are problematic?
- **App version segments**: Which app versions have issues?
- Shows average risk, high-risk percentage, and top factors per segment

### 4. **Performance Impact Analysis**
- How many users are affected by crashes/ANRs/frozen frames?
- Average performance issue rates
- **Correlation**: How much do performance issues contribute to churn risk?

### 5. **Engagement Patterns**
- Inactive users count (7+ days)
- Declining engagement users (50%+ drop)
- Average session metrics
- Helps identify engagement trends

## Dashboard Tabs

### Overview Tab
- Risk score distribution chart
- Top 5 risk factors with visualizations
- Quick insights at a glance

### Risk Factors Tab
- Complete ranked list of all risk factors
- Shows which issues affect the most users
- Helps prioritize fixes

### Segments Tab
- Device breakdown
- OS version breakdown
- App version breakdown
- Identify problematic segments

### Performance Impact Tab
- Users affected by performance issues
- Average rates
- Risk correlation visualization

### Engagement Patterns Tab
- Inactive user counts
- Engagement decline metrics
- Session statistics

## How to Use for 100k+ Users

### 1. **Identify Systemic Issues**
Look at "Top Risk Factors" to see what affects the most users:
- "No session in 30+ days" affecting 15,000 users → Re-engagement campaign needed
- "3+ crashes in last 7 days" affecting 5,000 users → Critical bug fix needed

### 2. **Segment Prioritization**
Check "Segments" tab to find:
- Device: "Samsung Galaxy S10" has 45% high-risk users → Device-specific issue
- OS: "Android 11" has 60% high-risk → OS compatibility issue
- App: "v2.1.0" has 50% high-risk → Bad release, rollback needed

### 3. **Performance Focus**
"Performance Impact" shows:
- 20,000 users with crashes → Fix crash bugs
- High correlation (40%+) → Performance is major churn driver

### 4. **Engagement Strategy**
"Engagement Patterns" reveals:
- 30,000 inactive users → Re-engagement campaign
- 25,000 with declining engagement → Feature improvements needed

## API Endpoint

```bash
POST /api/v1/churn/analytics
Content-Type: application/json

{
  "riskLevel": "HIGH",  // Optional filter
  "minRiskScore": 70,   // Optional filter
  "limit": 10000        // Sample size for analytics (max 10k)
}
```

**Response includes:**
- Aggregate metrics
- Risk distribution
- Top risk factors
- Segment breakdowns
- Performance impact
- Engagement patterns

## Benefits Over Individual User Table

✅ **Scalable**: Works with 100k, 1M, 10M users
✅ **Actionable**: Shows patterns, not noise
✅ **Fast**: Aggregated queries, not individual predictions
✅ **Insightful**: Identifies systemic issues
✅ **Prioritized**: Shows what affects most users

## Example Insights

**Instead of:** "User ABC123 has risk score 75"
**You get:** "15% of users (15,000) have 'No session in 30+ days' as top risk factor"

**Instead of:** "User XYZ789 has 3 crashes"
**You get:** "5,000 users (5%) have 3+ crashes, correlating with 45% higher churn risk"

**Instead of:** Individual device listings
**You get:** "Samsung Galaxy S10 has 45% high-risk users vs 12% average"

## Next Steps

1. **Fix systemic issues** identified in Top Risk Factors
2. **Target segments** with highest risk percentages
3. **Improve performance** for users with crashes/ANRs
4. **Re-engage** inactive users (largest segment)
5. **Monitor trends** over time to measure impact

This dashboard gives you **actionable insights at scale**, not individual user noise.

