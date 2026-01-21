"""
FastAPI service for churn prediction using ML models
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
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


class PatternDiscoveryRequest(BaseModel):
    """Request for pattern discovery"""
    users: List[ChurnPredictionRequest]


class PatternDiscoveryResponse(BaseModel):
    """Response for pattern discovery"""
    patterns: List[Dict[str, Any]]


class RootCauseAnalysisRequest(BaseModel):
    """Request for root cause analysis"""
    users: List[ChurnPredictionRequest]


class RootCauseAnalysisResponse(BaseModel):
    """Response for root cause analysis"""
    root_causes: List[Dict[str, Any]]
    aggregate_importance: Dict[str, float]
    correlations: Dict[str, float]


class TrendAnalysisRequest(BaseModel):
    """Request for trend analysis"""
    users: List[ChurnPredictionRequest]
    historical_users: Optional[List[ChurnPredictionRequest]] = None


class TrendAnalysisResponse(BaseModel):
    """Response for trend analysis"""
    trend_direction: str
    trend_strength: float
    statistical_significance: bool
    deviation_from_expected: float
    is_anomaly: bool
    current_mean: float
    expected_value: Optional[float] = None
    p_value: Optional[float] = None


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
    Predict churn risk for multiple users (optimized batch processing)
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
            
            # Optionally include feature importance (can be disabled for performance)
            importance = None
            if len(request.users) <= 100:  # Only for small batches
                importance = model.get_feature_importance(features)
            
            predictions.append(ChurnPredictionResponse(
                user_id=user_request.user_id,
                risk_score=risk_score,
                churn_probability=float(probability),
                risk_level=risk_level,
                feature_importance=importance
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


@app.post("/analyze/patterns", response_model=PatternDiscoveryResponse)
async def discover_patterns(request: PatternDiscoveryRequest):
    """
    ML-based pattern discovery using clustering
    Automatically discovers common churn patterns
    """
    try:
        logger.info(f"Discovering patterns for {len(request.users)} users")
        
        # Extract features and predictions
        features_list = []
        predictions = []
        
        for user_request in request.users:
            features = model.extract_features(user_request)
            probability = model.predict(features)
            features_list.append(features[0])
            predictions.append(probability)
        
        features_array = np.array(features_list)
        predictions_array = np.array(predictions)
        
        # Discover patterns
        patterns = model.discover_churn_patterns(features_array, predictions_array)
        
        return PatternDiscoveryResponse(patterns=patterns)
        
    except Exception as e:
        logger.error(f"Error in pattern discovery: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Pattern discovery error: {str(e)}")


@app.post("/analyze/root-causes", response_model=RootCauseAnalysisResponse)
async def analyze_root_causes(request: RootCauseAnalysisRequest):
    """
    ML-based root cause analysis using feature importance
    Identifies which features contribute most to churn risk
    """
    try:
        logger.info(f"Analyzing root causes for {len(request.users)} users")
        
        # Extract features and predictions
        features_list = []
        predictions = []
        
        for user_request in request.users:
            features = model.extract_features(user_request)
            probability = model.predict(features)
            features_list.append(features[0])
            predictions.append(probability)
        
        features_array = np.array(features_list)
        predictions_array = np.array(predictions)
        
        # Analyze root causes
        analysis = model.analyze_root_causes(features_array, predictions_array)
        
        return RootCauseAnalysisResponse(
            root_causes=analysis.get('root_causes', []),
            aggregate_importance=analysis.get('aggregate_importance', {}),
            correlations=analysis.get('correlations', {})
        )
        
    except Exception as e:
        logger.error(f"Error in root cause analysis: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Root cause analysis error: {str(e)}")


@app.post("/analyze/trends", response_model=TrendAnalysisResponse)
async def analyze_trends(request: TrendAnalysisRequest):
    """
    ML-based trend analysis using time series regression
    Detects trends and anomalies in churn patterns
    """
    try:
        logger.info(f"Analyzing trends: {len(request.users)} current users")
        
        # Extract current predictions
        current_predictions = []
        for user_request in request.users:
            features = model.extract_features(user_request)
            probability = model.predict(features)
            current_predictions.append(probability)
        
        current_array = np.array(current_predictions)
        
        # Extract historical predictions if provided
        historical_array = None
        if request.historical_users and len(request.historical_users) > 0:
            logger.info(f"Using {len(request.historical_users)} historical users for trend analysis")
            historical_predictions = []
            for user_request in request.historical_users:
                features = model.extract_features(user_request)
                probability = model.predict(features)
                historical_predictions.append(probability)
            historical_array = np.array(historical_predictions)
        
        # Analyze trends
        if historical_array is not None:
            trend_analysis = model.analyze_trends(current_array, historical_array)
        else:
            # Just detect anomalies in current data
            anomaly_analysis = model.detect_anomalies(current_array)
            trend_analysis = {
                'trend_direction': 'unknown',
                'trend_strength': 0.0,
                'statistical_significance': False,
                'deviation_from_expected': 0.0,
                'is_anomaly': anomaly_analysis.get('is_spike', False),
                'current_mean': anomaly_analysis.get('current_mean', 0.0),
                'expected_value': None,
                'p_value': None
            }
        
        return TrendAnalysisResponse(**trend_analysis)
        
    except Exception as e:
        logger.error(f"Error in trend analysis: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Trend analysis error: {str(e)}")


@app.post("/analyze/anomalies")
async def detect_anomalies(request: BatchPredictionRequest):
    """
    ML-based anomaly detection using Isolation Forest
    Detects unusual patterns in churn predictions
    """
    try:
        logger.info(f"Detecting anomalies for {len(request.users)} users")
        
        # Extract features and predictions
        features_list = []
        predictions = []
        
        for user_request in request.users:
            features = model.extract_features(user_request)
            probability = model.predict(features)
            features_list.append(features[0])
            predictions.append(probability)
        
        predictions_array = np.array(predictions)
        
        # Detect anomalies
        anomaly_analysis = model.detect_anomalies(predictions_array)
        
        return anomaly_analysis
        
    except Exception as e:
        logger.error(f"Error in anomaly detection: {str(e)}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Anomaly detection error: {str(e)}")


@app.get("/")
async def root():
    """Root endpoint"""
    return {
        "service": "Churn Prediction ML Service",
        "version": "1.0.0",
        "endpoints": {
            "predict": "/predict",
            "batch_predict": "/predict/batch",
            "analyze_patterns": "/analyze/patterns",
            "analyze_root_causes": "/analyze/root-causes",
            "analyze_trends": "/analyze/trends",
            "detect_anomalies": "/analyze/anomalies",
            "health": "/health"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)

