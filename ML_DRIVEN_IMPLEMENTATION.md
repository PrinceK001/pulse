# ML-Driven Churn Analytics Implementation

## Overview

This implementation transforms the churn prediction system from a **hybrid approach** (ML predictions + static rules) to a **fully ML-driven approach** that discovers patterns automatically and scales to 4M MAU.

## Key Changes

### 1. ML Service Enhancements (`backend/ml-service/`)

#### New Dependencies
- `shap==0.43.0` - For explainability and feature importance
- `scipy==1.11.4` - For statistical analysis and trend detection

#### Enhanced Model (`app/model.py`)
- **SHAP Values**: `get_shap_values()` - Explains individual predictions
- **Pattern Discovery**: `discover_churn_patterns()` - Uses DBSCAN clustering to find patterns automatically
- **Anomaly Detection**: `detect_anomalies()` - Uses Isolation Forest to detect unusual patterns
- **Root Cause Analysis**: `analyze_root_causes()` - Uses ML feature importance instead of static rules
- **Trend Analysis**: `analyze_trends()` - Uses time series regression for trend detection

#### New API Endpoints (`app/main.py`)
- `POST /analyze/patterns` - Discover churn patterns using ML clustering
- `POST /analyze/root-causes` - Analyze root causes using ML feature importance
- `POST /analyze/trends` - Detect trends and anomalies using ML regression
- `POST /analyze/anomalies` - Detect anomalies in churn predictions

### 2. Java Service Updates

#### ML Client (`MLPredictionClient.java`)
- **Batch Prediction**: `predictChurnBatch()` - Single API call for multiple users (scales to 4M MAU)
- **Pattern Discovery**: `discoverPatterns()` - Calls ML pattern discovery endpoint
- **Root Cause Analysis**: `analyzeRootCauses()` - Calls ML root cause analysis endpoint
- **Trend Analysis**: `analyzeTrends()` - Calls ML trend analysis endpoint

#### Enhanced Analytics Service (`ChurnAnalyticsService.java`)
- **ML-Driven Analysis**: Uses ML insights instead of static rules
- **Batch Processing**: Processes 1-2k users (statistically valid sample)
- **Parallel ML Calls**: Gets root causes and patterns in parallel
- **Fallback**: Falls back to basic analytics if ML analysis fails

#### New Response Models
- `MLPatternDiscoveryResponse` - ML-discovered patterns
- `MLRootCauseAnalysisResponse` - ML-identified root causes
- `MLTrendAnalysisResponse` - ML-detected trends
- Enhanced `ChurnAnalyticsResponse` with:
  - `RootCauseAnalysis` - ML-driven root causes
  - `PriorityFix` - Prioritized fixes based on ML impact
  - `TrendAnalysis` - ML-detected trends and anomalies
  - `PatternInsights` - ML-discovered patterns

## Architecture

### Before (Hybrid Approach)
```
User Features → ML Prediction → Static Rules → Insights
                (ML)            (Static)
```

### After (ML-Driven)
```
User Features → Batch ML Prediction → ML Pattern Discovery → ML Insights
                (1 API call)         (Automatic)            (ML-driven)
```

## Scalability for 4M MAU

### Sample Size
- **Analytics**: 1,000-2,000 users (statistically valid)
- **Stratified Sampling**: Applied if >50k users
- **Extrapolation**: Results extrapolated to full user base

### Performance
- **Batch Prediction**: 1 API call instead of N calls
- **Parallel Analysis**: Root causes and patterns analyzed in parallel
- **Response Time**: < 5 seconds for analytics

### Memory Efficiency
- **Streaming**: Processes users in batches
- **Cardinality Control**: Limits segments and factors
- **Sampling**: Reduces memory footprint

## ML-Driven Insights

### 1. Root Cause Analysis
- **Method**: ML feature importance aggregation
- **Output**: Top 10 root causes ranked by impact
- **Benefits**: Discovers unexpected patterns automatically

### 2. Pattern Discovery
- **Method**: DBSCAN clustering on features + predictions
- **Output**: Common churn patterns (e.g., "high_crashes + no_sessions")
- **Benefits**: Finds patterns we didn't know existed

### 3. Trend Analysis
- **Method**: Time series regression comparing current vs historical
- **Output**: Trend direction, strength, statistical significance
- **Benefits**: Detects anomalies and trends automatically

### 4. Anomaly Detection
- **Method**: Isolation Forest on prediction distribution
- **Output**: Anomaly count, spike detection, severity
- **Benefits**: Alerts on unusual patterns

## Example Output

### Root Cause Analysis
```json
{
  "rootCauseAnalysis": {
    "primaryCauses": [
      {
        "cause": "High crash rate",
        "affectedUserCount": 45000,
        "averageSeverity": 85.0,
        "impactScore": 3825000,
        "importance": 0.28,
        "correlationWithHighRisk": 0.75,
        "recommendedFix": "Fix crashes in affected app versions",
        "estimatedChurnReduction": 8.4
      }
    ],
    "aggregateFeatureImportance": {
      "days_since": 0.28,
      "crash_count": 0.22,
      "engagement_decline": 0.15
    }
  }
}
```

### Pattern Discovery
```json
{
  "patternInsights": {
    "commonPatterns": [
      {
        "pattern": "high_crashes + no_sessions",
        "userCount": 15000,
        "averageRiskScore": 82.5,
        "churnProbability": 0.825
      }
    ]
  }
}
```

### Trend Analysis
```json
{
  "trendAnalysis": {
    "trendDirection": 25.5,
    "trendDirectionLabel": "increasing",
    "trendStrength": 0.15,
    "statisticalSignificance": true,
    "isAnomaly": true,
    "anomalies": [
      {
        "type": "SPIKE",
        "description": "Churn risk increased by 25.5%",
        "severity": 0.15
      }
    ]
  }
}
```

## Benefits Over Static Rules

| Aspect | Static Rules | ML-Driven |
|--------|--------------|-----------|
| Pattern Discovery | Manual, predefined | Automatic discovery |
| Edge Cases | Missed | Detected automatically |
| Non-linear Relationships | Assumed linear | Captured by model |
| Feature Interactions | Manual rules | Learned by model |
| Adaptability | Fixed thresholds | Learns from data |
| Scalability | Limited | Scales to 4M MAU |
| Explainability | Rule-based | SHAP values |

## Testing

### Test ML Service
```bash
cd backend/ml-service
./run_and_test.sh
```

### Test Batch Prediction
```bash
curl -X POST http://localhost:8000/predict/batch \
  -H "Content-Type: application/json" \
  -d @test_batch.json
```

### Test Pattern Discovery
```bash
curl -X POST http://localhost:8000/analyze/patterns \
  -H "Content-Type: application/json" \
  -d @test_batch.json
```

### Test Root Cause Analysis
```bash
curl -X POST http://localhost:8000/analyze/root-causes \
  -H "Content-Type: application/json" \
  -d @test_batch.json
```

## Next Steps

1. **Frontend Updates**: Display ML-driven insights in dashboard
2. **Historical Data**: Add date filtering for trend analysis
3. **Caching**: Cache ML insights for faster responses
4. **Monitoring**: Track ML model performance and drift
5. **A/B Testing**: Compare ML-driven vs static rule insights

## Files Modified

### Python (ML Service)
- `backend/ml-service/app/model.py` - Enhanced with ML analysis methods
- `backend/ml-service/app/main.py` - Added new endpoints
- `backend/ml-service/requirements.txt` - Added SHAP and scipy

### Java (Backend Service)
- `backend/server/src/main/java/org/dreamhorizon/pulseserver/client/ml/MLPredictionClient.java` - Added batch and analysis methods
- `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/churn/ChurnAnalyticsService.java` - Uses ML insights
- `backend/server/src/main/java/org/dreamhorizon/pulseserver/service/churn/models/ChurnAnalyticsResponse.java` - Enhanced response models
- New files:
  - `MLPatternDiscoveryResponse.java`
  - `MLRootCauseAnalysisResponse.java`
  - `MLTrendAnalysisResponse.java`

## Performance Characteristics

- **Sample Size**: 1,000-2,000 users (statistically valid)
- **ML Calls**: 3 parallel calls (batch prediction, root causes, patterns)
- **Response Time**: < 5 seconds
- **Scalability**: Works for 4M MAU (extrapolates from sample)
- **Memory**: Efficient batch processing

