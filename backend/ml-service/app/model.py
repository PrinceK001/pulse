"""
ML Model wrapper for churn prediction
Handles model loading, feature extraction, and prediction
"""
import pickle
import os
import numpy as np
from typing import Dict, Optional, Any
import logging
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler

logger = logging.getLogger(__name__)


class ChurnMLModel:
    """Wrapper for churn prediction ML model"""
    
    def __init__(self, model_path: str = "models/churn_model_v1.pkl"):
        self.model = None
        self.scaler = None
        self.feature_names = None
        self.model_path = model_path
        self.model_version = "v1"
        self.load_model()
    
    def load_model(self):
        """Load trained model and scaler from file"""
        model_file = os.path.join(
            os.path.dirname(os.path.dirname(__file__)),
            self.model_path
        )
        
        if os.path.exists(model_file):
            try:
                with open(model_file, 'rb') as f:
                    model_data = pickle.load(f)
                    self.model = model_data.get('model')
                    self.scaler = model_data.get('scaler')
                    self.feature_names = model_data.get('feature_names')
                    self.model_version = model_data.get('version', 'v1')
                
                logger.info(f"Model loaded successfully from {model_file}")
                logger.info(f"Model type: {type(self.model).__name__}")
            except Exception as e:
                logger.error(f"Error loading model from {model_file}: {str(e)}")
                self._create_fallback_model()
        else:
            logger.warning(f"Model not found at {model_file}, using fallback model")
            self._create_fallback_model()
    
    def extract_features(self, request) -> np.ndarray:
        """
        Extract and normalize features from request
        
        Features:
        1. sessions_last_7_days
        2. sessions_last_30_days
        3. days_since_last_session
        4. avg_session_duration_sec (converted from ms)
        5. unique_screens_last_7_days
        6. crash_count_last_7_days
        7. anr_count_last_7_days
        8. frozen_frame_rate
        9. session_frequency (derived)
        10. engagement_decline (derived)
        11. performance_score (derived)
        """
        # Base features
        sessions_7d = float(request.sessions_last_7_days)
        sessions_30d = float(request.sessions_last_30_days)
        days_since = float(request.days_since_last_session)
        avg_duration_sec = request.avg_session_duration / 1000.0  # Convert to seconds
        unique_screens = float(request.unique_screens_last_7_days)
        crash_count = float(request.crash_count_last_7_days)
        anr_count = float(request.anr_count_last_7_days)
        frozen_rate = float(request.frozen_frame_rate)
        
        # Derived features
        # Session frequency: ratio of 7d sessions to expected based on 30d average
        expected_7d = sessions_30d / 4.0 if sessions_30d > 0 else 0.0
        session_frequency = sessions_7d / max(expected_7d, 1.0)
        
        # Engagement decline: inverse of session frequency (1.0 = complete drop-off)
        engagement_decline = max(0.0, 1.0 - session_frequency) if session_frequency <= 1.0 else 0.0
        
        # Performance score: weighted combination of performance issues
        performance_score = (
            crash_count * 0.5 +
            anr_count * 0.3 +
            frozen_rate * 10.0
        )
        
        # Build feature vector
        features = np.array([
            sessions_7d,
            sessions_30d,
            days_since,
            avg_duration_sec,
            unique_screens,
            crash_count,
            anr_count,
            frozen_rate,
            session_frequency,
            engagement_decline,
            performance_score
        ], dtype=np.float32).reshape(1, -1)
        
        # Handle NaN and Inf values
        features = np.nan_to_num(features, nan=0.0, posinf=1.0, neginf=0.0)
        
        # Scale if scaler available
        if self.scaler is not None:
            try:
                features = self.scaler.transform(features)
            except Exception as e:
                logger.warning(f"Error scaling features: {str(e)}, using unscaled features")
        
        return features
    
    def predict(self, features: np.ndarray) -> float:
        """
        Predict churn probability (0.0-1.0)
        """
        if self.model is not None:
            try:
                # Get probability of positive class (churn)
                if hasattr(self.model, 'predict_proba'):
                    probability = self.model.predict_proba(features)[0][1]
                else:
                    # Fallback to binary prediction
                    prediction = self.model.predict(features)[0]
                    probability = float(prediction)
                
                # Ensure probability is in valid range
                probability = max(0.0, min(1.0, float(probability)))
                return probability
            except Exception as e:
                logger.error(f"Error in model prediction: {str(e)}")
                return self._fallback_prediction(features)
        else:
            return self._fallback_prediction(features)
    
    def get_feature_importance(self, features: np.ndarray) -> Optional[Dict[str, float]]:
        """
        Get feature importance for this prediction
        Returns dict mapping feature names to importance scores
        """
        if self.model is None:
            return None
        
        try:
            if hasattr(self.model, 'feature_importances_'):
                importances = self.model.feature_importances_
                feature_names = self.feature_names or [
                    "sessions_7d", "sessions_30d", "days_since", "avg_duration",
                    "unique_screens", "crash_count", "anr_count", "frozen_rate",
                    "session_frequency", "engagement_decline", "performance_score"
                ]
                
                # Create dict and sort by importance
                importance_dict = dict(zip(feature_names, importances.tolist()))
                return dict(sorted(importance_dict.items(), key=lambda x: x[1], reverse=True))
        except Exception as e:
            logger.warning(f"Error getting feature importance: {str(e)}")
        
        return None
    
    def is_loaded(self) -> bool:
        """Check if model is loaded"""
        return self.model is not None
    
    def get_model_version(self) -> str:
        """Get model version"""
        return self.model_version
    
    def _create_fallback_model(self):
        """Create a simple fallback model if trained model not available"""
        logger.info("Creating fallback logistic regression model")
        
        try:
            # Create a simple logistic regression model
            self.model = LogisticRegression(random_state=42, max_iter=1000)
            
            # Train on synthetic data that represents common patterns
            # This is just a placeholder - real model should be trained on actual data
            X_dummy = np.array([
                # Low risk patterns
                [10, 40, 1, 120, 5, 0, 0, 0.05, 1.0, 0.0, 0.5],
                [8, 35, 2, 100, 4, 0, 0, 0.03, 0.9, 0.1, 0.3],
                [12, 45, 0, 150, 6, 0, 0, 0.02, 1.1, 0.0, 0.2],
                # Medium risk patterns
                [3, 20, 5, 80, 2, 1, 0, 0.1, 0.6, 0.4, 1.0],
                [2, 15, 7, 60, 1, 0, 1, 0.15, 0.5, 0.5, 1.2],
                # High risk patterns
                [0, 10, 15, 30, 0, 2, 1, 0.25, 0.0, 1.0, 3.0],
                [0, 5, 30, 20, 0, 3, 2, 0.3, 0.0, 1.0, 4.5],
            ] * 10)  # Repeat to have enough samples
            
            y_dummy = np.array([
                0, 0, 0,  # Low risk
                0, 0,     # Medium risk (some churn)
                1, 1      # High risk (churn)
            ] * 10)
            
            self.model.fit(X_dummy, y_dummy)
            
            # Create a simple scaler
            self.scaler = StandardScaler()
            self.scaler.fit(X_dummy)
            
            self.feature_names = [
                "sessions_7d", "sessions_30d", "days_since", "avg_duration",
                "unique_screens", "crash_count", "anr_count", "frozen_rate",
                "session_frequency", "engagement_decline", "performance_score"
            ]
            
            logger.info("Fallback model created successfully")
        except Exception as e:
            logger.error(f"Error creating fallback model: {str(e)}")
            self.model = None
            self.scaler = None
    
    def _fallback_prediction(self, features: np.ndarray) -> float:
        """
        Simple heuristic-based fallback prediction
        Used when model is not available or prediction fails
        """
        try:
            days_since = features[0][2]
            sessions_7d = features[0][0]
            engagement_decline = features[0][9]
            performance_score = features[0][10]
            
            # Heuristic scoring
            score = 0.0
            
            # Days since last session (0-0.4)
            if days_since >= 30:
                score += 0.4
            elif days_since >= 14:
                score += 0.3
            elif days_since >= 7:
                score += 0.2
            elif days_since >= 3:
                score += 0.1
            
            # Engagement decline (0-0.3)
            score += engagement_decline * 0.3
            
            # Performance issues (0-0.2)
            score += min(performance_score / 10.0, 0.2)
            
            # Zero sessions (0-0.1)
            if sessions_7d == 0 and days_since < 7:
                score += 0.1
            
            return min(score, 1.0)
        except Exception as e:
            logger.error(f"Error in fallback prediction: {str(e)}")
            return 0.5  # Default to medium risk

