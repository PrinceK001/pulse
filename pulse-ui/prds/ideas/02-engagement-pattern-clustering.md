# PRD: Engagement Pattern Clustering

## 1. Overview

### 1.1 Problem Statement
Product teams need to understand different user engagement patterns to personalize experiences, prioritize features, and optimize retention strategies. Current analytics show aggregate metrics but don't reveal distinct user behavior segments.

### 1.2 Solution
An ML-powered user clustering system that automatically identifies distinct engagement patterns, creates user segments, and provides insights into each segment's behavior, needs, and value.

### 1.3 Success Metrics
- **Segment Coverage**: >90% of users assigned to meaningful segments
- **Segment Stability**: <10% segment reassignment week-over-week
- **Actionability**: 80% of PMs use segments for feature decisions
- **Personalization Impact**: 15% improvement in engagement for personalized experiences

---

## 2. User Stories

### 2.1 Product Manager
- **As a PM**, I want to see distinct user segments so I can prioritize features for each group
- **As a PM**, I want to understand what makes each segment unique
- **As a PM**, I want to see how segments evolve over time

### 2.2 Mobile Leader
- **As a mobile leader**, I want to see segment-specific performance issues
- **As a mobile leader**, I want to personalize app experience by segment
- **As a mobile leader**, I want to measure feature adoption by segment

### 2.3 Growth Team
- **As a growth manager**, I want to identify high-value segments for targeting
- **As a growth manager**, I want to see conversion funnels by segment
- **As a growth manager**, I want to track segment growth/decline

---

## 3. Features & Functionality

### 3.1 User Segments Dashboard
**Location**: New page `/user-segments` or section in User Engagement

**Components**:
1. **Segment Overview**
   - Total segments identified
   - User distribution across segments
   - Segment size trends
   - New/declining segments

2. **Segment Cards**
   Each segment shows:
   - Segment name (auto-generated or custom)
   - User count & percentage
   - Key characteristics (top 3)
   - Average engagement metrics
   - Growth trend
   - Typical user journey
   - Performance health

3. **Segment Comparison Table**
   - Side-by-side comparison of segments
   - Metrics: DAU, session frequency, session duration, feature usage, performance
   - Statistical significance indicators

4. **Segment Deep Dive**
   - Click segment → Detailed view:
     - User list
     - Behavior patterns
     - Feature adoption
     - Performance issues
     - Geographic/device distribution
     - Business event completion
     - Recommended actions

### 3.2 Segment Types (Expected)

**Primary Segments**:
1. **Power Users** (10-15%)
   - High session frequency
   - Long session duration
   - Deep feature usage
   - High business event completion

2. **Casual Users** (40-50%)
   - Moderate session frequency
   - Short-medium sessions
   - Basic feature usage
   - Occasional business events

3. **Explorers** (15-20%)
   - High screen navigation
   - Trying many features
   - Variable session duration
   - Learning phase

4. **Strugglers** (10-15%)
   - High performance issues
   - Low feature completion
   - Short sessions
   - High drop-off rate

5. **At-Risk Users** (10-15%)
   - Declining engagement
   - Increasing time between sessions
   - Low feature usage
   - High churn risk

6. **New Users** (5-10%)
   - First 7 days
   - Onboarding phase
   - Learning patterns

### 3.3 Segment Characteristics

**Clustering Features**:
- **Engagement Metrics**:
  - Session frequency (daily/weekly)
  - Average session duration
  - Time between sessions
  - DAU/WAU/MAU ratios
  - Active days per week

- **Behavioral Patterns**:
  - Screens visited (diversity, frequency)
  - Navigation patterns (depth, breadth)
  - Feature usage (count, frequency)
  - Business event completion rate
  - Time-of-day patterns

- **Performance Context**:
  - Average session quality score
  - Crash/ANR rate
  - Network quality
  - Device performance

- **Temporal Patterns**:
  - Day of week preferences
  - Time of day activity
  - Session timing patterns

### 3.4 Segment Insights

**Auto-Generated Insights**:
- "Power Users are 3x more likely to use Feature X"
- "Strugglers have 2x higher crash rate on Screen Y"
- "Explorers convert 40% better with Feature Z"
- "Casual Users prefer morning sessions"

**Actionable Recommendations**:
- Feature prioritization by segment
- Performance optimization targets
- Personalization opportunities
- Retention strategies

---

## 4. Technical Implementation

### 4.1 Clustering Algorithm

**Approach**: 
- **Primary**: K-Means or DBSCAN for initial clustering
- **Advanced**: Hierarchical clustering or Gaussian Mixture Models
- **Hybrid**: Combine multiple algorithms for robustness

**Feature Engineering**:
1. **User Feature Vector** (per user, weekly snapshot):
   ```python
   {
     'session_frequency_7d': float,
     'avg_session_duration': float,
     'screens_visited_count': int,
     'unique_features_used': int,
     'business_events_completed': int,
     'crash_rate': float,
     'anr_rate': float,
     'session_quality_avg': float,
     'active_days_7d': int,
     'peak_hour': int,  # 0-23
     'preferred_day': int,  # 0-6
     'network_quality_avg': float,
     'device_performance_score': float
   }
   ```

2. **Normalization**:
   - StandardScaler or MinMaxScaler
   - Handle outliers (robust scaling)

3. **Dimensionality Reduction** (optional):
   - PCA for visualization
   - t-SNE for 2D visualization

**Clustering Process**:
1. **Weekly Batch Job**:
   - Extract user features for all active users
   - Normalize features
   - Run clustering algorithm
   - Assign users to segments
   - Calculate segment characteristics

2. **Segment Naming**:
   - Auto-generate based on top characteristics
   - Allow manual naming/editing

3. **Segment Stability**:
   - Track segment assignments over time
   - Detect segment evolution
   - Alert on significant changes

### 4.2 Data Pipeline

**Data Sources**:
- `otel_logs` - Session events
- `otel_traces` - Screen sessions, interactions
- Business events
- Performance metrics

**Storage**:
- **User Features**: ClickHouse table `user_features_weekly`
- **Segment Assignments**: ClickHouse table `user_segments` (weekly snapshots)
- **Segment Metadata**: MySQL/PostgreSQL table `segments` (names, descriptions, characteristics)

**Processing**:
- Weekly batch job (Sunday night)
- Process previous week's data
- Update segment assignments
- Generate insights

### 4.3 API Endpoints

```
GET /api/v1/segments
  - Returns: List of all segments with overview

GET /api/v1/segments/{segmentId}
  - Returns: Segment details, user list, characteristics

GET /api/v1/segments/{segmentId}/users
  - Query params: limit, offset, filters
  - Returns: Users in segment

GET /api/v1/segments/{segmentId}/insights
  - Returns: Auto-generated insights for segment

GET /api/v1/segments/comparison
  - Query params: segmentIds (comma-separated)
  - Returns: Side-by-side comparison

GET /api/v1/users/{userId}/segment
  - Returns: User's current segment assignment

GET /api/v1/segments/trends
  - Returns: Segment size trends over time
```

### 4.4 Frontend Implementation

**New Components**:
- `UserSegmentsDashboard.tsx` - Main dashboard
- `SegmentCard.tsx` - Individual segment card
- `SegmentComparison.tsx` - Comparison table
- `SegmentDeepDive.tsx` - Segment detail view
- `SegmentInsights.tsx` - Auto-generated insights
- `SegmentTrendChart.tsx` - Size trends

**Visualizations**:
- Segment distribution (pie/donut chart)
- Segment comparison (bar charts, radar charts)
- User journey by segment (sankey diagram)
- Segment evolution (line chart)
- 2D cluster visualization (t-SNE scatter plot)

---

## 5. Success Criteria

### 5.1 Clustering Quality
- **Silhouette Score**: >0.5 (good separation)
- **Segment Stability**: <10% reassignment week-over-week
- **Coverage**: >90% of users in meaningful segments
- **Segment Size**: No segment <1% or >50% of users

### 5.2 Business Impact
- **Feature Adoption**: 15% improvement with segment-based targeting
- **Retention**: 10% improvement for personalized experiences
- **PM Usage**: 80% of PMs use segments for decisions
- **Actionability**: >5 actionable insights per segment

---

## 6. Implementation Roadmap

### Phase 1: MVP (4-6 weeks)
- [ ] Feature engineering pipeline
- [ ] Basic clustering algorithm (K-Means)
- [ ] Segment assignment API
- [ ] Simple dashboard with segment list
- [ ] Segment overview cards

### Phase 2: Enhanced Features (4-6 weeks)
- [ ] Segment comparison
- [ ] Segment deep dive
- [ ] Auto-generated insights
- [ ] Segment trends
- [ ] User segment assignment view

### Phase 3: Advanced Analytics (4-6 weeks)
- [ ] Advanced clustering algorithms
- [ ] Segment evolution tracking
- [ ] Predictive segment migration
- [ ] Integration with personalization
- [ ] A/B testing by segment

---

## 7. Open Questions

1. How many segments is optimal? (Start with 5-7)
2. Should segments be hierarchical? (e.g., Power User → Mobile Power User)
3. How to handle users that don't fit any segment?
4. Should we allow custom segment definitions?

