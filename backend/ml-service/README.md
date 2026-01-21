# Churn Prediction ML Service

ML microservice for predicting user churn risk using XGBoost.

## Setup

### Install Dependencies

```bash
pip install -r requirements.txt
```

### Train Model

```bash
# Train with sample data (default)
python training/train_model.py

# Train with custom sample size
python training/train_model.py --samples 5000

# Train with ClickHouse data (when implemented)
python training/train_model.py --clickhouse "clickhouse://localhost:8123/otel"
```

### Run Service

```bash
# Development
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Production
uvicorn app.main:app --host 0.0.0.0 --port 8000 --workers 4
```

### Docker

```bash
# Build
docker build -t churn-ml-service .

# Run
docker run -p 8000:8000 churn-ml-service
```

## API Endpoints

### Health Check
```
GET /health
```

### Single Prediction
```
POST /predict
Content-Type: application/json

{
  "user_id": "user123",
  "sessions_last_7_days": 5,
  "sessions_last_30_days": 20,
  "days_since_last_session": 2,
  "avg_session_duration": 120000,
  "unique_screens_last_7_days": 4,
  "crash_count_last_7_days": 0,
  "anr_count_last_7_days": 0,
  "frozen_frame_rate": 0.05
}
```

### Batch Prediction
```
POST /predict/batch
Content-Type: application/json

{
  "users": [
    { ... user 1 ... },
    { ... user 2 ... }
  ]
}
```

## Model Features

The model uses 11 features:
1. sessions_last_7_days
2. sessions_last_30_days
3. days_since_last_session
4. avg_session_duration_sec
5. unique_screens_last_7_days
6. crash_count_last_7_days
7. anr_count_last_7_days
8. frozen_frame_rate
9. session_frequency (derived)
10. engagement_decline (derived)
11. performance_score (derived)

## Model Training

The model is trained on:
- User engagement features (sessions, duration, screens)
- Performance metrics (crashes, ANRs, frozen frames)
- Derived features (engagement trends, performance scores)

Label: User churned if no session for 30+ days

