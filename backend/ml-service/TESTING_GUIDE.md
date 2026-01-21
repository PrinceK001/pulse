# Churn Prediction ML Service - Testing Guide

## Prerequisites

1. Python 3.11+ installed
2. Java backend running (or Docker)
3. ClickHouse with sample data (optional, for real data testing)

## Step 1: Test Python ML Service Standalone

### 1.1 Install Dependencies
```bash
cd backend/ml-service
pip install -r requirements.txt
```

### 1.2 Train the Model
```bash
# Train with sample data (2000 users)
python training/train_model.py --samples 2000

# This will create: models/churn_model_v1.pkl
```

### 1.3 Start the ML Service
```bash
# Option 1: Using start script
./start.sh

# Option 2: Direct uvicorn (with auto-reload)
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

### 1.4 Test Health Endpoint
```bash
curl http://localhost:8000/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "model_loaded": true,
  "model_version": "v1"
}
```

### 1.5 Test Single Prediction
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user_123",
    "sessions_last_7_days": 2,
    "sessions_last_30_days": 15,
    "days_since_last_session": 5,
    "avg_session_duration": 120000,
    "unique_screens_last_7_days": 3,
    "crash_count_last_7_days": 1,
    "anr_count_last_7_days": 0,
    "frozen_frame_rate": 0.1
  }'
```

**Expected Response:**
```json
{
  "user_id": "test_user_123",
  "risk_score": 45,
  "churn_probability": 0.45,
  "risk_level": "MEDIUM",
  "feature_importance": {
    "days_since": 0.25,
    "engagement_decline": 0.20,
    ...
  }
}
```

### 1.6 Test Batch Prediction
```bash
curl -X POST http://localhost:8000/predict/batch \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user1",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 10,
        "days_since_last_session": 20,
        "avg_session_duration": 60000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.2
      },
      {
        "user_id": "user2",
        "sessions_last_7_days": 10,
        "sessions_last_30_days": 40,
        "days_since_last_session": 1,
        "avg_session_duration": 180000,
        "unique_screens_last_7_days": 8,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.02
      }
    ]
  }'
```

### 1.7 Use Test Script
```bash
./test_service.sh
```

## Step 2: Test Java Backend Integration

### 2.1 Configure ML Service URL

**Option A: Environment Variable**
```bash
export CONFIG_SERVICE_APPLICATION_MLSERVICEBASEURL=http://localhost:8000
```

**Option B: Config File**
Edit `backend/server/src/main/resources/conf/application-default.conf`:
```
app {
    mlServiceBaseUrl=http://localhost:8000
}
```

### 2.2 Start Java Backend
```bash
cd backend/server
mvn clean compile
# Then start your application (method depends on your setup)
```

### 2.3 Test Churn Predictions API

**Get all high-risk users:**
```bash
curl -X POST http://localhost:8080/api/v1/churn/predictions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "riskLevel": "HIGH",
    "minRiskScore": 70,
    "limit": 10
  }'
```

**Get predictions for specific user:**
```bash
curl http://localhost:8080/api/v1/churn/predictions/user/USER_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

**Get risk by segment:**
```bash
curl http://localhost:8080/api/v1/churn/predictions/segments?limit=5 \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 2.4 Test Fallback Mechanism

**Stop ML service:**
```bash
# Stop the Python ML service (Ctrl+C or kill process)
```

**Make API call again:**
```bash
curl -X POST http://localhost:8080/api/v1/churn/predictions \
  -H "Content-Type: application/json" \
  -d '{
    "riskLevel": "HIGH",
    "limit": 10
  }'
```

**Expected:** Should still return results using rule-based fallback. Check logs for:
```
WARN: ML prediction failed for user ..., using rule-based fallback
```

## Step 3: Test with Real ClickHouse Data

### 3.1 Verify ClickHouse Connection
```bash
# Check if ClickHouse has user data
# (Use your ClickHouse client or query tool)
```

### 3.2 Test with Real User IDs
```bash
# Replace USER_ID with actual user from your database
curl http://localhost:8080/api/v1/churn/predictions/user/USER_ID \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 3.3 Test Bulk Predictions
```bash
curl -X POST http://localhost:8080/api/v1/churn/predictions \
  -H "Content-Type: application/json" \
  -d '{
    "limit": 100,
    "minRiskScore": 50
  }'
```

## Step 4: Test Frontend Integration

### 4.1 Start Frontend
```bash
cd pulse-ui
yarn install
yarn start
```

### 4.2 Navigate to User Engagement Page
1. Open browser: http://localhost:3000
2. Navigate to: **User Engagement** page
3. Scroll to: **Churn Prediction Dashboard** section

### 4.3 Verify Dashboard
- ✅ Overview cards showing risk counts
- ✅ User table with risk scores
- ✅ Risk factors displayed
- ✅ Filters working (risk level, device, etc.)

## Step 5: Performance Testing

### 5.1 Test Latency
```bash
# Time a single prediction
time curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{...}'
```

**Expected:** < 100ms per prediction

### 5.2 Test Throughput
```bash
# Use Apache Bench or similar
ab -n 100 -c 10 -p test_request.json -T application/json \
  http://localhost:8000/predict
```

### 5.3 Test Batch Performance
```bash
# Test with 100 users at once
# (Create a test file with 100 user objects)
curl -X POST http://localhost:8000/predict/batch \
  -H "Content-Type: application/json" \
  -d @batch_test.json
```

## Step 6: Test Error Scenarios

### 6.1 Invalid Request
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test",
    "sessions_last_7_days": -1
  }'
```

**Expected:** 422 Validation Error

### 6.2 Missing Fields
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test"
  }'
```

**Expected:** 422 Validation Error

### 6.3 ML Service Down
1. Stop ML service
2. Make Java API call
3. Verify fallback works
4. Check logs for fallback messages

## Step 7: Test Model Accuracy

### 7.1 Train Model with More Data
```bash
cd backend/ml-service
python training/train_model.py --samples 5000
```

### 7.2 Check Model Metrics
After training, you'll see:
```
Model Performance:
Precision: 0.85
Recall: 0.82
F1-Score: 0.83
ROC-AUC: 0.92
```

### 7.3 Test Different Risk Scenarios

**High Risk User:**
```json
{
  "sessions_last_7_days": 0,
  "sessions_last_30_days": 5,
  "days_since_last_session": 35,
  "crash_count_last_7_days": 3,
  "frozen_frame_rate": 0.3
}
```
**Expected:** risk_score >= 70 (HIGH)

**Low Risk User:**
```json
{
  "sessions_last_7_days": 12,
  "sessions_last_30_days": 45,
  "days_since_last_session": 1,
  "crash_count_last_7_days": 0,
  "frozen_frame_rate": 0.01
}
```
**Expected:** risk_score < 40 (LOW)

## Step 8: Docker Testing

### 8.1 Build and Run ML Service
```bash
cd backend/ml-service
docker build -t pulse-ml-service .
docker run -p 8000:8000 pulse-ml-service
```

### 8.2 Test in Docker
```bash
curl http://localhost:8000/health
```

### 8.3 Full Stack with Docker Compose
```bash
cd deploy
docker-compose up --build pulse-ml-service pulse-server
```

## Troubleshooting

### ML Service Not Starting
- Check Python version: `python3 --version` (need 3.11+)
- Check dependencies: `pip list | grep fastapi`
- Check port availability: `lsof -i :8000`

### Model Not Found
- Train model: `python training/train_model.py`
- Check file exists: `ls -la models/churn_model_v1.pkl`

### Java Can't Connect
- Verify ML service running: `curl http://localhost:8000/health`
- Check config: `echo $CONFIG_SERVICE_APPLICATION_MLSERVICEBASEURL`
- Check network: `ping localhost` (or service hostname)

### Predictions Seem Wrong
- Check model version: `curl http://localhost:8000/health`
- Retrain model with more data
- Verify feature extraction in Java logs

## Quick Test Checklist

- [ ] ML service starts successfully
- [ ] Health endpoint returns 200
- [ ] Single prediction works
- [ ] Batch prediction works
- [ ] Java backend connects to ML service
- [ ] Churn predictions API returns data
- [ ] Fallback works when ML service down
- [ ] Frontend dashboard displays data
- [ ] High-risk users identified correctly
- [ ] Low-risk users identified correctly

## Sample Test Data

Save this as `test_user.json`:
```json
{
  "user_id": "test_user_001",
  "sessions_last_7_days": 3,
  "sessions_last_30_days": 18,
  "days_since_last_session": 4,
  "avg_session_duration": 95000,
  "unique_screens_last_7_days": 5,
  "crash_count_last_7_days": 1,
  "anr_count_last_7_days": 0,
  "frozen_frame_rate": 0.08
}
```

Test with:
```bash
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d @test_user.json
```

