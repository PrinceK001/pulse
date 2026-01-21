"""
ML Model wrapper for churn prediction
Handles model loading, feature extraction, and prediction
Enhanced with SHAP, pattern discovery, and anomaly detection
"""
import pickle
import os
import numpy as np
from typing import Dict, Optional, Any, List, Tuple
import logging
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import DBSCAN
from sklearn.ensemble import IsolationForest
from scipy import stats

logger = logging.getLogger(__name__)

# Try to import SHAP, but make it optional
try:
    import shap
    SHAP_AVAILABLE = True
except ImportError:
    SHAP_AVAILABLE = False
    logger.warning("SHAP not available. Install with: pip install shap")


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
    
    def get_shap_values(self, features: np.ndarray) -> Optional[np.ndarray]:
        """
        Get SHAP values for explainability
        Shows how each feature contributes to THIS specific prediction
        """
        if not SHAP_AVAILABLE or self.model is None:
            return None
        
        try:
            # Only works with tree-based models (XGBoost, RandomForest, etc.)
            if hasattr(self.model, 'predict_proba'):
                explainer = shap.TreeExplainer(self.model)
                shap_values = explainer.shap_values(features)
                # Return absolute values for easier interpretation
                return np.abs(shap_values)
        except Exception as e:
            logger.warning(f"Error getting SHAP values: {str(e)}")
        
        return None
    
    def discover_churn_patterns(
        self, 
        features_list: np.ndarray, 
        predictions: np.ndarray
    ) -> List[Dict[str, Any]]:
        """
        Use ML clustering to discover patterns automatically
        Instead of rule-based pattern identification
        """
        if len(features_list) < 10:
            logger.warning("Not enough samples for pattern discovery")
            return []
        
        try:
            # Combine features + predictions for clustering
            predictions_reshaped = predictions.reshape(-1, 1)
            X = np.hstack([features_list, predictions_reshaped])
            
            # Scale features for clustering
            scaler = StandardScaler()
            X_scaled = scaler.fit_transform(X)
            
            # DBSCAN finds patterns automatically (density-based clustering)
            # eps: maximum distance between samples in same cluster
            # min_samples: minimum samples in a cluster
            clusters = DBSCAN(eps=0.5, min_samples=max(5, len(X) // 20)).fit(X_scaled)
            
            # Analyze each cluster
            patterns = []
            unique_labels = set(clusters.labels_)
            
            for cluster_id in unique_labels:
                if cluster_id == -1:  # Noise points
                    continue
                
                cluster_mask = clusters.labels_ == cluster_id
                cluster_features = features_list[cluster_mask]
                cluster_predictions = predictions[cluster_mask]
                
                # Characterize this pattern
                pattern = {
                    'pattern_id': int(cluster_id),
                    'user_count': int(cluster_mask.sum()),
                    'avg_risk_score': float(cluster_predictions.mean() * 100),
                    'avg_churn_probability': float(cluster_predictions.mean()),
                    'characteristics': self._characterize_cluster(cluster_features)
                }
                patterns.append(pattern)
            
            # Sort by user count (most common patterns first)
            patterns.sort(key=lambda x: x['user_count'], reverse=True)
            
            return patterns[:10]  # Return top 10 patterns
            
        except Exception as e:
            logger.error(f"Error in pattern discovery: {str(e)}", exc_info=True)
            return []
    
    def _characterize_cluster(self, cluster_features: np.ndarray) -> Dict[str, Any]:
        """
        Characterize a cluster by analyzing feature averages
        """
        feature_names = self.feature_names or [
            "sessions_7d", "sessions_30d", "days_since", "avg_duration",
            "unique_screens", "crash_count", "anr_count", "frozen_rate",
            "session_frequency", "engagement_decline", "performance_score"
        ]
        
        avg_features = cluster_features.mean(axis=0)
        
        characteristics = {}
        for i, name in enumerate(feature_names):
            if i < len(avg_features):
                characteristics[name] = float(avg_features[i])
        
        # Identify key characteristics
        key_characteristics = []
        if characteristics.get('days_since', 0) > 14:
            key_characteristics.append('inactive')
        if characteristics.get('crash_count', 0) >= 2:
            key_characteristics.append('high_crashes')
        if characteristics.get('sessions_7d', 0) == 0:
            key_characteristics.append('no_sessions')
        if characteristics.get('frozen_rate', 0) > 0.2:
            key_characteristics.append('high_frozen_frames')
        if characteristics.get('engagement_decline', 0) > 0.5:
            key_characteristics.append('declining_engagement')
        
        characteristics['key_indicators'] = key_characteristics
        return characteristics
    
    def detect_anomalies(
        self, 
        current_predictions: np.ndarray, 
        historical_predictions: Optional[np.ndarray] = None
    ) -> Dict[str, Any]:
        """
        Use ML to detect anomalies in churn patterns
        Instead of static thresholds
        """
        try:
            if historical_predictions is not None and len(historical_predictions) > 0:
                # Compare current vs historical
                X = np.vstack([
                    current_predictions.reshape(-1, 1),
                    historical_predictions.reshape(-1, 1)
                ])
            else:
                # Just analyze current distribution
                X = current_predictions.reshape(-1, 1)
            
            # Isolation Forest detects anomalies
            contamination = min(0.1, max(0.01, 10.0 / len(X)))  # 1-10% contamination
            iso_forest = IsolationForest(contamination=contamination, random_state=42)
            anomaly_labels = iso_forest.fit_predict(X)
            
            # Calculate statistics
            current_mean = float(current_predictions.mean())
            current_std = float(current_predictions.std())
            
            anomaly_count = int((anomaly_labels == -1).sum())
            anomaly_percentage = float(anomaly_count / len(anomaly_labels) * 100)
            
            # Detect if there's a significant spike
            is_spike = False
            spike_severity = 0.0
            
            if historical_predictions is not None and len(historical_predictions) > 0:
                historical_mean = float(historical_predictions.mean())
                historical_std = float(historical_predictions.std())
                
                # Z-score test for spike
                if historical_std > 0:
                    z_score = (current_mean - historical_mean) / historical_std
                    is_spike = abs(z_score) > 2.0  # 2 standard deviations
                    spike_severity = abs(z_score)
            
            return {
                'anomaly_count': anomaly_count,
                'anomaly_percentage': anomaly_percentage,
                'is_spike': is_spike,
                'spike_severity': float(spike_severity),
                'current_mean': current_mean,
                'current_std': current_std,
                'anomaly_indices': np.where(anomaly_labels == -1)[0].tolist() if len(X) < 10000 else []
            }
            
        except Exception as e:
            logger.error(f"Error in anomaly detection: {str(e)}", exc_info=True)
            return {
                'anomaly_count': 0,
                'anomaly_percentage': 0.0,
                'is_spike': False,
                'spike_severity': 0.0,
                'current_mean': 0.0,
                'current_std': 0.0
            }
    
    def analyze_root_causes(
        self, 
        features_list: np.ndarray, 
        predictions: np.ndarray
    ) -> Dict[str, Any]:
        """
        ML-based root cause analysis using feature importance
        Aggregates feature importance across all predictions
        """
        if self.model is None:
            return {}
        
        try:
            # Aggregate feature importance
            aggregate_importance = {}
            feature_names = self.feature_names or [
                "sessions_7d", "sessions_30d", "days_since", "avg_duration",
                "unique_screens", "crash_count", "anr_count", "frozen_rate",
                "session_frequency", "engagement_decline", "performance_score"
            ]
            
            # Get global feature importance from model
            if hasattr(self.model, 'feature_importances_'):
                global_importance = self.model.feature_importances_
            else:
                # Fallback: calculate from SHAP if available
                if SHAP_AVAILABLE:
                    try:
                        explainer = shap.TreeExplainer(self.model)
                        shap_values = explainer.shap_values(features_list[:min(100, len(features_list))])
                        global_importance = np.abs(shap_values).mean(axis=0)
                    except:
                        # Use uniform importance as last resort
                        global_importance = np.ones(len(feature_names)) / len(feature_names)
                else:
                    global_importance = np.ones(len(feature_names)) / len(feature_names)
            
            # Create importance dict
            for i, name in enumerate(feature_names):
                if i < len(global_importance):
                    aggregate_importance[name] = float(global_importance[i])
            
            # Normalize to sum to 1.0
            total = sum(aggregate_importance.values())
            if total > 0:
                aggregate_importance = {k: v / total for k, v in aggregate_importance.items()}
            
            # Calculate correlation with high risk predictions
            high_risk_mask = predictions >= 0.7  # 70%+ churn probability
            correlations = {}
            
            if high_risk_mask.sum() > 0:
                for i, name in enumerate(feature_names):
                    if i < features_list.shape[1]:
                        feature_values = features_list[:, i]
                        # Correlation between feature and high risk
                        if feature_values.std() > 0:
                            correlation = np.corrcoef(feature_values, predictions)[0, 1]
                            correlations[name] = float(correlation) if not np.isnan(correlation) else 0.0
            
            # Sort by importance
            sorted_causes = sorted(
                aggregate_importance.items(), 
                key=lambda x: x[1], 
                reverse=True
            )[:10]
            
            return {
                'root_causes': [
                    {
                        'feature': cause[0],
                        'importance': cause[1],
                        'correlation_with_high_risk': correlations.get(cause[0], 0.0)
                    }
                    for cause in sorted_causes
                ],
                'aggregate_importance': aggregate_importance,
                'correlations': correlations
            }
            
        except Exception as e:
            logger.error(f"Error in root cause analysis: {str(e)}", exc_info=True)
            return {}
    
    def analyze_trends(
        self, 
        current_metrics: np.ndarray, 
        historical_metrics: np.ndarray
    ) -> Dict[str, Any]:
        """
        Use ML regression to detect trends
        Instead of simple percentage comparison
        """
        try:
            if len(historical_metrics) < 3:
                # Not enough data for trend analysis
                current_mean = float(current_metrics.mean()) if len(current_metrics) > 0 else 0.0
                return {
                    'trend_direction': 'insufficient_data',
                    'trend_strength': 0.0,
                    'statistical_significance': False,
                    'deviation_from_expected': 0.0,
                    'is_anomaly': False,
                    'current_mean': current_mean,
                    'expected_value': None,
                    'p_value': None
                }
            
            # Time series regression
            X = np.array(range(len(historical_metrics))).reshape(-1, 1)
            y = historical_metrics
            
            # Linear regression
            slope, intercept, r_value, p_value, std_err = stats.linregress(
                range(len(historical_metrics)), historical_metrics
            )
            
            # Predict next period
            next_period = len(historical_metrics)
            expected_value = slope * next_period + intercept
            
            # Compare with actual
            actual_value = float(current_metrics.mean())
            deviation = actual_value - expected_value
            
            # Determine if anomaly (2 standard errors)
            is_anomaly = abs(deviation) > 2 * std_err if std_err > 0 else False
            
            return {
                'trend_direction': 'increasing' if slope > 0 else 'decreasing',
                'trend_strength': float(abs(slope)),
                'statistical_significance': p_value < 0.05,
                'p_value': float(p_value),
                'deviation_from_expected': float(deviation),
                'expected_value': float(expected_value),
                'current_mean': actual_value,  # Use current_mean instead of actual_value
                'is_anomaly': bool(is_anomaly),
                'r_squared': float(r_value ** 2)
            }
            
        except Exception as e:
            logger.error(f"Error in trend analysis: {str(e)}", exc_info=True)
            return {
                'trend_direction': 'error',
                'trend_strength': 0.0,
                'statistical_significance': False,
                'deviation_from_expected': 0.0,
                'is_anomaly': False
            }
    
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

