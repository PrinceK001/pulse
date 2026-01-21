#!/bin/bash

# Churn Prediction ML Service - Run and Test Script
# This script will: install deps, train model, start service, and test it

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "=========================================="
echo "Churn Prediction ML Service"
echo "Run and Test Script"
echo "=========================================="
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check Python
echo "1. Checking Python installation..."
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python 3 not found. Please install Python 3.11+${NC}"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | cut -d' ' -f2)
echo -e "${GREEN}✅ Python $PYTHON_VERSION found${NC}"
echo ""

# Check if virtual environment exists
if [ ! -d "venv" ]; then
    echo "2. Creating virtual environment..."
    python3 -m venv venv
    echo -e "${GREEN}✅ Virtual environment created${NC}"
fi

# Activate virtual environment
echo "3. Activating virtual environment..."
source venv/bin/activate
echo -e "${GREEN}✅ Virtual environment activated${NC}"
echo ""

# Install dependencies
echo "4. Installing dependencies..."
pip install -q --upgrade pip
pip install -q -r requirements.txt
echo -e "${GREEN}✅ Dependencies installed${NC}"
echo ""

# Check if model exists
if [ ! -f "models/churn_model_v1.pkl" ]; then
    echo "5. Training model (this may take a minute)..."
    python training/train_model.py --samples 2000
    echo -e "${GREEN}✅ Model trained and saved${NC}"
else
    echo -e "${GREEN}✅ Model already exists (models/churn_model_v1.pkl)${NC}"
fi
echo ""

# Check if port 8000 is available
if lsof -Pi :8000 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
    echo -e "${YELLOW}⚠️  Port 8000 is already in use${NC}"
    echo "   Killing existing process..."
    kill $(lsof -t -i:8000) 2>/dev/null || true
    sleep 2
fi

# Start the service in background
echo "6. Starting ML service on port 8000..."
uvicorn app.main:app --host 0.0.0.0 --port 8000 > /tmp/ml_service.log 2>&1 &
SERVICE_PID=$!
echo "   Service PID: $SERVICE_PID"
echo "   Logs: /tmp/ml_service.log"

# Wait for service to start
echo "7. Waiting for service to be ready..."
MAX_WAIT=30
WAIT_COUNT=0
while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if curl -s http://localhost:8000/health > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Service is ready!${NC}"
        break
    fi
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
    echo -n "."
done
echo ""

if [ $WAIT_COUNT -eq $MAX_WAIT ]; then
    echo -e "${RED}❌ Service failed to start. Check logs: /tmp/ml_service.log${NC}"
    kill $SERVICE_PID 2>/dev/null || true
    exit 1
fi

echo ""
echo "=========================================="
echo "Running Tests"
echo "=========================================="
echo ""

# Test 1: Health check
echo "Test 1: Health Check"
echo "-------------------"
HEALTH_RESPONSE=$(curl -s http://localhost:8000/health)
echo "$HEALTH_RESPONSE" | python3 -m json.tool
if echo "$HEALTH_RESPONSE" | grep -q '"status": "healthy"'; then
    echo -e "${GREEN}✅ Health check passed${NC}"
else
    echo -e "${RED}❌ Health check failed${NC}"
fi
echo ""

# Test 2: Single prediction (medium risk)
echo "Test 2: Single Prediction (Medium Risk User)"
echo "--------------------------------------------"
PREDICTION_RESPONSE=$(curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user_001",
    "sessions_last_7_days": 3,
    "sessions_last_30_days": 18,
    "days_since_last_session": 4,
    "avg_session_duration": 95000,
    "unique_screens_last_7_days": 5,
    "crash_count_last_7_days": 1,
    "anr_count_last_7_days": 0,
    "frozen_frame_rate": 0.08
  }')
echo "$PREDICTION_RESPONSE" | python3 -m json.tool
if echo "$PREDICTION_RESPONSE" | grep -q '"risk_score"'; then
    RISK_SCORE=$(echo "$PREDICTION_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['risk_score'])")
    echo -e "${GREEN}✅ Prediction successful - Risk Score: $RISK_SCORE${NC}"
else
    echo -e "${RED}❌ Prediction failed${NC}"
fi
echo ""

# Test 3: High risk user
echo "Test 3: High Risk User Prediction"
echo "----------------------------------"
HIGH_RISK_RESPONSE=$(curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "high_risk_user",
    "sessions_last_7_days": 0,
    "sessions_last_30_days": 5,
    "days_since_last_session": 35,
    "avg_session_duration": 30000,
    "unique_screens_last_7_days": 0,
    "crash_count_last_7_days": 3,
    "anr_count_last_7_days": 2,
    "frozen_frame_rate": 0.3
  }')
echo "$HIGH_RISK_RESPONSE" | python3 -m json.tool
if echo "$HIGH_RISK_RESPONSE" | grep -q '"risk_level": "HIGH"'; then
    echo -e "${GREEN}✅ High risk user correctly identified${NC}"
else
    RISK_LEVEL=$(echo "$HIGH_RISK_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('risk_level', 'UNKNOWN'))")
    echo -e "${YELLOW}⚠️  Risk level: $RISK_LEVEL (expected HIGH)${NC}"
fi
echo ""

# Test 4: Low risk user
echo "Test 4: Low Risk User Prediction"
echo "---------------------------------"
LOW_RISK_RESPONSE=$(curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "low_risk_user",
    "sessions_last_7_days": 12,
    "sessions_last_30_days": 45,
    "days_since_last_session": 1,
    "avg_session_duration": 180000,
    "unique_screens_last_7_days": 8,
    "crash_count_last_7_days": 0,
    "anr_count_last_7_days": 0,
    "frozen_frame_rate": 0.01
  }')
echo "$LOW_RISK_RESPONSE" | python3 -m json.tool
if echo "$LOW_RISK_RESPONSE" | grep -q '"risk_level": "LOW"'; then
    echo -e "${GREEN}✅ Low risk user correctly identified${NC}"
else
    RISK_LEVEL=$(echo "$LOW_RISK_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin).get('risk_level', 'UNKNOWN'))")
    echo -e "${YELLOW}⚠️  Risk level: $RISK_LEVEL (expected LOW)${NC}"
fi
echo ""

# Test 5: Batch prediction
echo "Test 5: Batch Prediction"
echo "------------------------"
BATCH_RESPONSE=$(curl -s -X POST http://localhost:8000/predict/batch \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user1",
        "sessions_last_7_days": 2,
        "sessions_last_30_days": 10,
        "days_since_last_session": 8,
        "avg_session_duration": 80000,
        "unique_screens_last_7_days": 3,
        "crash_count_last_7_days": 1,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.1
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
  }')
BATCH_COUNT=$(echo "$BATCH_RESPONSE" | python3 -c "import sys, json; print(len(json.load(sys.stdin).get('predictions', [])))")
echo "Predictions returned: $BATCH_COUNT"
if [ "$BATCH_COUNT" -eq 2 ]; then
    echo -e "${GREEN}✅ Batch prediction successful${NC}"
else
    echo -e "${RED}❌ Batch prediction failed${NC}"
fi
echo ""

# Summary
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "${GREEN}✅ ML Service is running on http://localhost:8000${NC}"
echo -e "${GREEN}✅ All tests completed${NC}"
echo ""
echo "Service is running in the background (PID: $SERVICE_PID)"
echo "View logs: tail -f /tmp/ml_service.log"
echo "Stop service: kill $SERVICE_PID"
echo ""
echo "Next steps:"
echo "1. Test Java backend integration:"
echo "   export CONFIG_SERVICE_APPLICATION_MLSERVICEBASEURL=http://localhost:8000"
echo "   # Then start your Java backend"
echo ""
echo "2. Test API endpoint:"
echo "   curl -X POST http://localhost:8000/predict -H 'Content-Type: application/json' -d @test_user.json"
echo ""
echo "3. View API docs:"
echo "   open http://localhost:8000/docs"
echo ""

