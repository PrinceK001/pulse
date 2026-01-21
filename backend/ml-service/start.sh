#!/bin/bash

# Start ML service
# This script starts the FastAPI service for churn prediction

set -e

echo "Starting Churn Prediction ML Service..."

# Check if model exists, if not train one
if [ ! -f "models/churn_model_v1.pkl" ]; then
    echo "Model not found. Training model..."
    python training/train_model.py --samples 2000
fi

# Start the service
echo "Starting FastAPI service on port 8000..."
uvicorn app.main:app --host 0.0.0.0 --port 8000

