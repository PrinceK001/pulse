# How to View User Health, Churn Probability, and Risk Factors

## Overview

The Churn Prediction Dashboard provides a comprehensive view of:
1. **General Health** of your user base
2. **Churn Probability** for each user
3. **Potential Reasons for Churn** (risk factors)

## Accessing the Dashboard

### Step 1: Navigate to User Engagement Page

1. Open your Pulse application
2. Go to **User Engagement** page
3. Scroll down to the **"Churn Prediction Dashboard"** section

### Step 2: View Overall Health Metrics

At the top, you'll see **4 overview cards**:

1. **Overall Health** - Percentage of healthy users (low risk)
   - Green: >90% healthy
   - Yellow: 85-90% healthy  
   - Red: <85% healthy

2. **High Risk Users** - Count and percentage of users at high churn risk
   - Shows users with risk score ≥ 70

3. **Medium Risk Users** - Count and percentage of users at medium risk
   - Shows users with risk score 40-69

4. **Average Churn Risk** - Average risk score across all users
   - Color-coded: Green (<40), Yellow (40-69), Red (≥70)

### Step 3: View Individual User Details

The **"At-Risk Users"** table shows:

| Column | Description |
|--------|-------------|
| **User ID** | Unique identifier for each user |
| **Health Status** | Risk level badge (HIGH/MEDIUM/LOW) |
| **Risk Score** | 0-100 score with progress bar |
| **Churn Probability** | Percentage likelihood of churning (0-100%) |
| **Days Since Last Session** | Inactivity period |
| **Sessions (7d)** | Engagement metric |
| **Crashes (7d)** | Performance issue indicator |
| **Risk Factors** | Primary reasons for churn risk (hover to see all) |
| **Device** | Device model information |

### Step 4: View Risk Factors (Churn Reasons)

**Hover over the "Risk Factors" column** to see all reasons why a user is at risk:

Common risk factors include:
- "No session in X days" - User hasn't been active
- "Zero sessions in last 7 days" - Complete drop-off
- "Session frequency declined by X%" - Engagement decline
- "X crash(es) in last 7 days" - Performance issues
- "X ANR(s) in last 7 days" - App freezing issues
- "High frozen frame rate: X%" - UI performance problems
- "Short average session duration: Xs" - Low engagement
- "Limited screen diversity" - Not exploring the app

### Step 5: Filter and Analyze

Use the filters at the top:

1. **Risk Level Filter**: 
   - All / High Risk / Medium Risk / Low Risk
   
2. **Min Risk Score Filter**:
   - All / 40+ / 70+
   - Focus on users above certain thresholds

## Understanding the Metrics

### Churn Probability
- **0-30%**: Low risk (green)
- **30-70%**: Medium risk (yellow)
- **70-100%**: High risk (red)

### Risk Score
- **0-39**: LOW risk
- **40-69**: MEDIUM risk
- **70-100**: HIGH risk

### Health Status
Based on multiple factors:
- Session frequency and recency
- Performance issues (crashes, ANRs, frozen frames)
- Engagement depth (screens visited, session duration)
- Engagement trends (declining vs stable)

## API Endpoints

You can also access this data via API:

### Get All Users with Churn Predictions
```bash
curl -X POST http://localhost:8080/api/v1/churn/predictions \
  -H "Content-Type: application/json" \
  -d '{
    "riskLevel": "HIGH",
    "minRiskScore": 70,
    "limit": 100
  }'
```

### Get Specific User's Churn Prediction
```bash
curl http://localhost:8080/api/v1/churn/predictions/user/{userId}
```

Response includes:
- `riskScore`: 0-100
- `churnProbability`: 0.0-1.0
- `riskLevel`: HIGH/MEDIUM/LOW
- `primaryRiskFactors`: Array of reasons
- All user metrics (sessions, crashes, etc.)

## Example Response

```json
{
  "userId": "user123",
  "riskScore": 75,
  "churnProbability": 0.75,
  "riskLevel": "HIGH",
  "primaryRiskFactors": [
    "No session in 35 days",
    "Zero sessions in last 7 days",
    "3 crash(es) in last 7 days",
    "High frozen frame rate: 30%"
  ],
  "daysSinceLastSession": 35,
  "sessionsLast7Days": 0,
  "crashCountLast7Days": 3,
  "frozenFrameRate": 0.3
}
```

## Taking Action

Based on the dashboard:

1. **High Risk Users (Red)**:
   - Immediate intervention needed
   - Send re-engagement campaigns
   - Investigate performance issues
   - Offer incentives to return

2. **Medium Risk Users (Yellow)**:
   - Monitor closely
   - Proactive engagement
   - Address performance issues
   - Improve user experience

3. **Low Risk Users (Green)**:
   - Maintain engagement
   - Continue good experience
   - Use as success benchmarks

## Tips

- **Sort by Risk Score**: Click column headers to sort
- **Filter by Device**: Identify device-specific issues
- **Monitor Trends**: Check regularly to catch early warning signs
- **Export Data**: Use API to export for further analysis
- **Segment Analysis**: Use the segments endpoint to see patterns by device/OS/app version

## Next Steps

1. Set up alerts for high-risk users
2. Create automated re-engagement campaigns
3. Track churn prediction accuracy over time
4. Use insights to improve app performance and UX

