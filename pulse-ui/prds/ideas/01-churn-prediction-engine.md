# PRD: Churn Prediction Engine

## 1. Overview

### 1.1 Problem Statement
Product managers and mobile leaders struggle to identify users at risk of churning before they leave. Current analytics show historical engagement but don't predict future behavior. By the time a user churns, it's too late to intervene.

### 1.2 Solution
A machine learning-powered churn prediction engine that analyzes user behavior patterns, session quality, engagement trends, and performance metrics to predict churn risk with actionable intervention recommendations.

### 1.3 Success Metrics
- **Prediction Accuracy**: >85% precision for high-risk users (churn within 30 days)
- **Early Detection**: Identify at-risk users 7-14 days before churn
- **Intervention Success Rate**: >30% of intervened users retained
- **Adoption**: 80% of product managers use churn insights weekly

---

## 2. User Stories

### 2.1 Product Manager
- **As a PM**, I want to see users at high risk of churning so I can prioritize retention campaigns
- **As a PM**, I want to understand why users are churning so I can fix root causes
- **As a PM**, I want to see the impact of my retention efforts on churn risk

### 2.2 Mobile Leader
- **As a mobile leader**, I want to see churn risk by user segment so I can personalize interventions
- **As a mobile leader**, I want to correlate technical issues with churn risk
- **As a mobile leader**, I want to predict churn impact on business metrics

### 2.3 Data Analyst
- **As a data analyst**, I want to understand churn prediction model performance
- **As a data analyst**, I want to export churn risk data for further analysis

---

## 3. Features & Functionality

### 3.1 Churn Risk Dashboard
**Location**: New page `/churn-prediction` or section in User Engagement page

**Components**:
1. **Churn Risk Overview**
   - Total users at risk (High/Medium/Low)
   - Churn risk trend over time
   - Predicted churn rate for next 30 days
   - Comparison with historical churn

2. **At-Risk Users Table**
   - User ID
   - Churn Risk Score (0-100)
   - Risk Level (High/Medium/Low)
   - Days Since Last Session
   - Primary Risk Factors (top 3)
   - Last Session Quality Score
   - Recommended Interventions
   - Actions: View User Journey, Send Intervention

3. **Risk Factor Analysis**
   - Top contributing factors to churn risk
   - Factor importance visualization
   - Correlation between factors

4. **Segment Breakdown**
   - Churn risk by:
     - Device Model
     - OS Version
     - App Version
     - Geographic Region
     - Network Provider
     - User Cohort (new/returning/power user)

5. **Intervention Recommendations**
   - Personalized intervention suggestions per user
   - Intervention effectiveness tracking
   - A/B test results for interventions

### 3.2 Churn Risk Score Calculation

**ML Model Approach**:
- **Algorithm**: Gradient Boosting (XGBoost/LightGBM) or Random Forest
- **Training Data**: Historical user sessions with known churn outcomes
- **Features**:
  - **Engagement Features**:
    - Session frequency (sessions per week)
    - Days since last session
    - Session duration trends
    - Time between sessions
    - DAU/WAU/MAU ratios
  - **Performance Features**:
    - Average session quality score
    - Crash rate per session
    - ANR rate per session
    - Frozen frame rate
    - Network error rate
  - **Behavioral Features**:
    - Screen navigation patterns
    - Feature usage frequency
    - Business event completion rate
    - Foreground/background ratio
  - **Contextual Features**:
    - Device model performance
    - Network quality
    - App version stability
    - Geographic region

**Churn Definition**:
- User has no session for 30+ days (configurable)
- User uninstalled app (if available)
- User marked as churned in business system (if integrated)

**Risk Score Calculation**:
- Score: 0-100
- **High Risk (70-100)**: >70% probability of churning in next 30 days
- **Medium Risk (40-69)**: 30-70% probability
- **Low Risk (0-39)**: <30% probability

### 3.3 User Journey View
- Click on user → View detailed journey
- Timeline of sessions
- Performance issues encountered
- Engagement decline visualization
- Risk factor timeline

### 3.4 Alerts & Notifications
- Alert when user moves to high-risk category
- Daily digest of new high-risk users
- Weekly churn risk trend report
- Integration with existing alert system

---

## 4. Technical Implementation

### 4.1 Data Pipeline

**Data Sources**:
- `otel_logs` - Session start events, user activity
- `otel_traces` - Screen sessions, interactions, performance metrics
- `stack_trace_events` - Crashes, ANRs
- Business events (custom events)

**Feature Engineering**:
1. **User Session Aggregation** (daily batch job)
   - Calculate per-user metrics:
     - Session count, frequency
     - Average session duration
     - Performance metrics (crashes, ANRs, frozen frames)
     - Screen visit patterns
     - Business event completion
   
2. **Time-Series Features** (rolling windows)
   - 7-day, 14-day, 30-day trends
   - Rate of change (increasing/decreasing)
   - Volatility measures

3. **User Cohorts**
   - New user (first 7 days)
   - Returning user
   - Power user (top 20% by engagement)

**Storage**:
- **Feature Store**: ClickHouse table `churn_features` (daily snapshots)
- **Predictions**: ClickHouse table `churn_predictions` (daily updates)
- **Model Artifacts**: S3 or model registry

### 4.2 ML Model Training

**Training Pipeline**:
1. **Data Preparation**:
   - Extract features for users with known churn status
   - Handle missing values
   - Feature scaling/normalization
   
2. **Model Training**:
   - Train/test split (80/20)
   - Cross-validation
   - Hyperparameter tuning
   - Feature importance analysis

3. **Model Evaluation**:
   - Precision, Recall, F1-score
   - ROC-AUC
   - Confusion matrix
   - Feature importance ranking

4. **Model Deployment**:
   - Batch prediction (daily)
   - Real-time prediction API (for specific users)

**Model Retraining**:
- Weekly retraining with latest data
- A/B testing new model versions
- Performance monitoring

### 4.3 API Endpoints

**Backend Endpoints**:
```
GET /api/v1/churn/risk-overview
  - Returns: Risk distribution, trends, predicted churn rate

GET /api/v1/churn/at-risk-users
  - Query params: riskLevel, limit, offset, filters
  - Returns: List of at-risk users with scores

GET /api/v1/churn/user/{userId}/risk
  - Returns: User's churn risk score, factors, journey

GET /api/v1/churn/risk-factors
  - Returns: Top risk factors with importance scores

GET /api/v1/churn/interventions/{userId}
  - Returns: Recommended interventions for user

POST /api/v1/churn/interventions/{userId}/track
  - Body: interventionType, timestamp
  - Tracks intervention application
```

### 4.4 Frontend Implementation

**New Components**:
- `ChurnRiskDashboard.tsx` - Main dashboard
- `AtRiskUsersTable.tsx` - Users table with filters
- `ChurnRiskChart.tsx` - Risk trend visualization
- `RiskFactorAnalysis.tsx` - Factor importance
- `UserChurnJourney.tsx` - Individual user journey view
- `InterventionRecommendations.tsx` - Intervention suggestions

**Integration Points**:
- Add "Churn Risk" section to User Engagement page
- Add churn risk badge to user detail views
- Add churn risk filter to user lists

---

## 5. Data Requirements

### 5.1 Minimum Data Requirements
- At least 30 days of historical session data
- Minimum 10,000 unique users for model training
- User IDs must be consistent across sessions
- Session start/end events tracked

### 5.2 Data Quality Checks
- User ID completeness (>95%)
- Session data completeness
- Feature calculation validation
- Model prediction confidence thresholds

---

## 6. Success Criteria & KPIs

### 6.1 Model Performance
- **Precision@HighRisk**: >85% (of users predicted high-risk, 85% actually churn)
- **Recall@HighRisk**: >70% (catch 70% of all churners)
- **F1-Score**: >0.75
- **Early Detection**: Average 10+ days before churn

### 6.2 Business Impact
- **Retention Rate**: 5% improvement in 30-day retention
- **Intervention Effectiveness**: >30% of intervened users retained
- **Time to Action**: <24 hours from high-risk detection to intervention

### 6.3 Adoption Metrics
- **Weekly Active Users**: 80% of PMs use feature weekly
- **Intervention Rate**: >50% of high-risk users receive intervention
- **User Satisfaction**: >4.0/5.0 rating

---

## 7. Implementation Roadmap

### Phase 1: MVP (4-6 weeks)
- [ ] Feature engineering pipeline
- [ ] ML model training (baseline model)
- [ ] Basic churn risk API
- [ ] Simple dashboard with risk scores
- [ ] At-risk users table

### Phase 2: Enhanced Features (4-6 weeks)
- [ ] Risk factor analysis
- [ ] User journey view
- [ ] Intervention recommendations
- [ ] Alerts integration
- [ ] Segment breakdown

### Phase 3: Advanced ML (4-6 weeks)
- [ ] Model optimization
- [ ] Real-time predictions
- [ ] A/B testing framework
- [ ] Intervention effectiveness tracking
- [ ] Advanced visualizations

### Phase 4: Scale & Optimize (Ongoing)
- [ ] Model performance monitoring
- [ ] Automated retraining
- [ ] Integration with marketing tools
- [ ] Advanced segmentation
- [ ] Predictive analytics expansion

---

## 8. Open Questions & Risks

### 8.1 Open Questions
1. How to define "churn" for different app types?
2. Should we integrate with business systems for churn confirmation?
3. What's the minimum data volume needed for accurate predictions?
4. How to handle new users with limited history?

### 8.2 Risks
- **Data Quality**: Incomplete user tracking affects model accuracy
- **Model Drift**: User behavior changes over time
- **False Positives**: Incorrectly flagging engaged users
- **Privacy**: User behavior analysis privacy concerns

### 8.3 Mitigation
- Robust data validation
- Regular model retraining
- Configurable risk thresholds
- Privacy-compliant data handling

---

## 9. Dependencies

### 9.1 Internal
- Session tracking system
- User engagement data pipeline
- Performance metrics collection
- Alert system integration

### 9.2 External
- ML infrastructure (training, serving)
- Feature store
- Model monitoring tools
- (Optional) Business system integration for churn confirmation

