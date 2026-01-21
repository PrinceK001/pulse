"""
FastAPI service for churn prediction using ML models
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Dict
import numpy as np
import logging
from app.model import ChurnMLModel

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Churn Prediction ML Service",
    description="ML service for predicting user churn risk",
    version="1.0.0"
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize model
model = ChurnMLModel()


class ChurnPredictionRequest(BaseModel):
    """Request model for churn prediction"""
    user_id: str = Field(..., description="User identifier")
    sessions_last_7_days: int = Field(..., ge=0, description="Number of sessions in last 7 days")
    sessions_last_30_days: int = Field(..., ge=0, description="Number of sessions in last 30 days")
    days_since_last_session: int = Field(..., ge=0, description="Days since last session")
    avg_session_duration: int = Field(..., ge=0, description="Average session duration in milliseconds")
    unique_screens_last_7_days: int = Field(..., ge=0, description="Unique screens visited in last 7 days")
    crash_count_last_7_days: int = Field(..., ge=0, description="Number of crashes in last 7 days")
    anr_count_last_7_days: int = Field(..., ge=0, description="Number of ANRs in last 7 days")
    frozen_frame_rate: float = Field(..., ge=0.0, le=1.0, description="Frozen frame rate (0.0-1.0)")
    device_model: Optional[str] = Field(None, description="Device model")
    os_version: Optional[str] = Field(None, description="OS version")
    app_version: Optional[str] = Field(None, description="App version")


class ChurnPredictionResponse(BaseModel):
    """Response model for churn prediction"""
    user_id: str
    risk_score: int = Field(..., ge=0, le=100, description="Churn risk score (0-100)")
    churn_probability: float = Field(..., ge=0.0, le=1.0, description="Churn probability (0.0-1.0)")
    risk_level: str = Field(..., description="Risk level: HIGH, MEDIUM, or LOW")
    feature_importance: Optional[Dict[str, float]] = Field(None, description="Feature importance scores")


class BatchPredictionRequest(BaseModel):
    """Request model for batch predictions"""
    users: List[ChurnPredictionRequest]


class BatchPredictionResponse(BaseModel):
    """Response model for batch predictions"""
    predictions: List[ChurnPredictionResponse]


@app.post("/predict", response_model=ChurnPredictionResponse)
async def predict_churn(request: ChurnPredictionRequest):
    """
    Predict churn risk for a single user
    """
    try:
        logger.info(f"Predicting churn for user: {request.user_id}")
        
        # Extract features and predict
        features = model.extract_features(request)
        probability = model.predict(features)
        risk_score = int(probability * 100)
        
        # Determine risk level
        if risk_score >= 70:
            risk_level = "HIGH"
        elif risk_score >= 40:
            risk_level = "MEDIUM"
        else:
            risk_level = "LOW"
        
        # Get feature importance (optional)
        importance = model.get_feature_importance(features)
        
        return ChurnPredictionResponse(
            user_id=request.user_id,
            risk_score=risk_score,
            churn_probability=float(probability),
            risk_level=risk_level,
            feature_importance=importance
        )
    except Exception as e:
        logger.error(f"Error predicting churn for user {request.user_id}: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Prediction error: {str(e)}")


@app.post("/predict/batch", response_model=BatchPredictionResponse)
async def predict_churn_batch(request: BatchPredictionRequest):
    """
    Predict churn risk for multiple users
    """
    try:
        logger.info(f"Predicting churn for {len(request.users)} users")
        
        predictions = []
        for user_request in request.users:
            features = model.extract_features(user_request)
            probability = model.predict(features)
            risk_score = int(probability * 100)
            
            if risk_score >= 70:
                risk_level = "HIGH"
            elif risk_score >= 40:
                risk_level = "MEDIUM"
            else:
                risk_level = "LOW"
            
            predictions.append(ChurnPredictionResponse(
                user_id=user_request.user_id,
                risk_score=risk_score,
                churn_probability=float(probability),
                risk_level=risk_level,
                feature_importance=None  # Skip for batch to improve performance
            ))
        
        return BatchPredictionResponse(predictions=predictions)
    except Exception as e:
        logger.error(f"Error in batch prediction: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Batch prediction error: {str(e)}")


@app.get("/health")
async def health():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "model_loaded": model.is_loaded(),
        "model_version": model.get_model_version()
    }


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Churn Prediction ML Service",
        "version": "1.0.0",
        "endpoints": {
            "predict": "/predict",
            "batch_predict": "/predict/batch",
            "health": "/health"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

