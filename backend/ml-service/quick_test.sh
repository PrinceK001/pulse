#!/bin/bash

echo "=== Churn Prediction ML Service - Quick Test ==="
echo ""

# Check if service is running
echo "1. Checking if ML service is running..."
if curl -s http://localhost:8000/health > /dev/null; then
    echo "✅ ML service is running"
    curl -s http://localhost:8000/health | python3 -m json.tool
else
    echo "❌ ML service is NOT running"
    echo "   Start it with: uvicorn app.main:app --host 0.0.0.0 --port 8000"
    exit 1
fi

echo ""
echo "2. Testing single prediction (medium risk user)..."
curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d @test_user.json | python3 -m json.tool

echo ""
echo "3. Testing high risk user..."
curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d @test_high_risk_user.json | python3 -m json.tool

echo ""
echo "4. Testing low risk user..."
curl -s -X POST http://localhost:8000/predict \
  -H "Content-Type: application/json" \
  -d @test_low_risk_user.json | python3 -m json.tool

echo ""
echo "=== Test Complete ==="
