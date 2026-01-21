#!/usr/bin/env python3
"""
Quick test script to demonstrate ML model outputs
"""
import sys
import json
from app.model import ChurnMLModel
from app.main import ChurnPredictionRequest

# Initialize model
print("=" * 60)
print("ML Model Test - Direct Model Output")
print("=" * 60)
print()

model = ChurnMLModel()
print(f"✅ Model loaded: {model.is_loaded()}")
print(f"✅ Model version: {model.get_model_version()}")
print()

# Test 1: High Risk User
print("=" * 60)
print("Test 1: High Risk User Prediction")
print("=" * 60)

high_risk_request = ChurnPredictionRequest(
    user_id="high_risk_001",
    sessions_last_7_days=0,
    sessions_last_30_days=5,
    days_since_last_session=35,
    avg_session_duration=30000,
    unique_screens_last_7_days=0,
    crash_count_last_7_days=3,
    anr_count_last_7_days=2,
    frozen_frame_rate=0.3
)

features = model.extract_features(high_risk_request)
probability = model.predict(features)
risk_score = int(probability * 100)
risk_level = "HIGH" if risk_score >= 70 else "MEDIUM" if risk_score >= 40 else "LOW"
importance = model.get_feature_importance(features)

print(f"User ID: {high_risk_request.user_id}")
print(f"Risk Score: {risk_score}/100")
print(f"Churn Probability: {probability:.4f} ({probability*100:.2f}%)")
print(f"Risk Level: {risk_level}")
print()
print("Feature Importance:")
if importance:
    for feature, imp in list(importance.items())[:5]:
        print(f"  {feature}: {imp:.4f}")
print()

# Test 2: Low Risk User
print("=" * 60)
print("Test 2: Low Risk User Prediction")
print("=" * 60)

low_risk_request = ChurnPredictionRequest(
    user_id="low_risk_001",
    sessions_last_7_days=12,
    sessions_last_30_days=45,
    days_since_last_session=1,
    avg_session_duration=180000,
    unique_screens_last_7_days=8,
    crash_count_last_7_days=0,
    anr_count_last_7_days=0,
    frozen_frame_rate=0.01
)

features = model.extract_features(low_risk_request)
probability = model.predict(features)
risk_score = int(probability * 100)
risk_level = "HIGH" if risk_score >= 70 else "MEDIUM" if risk_score >= 40 else "LOW"
importance = model.get_feature_importance(features)

print(f"User ID: {low_risk_request.user_id}")
print(f"Risk Score: {risk_score}/100")
print(f"Churn Probability: {probability:.4f} ({probability*100:.2f}%)")
print(f"Risk Level: {risk_level}")
print()
print("Feature Importance:")
if importance:
    for feature, imp in list(importance.items())[:5]:
        print(f"  {feature}: {imp:.4f}")
print()

# Test 3: Batch Prediction Simulation
print("=" * 60)
print("Test 3: Batch Prediction (5 users)")
print("=" * 60)

users = [
    high_risk_request,
    low_risk_request,
    ChurnPredictionRequest(
        user_id="medium_risk_001",
        sessions_last_7_days=2,
        sessions_last_30_days=15,
        days_since_last_session=5,
        avg_session_duration=120000,
        unique_screens_last_7_days=3,
        crash_count_last_7_days=1,
        anr_count_last_7_days=0,
        frozen_frame_rate=0.1
    ),
    ChurnPredictionRequest(
        user_id="medium_risk_002",
        sessions_last_7_days=1,
        sessions_last_30_days=8,
        days_since_last_session=10,
        avg_session_duration=60000,
        unique_screens_last_7_days=2,
        crash_count_last_7_days=2,
        anr_count_last_7_days=1,
        frozen_frame_rate=0.2
    ),
    ChurnPredictionRequest(
        user_id="low_risk_002",
        sessions_last_7_days=8,
        sessions_last_30_days=30,
        days_since_last_session=2,
        avg_session_duration=150000,
        unique_screens_last_7_days=5,
        crash_count_last_7_days=0,
        anr_count_last_7_days=0,
        frozen_frame_rate=0.05
    ),
]

predictions = []
for user in users:
    features = model.extract_features(user)
    probability = model.predict(features)
    risk_score = int(probability * 100)
    risk_level = "HIGH" if risk_score >= 70 else "MEDIUM" if risk_score >= 40 else "LOW"
    predictions.append({
        "user_id": user.user_id,
        "risk_score": risk_score,
        "churn_probability": float(probability),
        "risk_level": risk_level
    })

print("Batch Predictions:")
for pred in predictions:
    print(f"  {pred['user_id']}: Risk={pred['risk_score']}, Level={pred['risk_level']}, Prob={pred['churn_probability']:.4f}")
print()

# Summary
high_count = sum(1 for p in predictions if p['risk_level'] == 'HIGH')
medium_count = sum(1 for p in predictions if p['risk_level'] == 'MEDIUM')
low_count = sum(1 for p in predictions if p['risk_level'] == 'LOW')
avg_risk = sum(p['risk_score'] for p in predictions) / len(predictions)

print("=" * 60)
print("Summary")
print("=" * 60)
print(f"Total Users: {len(predictions)}")
print(f"High Risk: {high_count}")
print(f"Medium Risk: {medium_count}")
print(f"Low Risk: {low_count}")
print(f"Average Risk Score: {avg_risk:.1f}")
print()

print("✅ All tests completed successfully!")

