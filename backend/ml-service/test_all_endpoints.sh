#!/bin/bash

# Comprehensive test script for ML service endpoints
# Tests all endpoints and shows outputs

set -e

ML_SERVICE_URL="http://localhost:8000"
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=========================================="
echo "ML Service Comprehensive Test"
echo "=========================================="
echo ""

# Check if service is running
echo -e "${BLUE}Checking if ML service is running...${NC}"
if ! curl -s -f "$ML_SERVICE_URL/health" > /dev/null 2>&1; then
    echo -e "${RED}❌ ML service is not running at $ML_SERVICE_URL${NC}"
    echo -e "${YELLOW}Please start it first: cd backend/ml-service && ./run_and_test.sh${NC}"
    exit 1
fi
echo -e "${GREEN}✅ ML service is running${NC}"
echo ""

# Test 1: Health Check
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 1: Health Check${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s "$ML_SERVICE_URL/health" | python3 -m json.tool
echo ""
echo ""

# Test 2: Single Prediction - High Risk User
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 2: Single Prediction - High Risk User${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "high_risk_user_001",
    "sessions_last_7_days": 0,
    "sessions_last_30_days": 5,
    "days_since_last_session": 35,
    "avg_session_duration": 30000,
    "unique_screens_last_7_days": 0,
    "crash_count_last_7_days": 3,
    "anr_count_last_7_days": 2,
    "frozen_frame_rate": 0.3
  }' | python3 -m json.tool
echo ""
echo ""

# Test 3: Single Prediction - Low Risk User
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 3: Single Prediction - Low Risk User${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/predict" \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "low_risk_user_001",
    "sessions_last_7_days": 12,
    "sessions_last_30_days": 45,
    "days_since_last_session": 1,
    "avg_session_duration": 180000,
    "unique_screens_last_7_days": 8,
    "crash_count_last_7_days": 0,
    "anr_count_last_7_days": 0,
    "frozen_frame_rate": 0.01
  }' | python3 -m json.tool
echo ""
echo ""

# Test 4: Batch Prediction
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 4: Batch Prediction (5 users)${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/predict/batch" \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user_001",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 5,
        "days_since_last_session": 35,
        "avg_session_duration": 30000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.3
      },
      {
        "user_id": "user_002",
        "sessions_last_7_days": 2,
        "sessions_last_30_days": 15,
        "days_since_last_session": 5,
        "avg_session_duration": 120000,
        "unique_screens_last_7_days": 3,
        "crash_count_last_7_days": 1,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.1
      },
      {
        "user_id": "user_003",
        "sessions_last_7_days": 12,
        "sessions_last_30_days": 45,
        "days_since_last_session": 1,
        "avg_session_duration": 180000,
        "unique_screens_last_7_days": 8,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.01
      },
      {
        "user_id": "user_004",
        "sessions_last_7_days": 1,
        "sessions_last_30_days": 8,
        "days_since_last_session": 10,
        "avg_session_duration": 60000,
        "unique_screens_last_7_days": 2,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.2
      },
      {
        "user_id": "user_005",
        "sessions_last_7_days": 8,
        "sessions_last_30_days": 30,
        "days_since_last_session": 2,
        "avg_session_duration": 150000,
        "unique_screens_last_7_days": 5,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.05
      }
    ]
  }' | python3 -m json.tool
echo ""
echo ""

# Test 5: Pattern Discovery (ML)
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 5: Pattern Discovery (ML Clustering)${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/analyze/patterns" \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user_001",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 5,
        "days_since_last_session": 35,
        "avg_session_duration": 30000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.3
      },
      {
        "user_id": "user_002",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 8,
        "days_since_last_session": 30,
        "avg_session_duration": 25000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.25
      },
      {
        "user_id": "user_003",
        "sessions_last_7_days": 12,
        "sessions_last_30_days": 45,
        "days_since_last_session": 1,
        "avg_session_duration": 180000,
        "unique_screens_last_7_days": 8,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.01
      },
      {
        "user_id": "user_004",
        "sessions_last_7_days": 10,
        "sessions_last_30_days": 40,
        "days_since_last_session": 1,
        "avg_session_duration": 160000,
        "unique_screens_last_7_days": 7,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.02
      },
      {
        "user_id": "user_005",
        "sessions_last_7_days": 1,
        "sessions_last_30_days": 8,
        "days_since_last_session": 10,
        "avg_session_duration": 60000,
        "unique_screens_last_7_days": 2,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.2
      },
      {
        "user_id": "user_006",
        "sessions_last_7_days": 1,
        "sessions_last_30_days": 10,
        "days_since_last_session": 8,
        "avg_session_duration": 70000,
        "unique_screens_last_7_days": 2,
        "crash_count_last_7_days": 1,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.15
      },
      {
        "user_id": "user_007",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 3,
        "days_since_last_session": 40,
        "avg_session_duration": 20000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 4,
        "anr_count_last_7_days": 3,
        "frozen_frame_rate": 0.35
      },
      {
        "user_id": "user_008",
        "sessions_last_7_days": 9,
        "sessions_last_30_days": 35,
        "days_since_last_session": 1,
        "avg_session_duration": 170000,
        "unique_screens_last_7_days": 6,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.03
      },
      {
        "user_id": "user_009",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 6,
        "days_since_last_session": 28,
        "avg_session_duration": 35000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.28
      },
      {
        "user_id": "user_010",
        "sessions_last_7_days": 11,
        "sessions_last_30_days": 42,
        "days_since_last_session": 0,
        "avg_session_duration": 190000,
        "unique_screens_last_7_days": 9,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.01
      }
    ]
  }' | python3 -m json.tool
echo ""
echo ""

# Test 6: Root Cause Analysis (ML)
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 6: Root Cause Analysis (ML Feature Importance)${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/analyze/root-causes" \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user_001",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 5,
        "days_since_last_session": 35,
        "avg_session_duration": 30000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.3
      },
      {
        "user_id": "user_002",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 8,
        "days_since_last_session": 30,
        "avg_session_duration": 25000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.25
      },
      {
        "user_id": "user_003",
        "sessions_last_7_days": 12,
        "sessions_last_30_days": 45,
        "days_since_last_session": 1,
        "avg_session_duration": 180000,
        "unique_screens_last_7_days": 8,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.01
      },
      {
        "user_id": "user_004",
        "sessions_last_7_days": 1,
        "sessions_last_30_days": 8,
        "days_since_last_session": 10,
        "avg_session_duration": 60000,
        "unique_screens_last_7_days": 2,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.2
      },
      {
        "user_id": "user_005",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 3,
        "days_since_last_session": 40,
        "avg_session_duration": 20000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 4,
        "anr_count_last_7_days": 3,
        "frozen_frame_rate": 0.35
      }
    ]
  }' | python3 -m json.tool
echo ""
echo ""

# Test 7: Trend Analysis (ML)
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 7: Trend Analysis (ML Time Series)${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/analyze/trends" \
  -H "Content-Type: application/json" \
  -d '{
    "current_users": [
      {
        "user_id": "current_001",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 5,
        "days_since_last_session": 35,
        "avg_session_duration": 30000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.3
      },
      {
        "user_id": "current_002",
        "sessions_last_7_days": 1,
        "sessions_last_30_days": 8,
        "days_since_last_session": 10,
        "avg_session_duration": 60000,
        "unique_screens_last_7_days": 2,
        "crash_count_last_7_days": 2,
        "anr_count_last_7_days": 1,
        "frozen_frame_rate": 0.2
      },
      {
        "user_id": "current_003",
        "sessions_last_7_days": 2,
        "sessions_last_30_days": 15,
        "days_since_last_session": 5,
        "avg_session_duration": 120000,
        "unique_screens_last_7_days": 3,
        "crash_count_last_7_days": 1,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.1
      }
    ],
    "historical_users": [
      {
        "user_id": "historical_001",
        "sessions_last_7_days": 2,
        "sessions_last_30_days": 15,
        "days_since_last_session": 3,
        "avg_session_duration": 130000,
        "unique_screens_last_7_days": 4,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.05
      },
      {
        "user_id": "historical_002",
        "sessions_last_7_days": 3,
        "sessions_last_30_days": 18,
        "days_since_last_session": 2,
        "avg_session_duration": 140000,
        "unique_screens_last_7_days": 5,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.03
      },
      {
        "user_id": "historical_003",
        "sessions_last_7_days": 4,
        "sessions_last_30_days": 20,
        "days_since_last_session": 1,
        "avg_session_duration": 150000,
        "unique_screens_last_7_days": 6,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.02
      }
    ]
  }' | python3 -m json.tool
echo ""
echo ""

# Test 8: Anomaly Detection (ML)
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}Test 8: Anomaly Detection (ML Isolation Forest)${NC}"
echo -e "${BLUE}========================================${NC}"
curl -s -X POST "$ML_SERVICE_URL/analyze/anomalies" \
  -H "Content-Type: application/json" \
  -d '{
    "users": [
      {
        "user_id": "user_001",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 5,
        "days_since_last_session": 35,
        "avg_session_duration": 30000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 3,
        "anr_count_last_7_days": 2,
        "frozen_frame_rate": 0.3
      },
      {
        "user_id": "user_002",
        "sessions_last_7_days": 12,
        "sessions_last_30_days": 45,
        "days_since_last_session": 1,
        "avg_session_duration": 180000,
        "unique_screens_last_7_days": 8,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.01
      },
      {
        "user_id": "user_003",
        "sessions_last_7_days": 8,
        "sessions_last_30_days": 30,
        "days_since_last_session": 2,
        "avg_session_duration": 150000,
        "unique_screens_last_7_days": 5,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.05
      },
      {
        "user_id": "user_004",
        "sessions_last_7_days": 10,
        "sessions_last_30_days": 40,
        "days_since_last_session": 1,
        "avg_session_duration": 160000,
        "unique_screens_last_7_days": 7,
        "crash_count_last_7_days": 0,
        "anr_count_last_7_days": 0,
        "frozen_frame_rate": 0.02
      },
      {
        "user_id": "user_005",
        "sessions_last_7_days": 0,
        "sessions_last_30_days": 3,
        "days_since_last_session": 40,
        "avg_session_duration": 20000,
        "unique_screens_last_7_days": 0,
        "crash_count_last_7_days": 4,
        "anr_count_last_7_days": 3,
        "frozen_frame_rate": 0.35
      }
    ]
  }' | python3 -m json.tool
echo ""
echo ""

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}✅ All Tests Completed!${NC}"
echo -e "${GREEN}========================================${NC}"

