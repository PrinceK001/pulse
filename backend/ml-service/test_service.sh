#!/bin/bash

# Test script for ML service

echo "Testing Churn Prediction ML Service..."
echo ""

# Test health endpoint
echo "1. Testing health endpoint..."
curl -s http://localhost:8000/health | python3 -m json.tool
echo ""

# Test prediction endpoint
echo "2. Testing prediction endpoint..."
curl -s -X POST http://localhost:8000/predict \
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
  }' | python3 -m json.tool

echo ""
echo "Test complete!"

