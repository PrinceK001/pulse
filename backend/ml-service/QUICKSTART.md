# Churn Prediction ML Service - Quick Start

## Prerequisites

- Python 3.11+
- pip

## Setup

1. **Install dependencies:**
```bash
cd backend/ml-service
pip install -r requirements.txt
```

2. **Train the model:**
```bash
python training/train_model.py --samples 2000
```

This will:
- Generate sample training data
- Train an XGBoost model
- Save model to `models/churn_model_v1.pkl`

3. **Start the service:**
```bash
# Option 1: Using start script
./start.sh

# Option 2: Direct uvicorn
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Test the Service

```bash
# Health check
curl http://localhost:8000/health

# Single prediction
curl -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "user123",
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

## Docker

```bash
# Build
docker build -t pulse-ml-service .

# Run
docker run -p 8000:8000 pulse-ml-service
```

## Integration with Java Backend

The Java backend will automatically call this service when:
1. ML service URL is configured in `application.conf` or environment variable
2. Service is running on the configured port (default: 8000)

If ML service is unavailable, the backend falls back to rule-based calculation.

