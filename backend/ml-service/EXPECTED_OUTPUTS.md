# ML Model Expected Outputs

## Model Output Structure

### Single User Prediction

**Input:**
```json
{
  "user_id": "user123",
  "sessions_last_7_days": 2,
  "sessions_last_30_days": 15,
  "days_since_last_session": 5,
  "avg_session_duration": 120000,
  "unique_screens_last_7_days": 3,
  "crash_count_last_7_days": 1,
  "anr_count_last_7_days": 0,
  "frozen_frame_rate": 0.1
}
```

**Output:**
```json
{
  "user_id": "user123",
  "risk_score": 45,              // Integer 0-100
  "churn_probability": 0.45,     // Float 0.0-1.0
  "risk_level": "MEDIUM",        // "HIGH", "MEDIUM", or "LOW"
  "feature_importance": {        // Optional, shows which features matter most
    "days_since_last_session": 0.28,
    "engagement_decline": 0.22,
    "performance_score": 0.15,
    "sessions_last_7_days": 0.12,
    "frozen_frame_rate": 0.10,
    "crash_count_last_7_days": 0.08,
    "avg_session_duration_sec": 0.05,
    "sessions_last_30_days": 0.04,
    "unique_screens_last_7_days": 0.03,
    "session_frequency": 0.02,
    "anr_count_last_7_days": 0.01
  }
}
```

## Risk Score Ranges

### LOW Risk (0-39)
**Characteristics:**
- Active users
- Recent sessions
- Good engagement
- Few/no performance issues

**Example Output:**
```json
{
  "risk_score": 25,
  "churn_probability": 0.25,
  "risk_level": "LOW"
}
```

**Typical User Profile:**
- Sessions in last 7 days: 8-15
- Days since last session: 0-2
- Crashes: 0
- Session duration: 2-5 minutes

### MEDIUM Risk (40-69)
**Characteristics:**
- Declining engagement
- Some performance issues
- Moderate inactivity

**Example Output:**
```json
{
  "risk_score": 55,
  "churn_probability": 0.55,
  "risk_level": "MEDIUM"
}
```

**Typical User Profile:**
- Sessions in last 7 days: 2-5
- Days since last session: 3-7
- Crashes: 1-2
- Session duration: 30s-2min

### HIGH Risk (70-100)
**Characteristics:**
- Inactive for extended period
- Multiple performance issues
- Significant engagement decline

**Example Output:**
```json
{
  "risk_score": 85,
  "churn_probability": 0.85,
  "risk_level": "HIGH"
}
```

**Typical User Profile:**
- Sessions in last 7 days: 0
- Days since last session: 14-30+
- Crashes: 3+
- Session duration: <30s

## Feature Importance

The model shows which features contribute most to the prediction:

**High Importance (>0.20):**
- `days_since_last_session` - Strong indicator
- `engagement_decline` - Engagement trend

**Medium Importance (0.10-0.20):**
- `performance_score` - Crashes/ANRs impact
- `sessions_last_7_days` - Recent activity
- `frozen_frame_rate` - UI performance

**Low Importance (<0.10):**
- `sessions_last_30_days` - Historical context
- `unique_screens_last_7_days` - Feature diversity
- `avg_session_duration` - Engagement depth

## Analytics Output Structure

### Overall Metrics
```json
{
  "totalUsers": 10000,
  "highRiskCount": 1500,
  "mediumRiskCount": 3000,
  "lowRiskCount": 5500,
  "averageRiskScore": 42.5,
  "overallChurnProbability": 0.425
}
```

### Risk Distribution
```json
{
  "riskDistribution": {
    "0-20": 3500,    // 35% of users
    "20-40": 2000,   // 20% of users
    "40-60": 2500,   // 25% of users
    "60-80": 1500,   // 15% of users
    "80-100": 500    // 5% of users
  }
}
```

### Top Risk Factors
```json
{
  "topRiskFactors": [
    {
      "factor": "No session in 30+ days",
      "userCount": 1200,
      "percentage": 12.0,
      "severity": "HIGH"
    },
    {
      "factor": "Zero sessions in last 7 days",
      "userCount": 950,
      "percentage": 9.5,
      "severity": "HIGH"
    },
    {
      "factor": "3+ crashes in last 7 days",
      "userCount": 450,
      "percentage": 4.5,
      "severity": "HIGH"
    }
  ]
}
```

### Segment Stats
```json
{
  "deviceSegments": {
    "Samsung Galaxy S10 Series": {
      "userCount": 2500,
      "averageRiskScore": 38.5,
      "highRiskCount": 300,
      "highRiskPercentage": 12.0,
      "churnProbability": 0.385,
      "topRiskFactors": [
        "No session in 14-29 days",
        "Session frequency declined by 50-69%"
      ]
    }
  }
}
```

## Model Behavior

### With Good Data
- **Active user**: Low risk (10-30)
- **Recent session**: Low risk
- **No crashes**: Low risk
- **Good engagement**: Low risk

### With Warning Signs
- **7+ days inactive**: Medium risk (40-60)
- **Declining sessions**: Medium risk
- **1-2 crashes**: Medium risk

### With Critical Issues
- **30+ days inactive**: High risk (70-90)
- **Zero sessions**: High risk
- **3+ crashes**: High risk
- **High frozen frames**: High risk

## Fallback Behavior

If ML service is unavailable:
- Falls back to rule-based calculation
- Still returns valid risk scores
- Logs warning message
- No interruption to user experience

## Model Training Output

When training, you'll see:
```
Model Performance:
Precision:  0.85
Recall:    0.82
F1-Score:  0.83
ROC-AUC:   0.92

Feature Importance (Top 10):
days_since_last_session    0.2850
engagement_decline         0.2200
performance_score          0.1500
sessions_last_7_days      0.1200
frozen_frame_rate         0.1000
crash_count_last_7_days   0.0800
avg_session_duration_sec  0.0500
sessions_last_30_days     0.0400
unique_screens_last_7_days 0.0300
session_frequency         0.0200
```

This shows which features the model considers most important for churn prediction.

