"""
Training script for churn prediction model
Trains XGBoost model on user engagement data
"""
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.metrics import (
    precision_score, recall_score, f1_score, roc_auc_score,
    confusion_matrix, classification_report
)
import xgboost as xgb
import pickle
import os
import sys
import logging
from pathlib import Path
from typing import Optional

# Add parent directory to path
sys.path.append(str(Path(__file__).parent.parent))

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


def engineer_features(df: pd.DataFrame) -> pd.DataFrame:
    """Engineer additional features from base features"""
    logger.info("Engineering features...")
    
    # Session frequency: ratio of 7d sessions to expected based on 30d average
    df['session_frequency'] = df['sessions_last_7_days'] / (df['sessions_last_30_days'] / 4.0 + 1)
    
    # Engagement decline: inverse of session frequency
    df['engagement_decline'] = 1.0 - df['session_frequency']
    df['engagement_decline'] = df['engagement_decline'].clip(0, 1)
    
    # Performance score: weighted combination
    df['performance_score'] = (
        df['crash_count_last_7_days'] * 0.5 +
        df['anr_count_last_7_days'] * 0.3 +
        df['frozen_frame_rate'] * 10.0
    )
    
    # Normalize session duration to seconds
    df['avg_session_duration_sec'] = df['avg_session_duration'] / 1000.0
    
    return df


def generate_sample_data(n_samples: int = 2000, seed: int = 42) -> pd.DataFrame:
    """Generate sample training data for testing"""
    logger.info(f"Generating {n_samples} sample data points...")
    np.random.seed(seed)
    
    # Generate realistic feature distributions
    df = pd.DataFrame({
        'sessions_last_7_days': np.random.poisson(5, n_samples),
        'sessions_last_30_days': np.random.poisson(20, n_samples),
        'days_since_last_session': np.random.exponential(5, n_samples).astype(int),
        'avg_session_duration': np.random.normal(120000, 40000, n_samples).astype(int).clip(10000, 300000),
        'unique_screens_last_7_days': np.random.poisson(3, n_samples).clip(0, 10),
        'crash_count_last_7_days': np.random.poisson(0.3, n_samples).astype(int).clip(0, 5),
        'anr_count_last_7_days': np.random.poisson(0.2, n_samples).astype(int).clip(0, 3),
        'frozen_frame_rate': np.random.beta(2, 20, n_samples).clip(0, 0.5),
    })
    
    # Engineer features
    df = engineer_features(df)
    
    # Create labels based on realistic churn patterns
    # High churn probability if:
    # - No sessions for 30+ days
    # - No sessions for 7+ days with engagement decline
    # - High performance issues
    churn_prob = (
        (df['days_since_last_session'] >= 30).astype(float) * 0.8 +
        ((df['days_since_last_session'] >= 7) & (df['sessions_last_7_days'] == 0)).astype(float) * 0.6 +
        (df['engagement_decline'] > 0.7).astype(float) * 0.4 +
        (df['performance_score'] > 2.0).astype(float) * 0.3
    ).clip(0, 1)
    
    df['churned'] = (np.random.random(n_samples) < churn_prob).astype(int)
    
    logger.info(f"Generated data: {df['churned'].sum()} churned users ({df['churned'].mean()*100:.1f}%)")
    
    return df


def load_training_data(clickhouse_connection_string: Optional[str] = None) -> pd.DataFrame:
    """
    Load training data from ClickHouse or generate sample data
    
    TODO: Implement actual ClickHouse connection when ready
    """
    if clickhouse_connection_string:
        try:
            # TODO: Implement ClickHouse query
            # from clickhouse_driver import Client
            # client = Client(clickhouse_connection_string)
            # query = """
            #     SELECT 
            #         user_id,
            #         sessions_last_7_days,
            #         sessions_last_30_days,
            #         days_since_last_session,
            #         avg_session_duration,
            #         unique_screens_last_7_days,
            #         crash_count_last_7_days,
            #         anr_count_last_7_days,
            #         frozen_frame_rate,
            #         CASE WHEN days_since_last_session >= 30 THEN 1 ELSE 0 END as churned
            #     FROM churn_features
            #     WHERE feature_date >= today() - INTERVAL 60 DAY
            # """
            # df = pd.DataFrame(client.execute(query))
            logger.warning("ClickHouse connection not implemented, using sample data")
            return generate_sample_data()
        except Exception as e:
            logger.error(f"Error loading from ClickHouse: {str(e)}, using sample data")
            return generate_sample_data()
    else:
        logger.info("No ClickHouse connection provided, using sample data")
        return generate_sample_data()


def train_model(
    n_samples: int = 2000,
    clickhouse_connection: Optional[str] = None,
    output_path: str = "models/churn_model_v1.pkl"
):
    """Train XGBoost churn prediction model"""
    logger.info("=" * 60)
    logger.info("Training Churn Prediction Model")
    logger.info("=" * 60)
    
    # Load data
    df = load_training_data(clickhouse_connection)
    
    if df.empty:
        logger.error("No training data available!")
        return None
    
    # Engineer features
    df = engineer_features(df)
    
    # Select features
    feature_cols = [
        'sessions_last_7_days',
        'sessions_last_30_days',
        'days_since_last_session',
        'avg_session_duration_sec',
        'unique_screens_last_7_days',
        'crash_count_last_7_days',
        'anr_count_last_7_days',
        'frozen_frame_rate',
        'session_frequency',
        'engagement_decline',
        'performance_score'
    ]
    
    X = df[feature_cols].fillna(0)
    y = df['churned']
    
    logger.info(f"Training data shape: {X.shape}")
    logger.info(f"Churn rate: {y.mean()*100:.2f}%")
    
    # Train/test split
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    logger.info(f"Train set: {X_train.shape[0]} samples")
    logger.info(f"Test set: {X_test.shape[0]} samples")
    
    # Scale features
    scaler = StandardScaler()
    X_train_scaled = scaler.fit_transform(X_train)
    X_test_scaled = scaler.transform(X_test)
    
    # Train XGBoost
    logger.info("Training XGBoost model...")
    model = xgb.XGBClassifier(
        objective='binary:logistic',
        n_estimators=200,
        max_depth=6,
        learning_rate=0.1,
        subsample=0.8,
        colsample_bytree=0.8,
        random_state=42,
        eval_metric='auc',
        n_jobs=-1
    )
    
    model.fit(
        X_train_scaled, y_train,
        eval_set=[(X_test_scaled, y_test)],
        verbose=False
    )
    
    # Evaluate
    logger.info("\n" + "=" * 60)
    logger.info("Model Evaluation")
    logger.info("=" * 60)
    
    y_pred = model.predict(X_test_scaled)
    y_pred_proba = model.predict_proba(X_test_scaled)[:, 1]
    
    precision = precision_score(y_test, y_pred)
    recall = recall_score(y_test, y_pred)
    f1 = f1_score(y_test, y_pred)
    roc_auc = roc_auc_score(y_test, y_pred_proba)
    
    logger.info(f"Precision:  {precision:.4f}")
    logger.info(f"Recall:     {recall:.4f}")
    logger.info(f"F1-Score:   {f1:.4f}")
    logger.info(f"ROC-AUC:    {roc_auc:.4f}")
    
    # Confusion matrix
    cm = confusion_matrix(y_test, y_pred)
    logger.info(f"\nConfusion Matrix:")
    logger.info(f"                Predicted")
    logger.info(f"              No    Yes")
    logger.info(f"Actual No   {cm[0,0]:5d} {cm[0,1]:5d}")
    logger.info(f"       Yes  {cm[1,0]:5d} {cm[1,1]:5d}")
    
    # Feature importance
    logger.info("\n" + "=" * 60)
    logger.info("Feature Importance (Top 10)")
    logger.info("=" * 60)
    
    feature_importance = pd.DataFrame({
        'feature': feature_cols,
        'importance': model.feature_importances_
    }).sort_values('importance', ascending=False)
    
    for idx, row in feature_importance.head(10).iterrows():
        logger.info(f"{row['feature']:30s} {row['importance']:.4f}")
    
    # Save model
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    model_data = {
        'model': model,
        'scaler': scaler,
        'feature_names': feature_cols,
        'version': 'v1',
        'metrics': {
            'precision': float(precision),
            'recall': float(recall),
            'f1_score': float(f1),
            'roc_auc': float(roc_auc)
        }
    }
    
    with open(output_path, 'wb') as f:
        pickle.dump(model_data, f)
    
    logger.info(f"\nModel saved to {output_path}")
    logger.info("=" * 60)
    
    return model, scaler, feature_cols


if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Train churn prediction model')
    parser.add_argument('--samples', type=int, default=2000, help='Number of sample data points')
    parser.add_argument('--clickhouse', type=str, default=None, help='ClickHouse connection string')
    parser.add_argument('--output', type=str, default='models/churn_model_v1.pkl', help='Output model path')
    
    args = parser.parse_args()
    
    train_model(
        n_samples=args.samples,
        clickhouse_connection=args.clickhouse,
        output_path=args.output
    )

