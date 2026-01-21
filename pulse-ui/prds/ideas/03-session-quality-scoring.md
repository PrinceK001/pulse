# PRD: Session Quality Scoring

## 1. Overview

### 1.1 Problem Statement
Teams need a unified way to measure session quality that combines performance, engagement, and user experience. Currently, metrics are siloed (crashes, ANRs, duration) making it hard to understand overall session health.

### 1.2 Solution
A comprehensive session quality scoring system that evaluates each session across multiple dimensions (performance, engagement, completion) and provides a single quality score with detailed breakdowns.

### 1.3 Success Metrics
- **Score Accuracy**: Quality score correlates >0.8 with user satisfaction (if available)
- **Actionability**: 90% of low-quality sessions have identifiable issues
- **Adoption**: 100% of sessions scored within 24 hours
- **Impact**: 20% improvement in average session quality through targeted fixes

---

## 2. User Stories

### 2.1 Product Manager
- **As a PM**, I want to see session quality trends so I can measure UX improvements
- **As a PM**, I want to identify low-quality sessions to prioritize fixes
- **As a PM**, I want to correlate quality with business outcomes

### 2.2 Mobile Leader
- **As a mobile leader**, I want to see quality by device/OS to prioritize optimizations
- **As a mobile leader**, I want to track quality impact of releases
- **As a mobile leader**, I want to set quality targets and track progress

### 2.3 Engineering Team
- **As an engineer**, I want to see what's causing low-quality sessions
- **As an engineer**, I want to validate that my fixes improve quality
- **As an engineer**, I want to prioritize bugs by quality impact

---

## 3. Features & Functionality

### 3.1 Session Quality Dashboard
**Location**: New section in existing pages or dedicated `/session-quality` page

**Components**:
1. **Quality Overview**
   - Average quality score (overall, by time period)
   - Quality distribution (Excellent/Good/Fair/Poor)
   - Quality trend over time
   - Quality by segment (device, OS, region, app version)

2. **Quality Score Breakdown**
   - Overall score (0-100)
   - Component scores:
     - Performance Score (0-100)
     - Engagement Score (0-100)
     - Completion Score (0-100)
   - Weighted contribution of each component

3. **Low-Quality Sessions Table**
   - Session ID
   - Quality Score
   - Primary Issues (top 3)
   - User ID
   - Device/OS/App Version
   - Timestamp
   - Actions: View Session Timeline, View Details

4. **Quality Trends**
   - Time series of average quality
   - Quality by app version (release impact)
   - Quality by device model
   - Quality by geographic region

5. **Quality Heatmap**
   - Quality by screen/feature
   - Identify problematic areas
   - Correlation with business events

### 3.2 Quality Score Calculation

**Score Components**:

1. **Performance Score (40% weight)**
   - **Crashes**: -30 points per crash
   - **ANRs**: -20 points per ANR
   - **Frozen Frames**: -5 points per frozen frame (>700ms)
   - **Slow Frames**: -1 point per slow frame (16-700ms)
   - **Network Errors**: -10 points per failed network request
   - **Network Latency**: Penalty for P95 latency >2s
   - **Screen Load Time**: Penalty for load time >3s

2. **Engagement Score (35% weight)**
   - **Session Duration**: 
     - Excellent: >5 minutes
     - Good: 2-5 minutes
     - Fair: 1-2 minutes
     - Poor: <1 minute
   - **Screen Diversity**: More screens = higher score
   - **Interaction Depth**: More interactions = higher score
   - **Feature Usage**: Using key features = higher score
   - **Time in Foreground**: Higher ratio = higher score

3. **Completion Score (25% weight)**
   - **Business Event Completion**: 
     - Completed key events = higher score
     - Started but didn't complete = medium score
     - No key events = lower score
   - **Interaction Completion**: Completed critical interactions
   - **Screen Flow Completion**: Completed expected screen flows
   - **Error Recovery**: Recovered from errors = bonus points

**Score Formula**:
```
Quality Score = 
  (Performance Score × 0.40) + 
  (Engagement Score × 0.35) + 
  (Completion Score × 0.25)

Where each component score is normalized to 0-100
```

**Quality Categories**:
- **Excellent (80-100)**: High-quality session, no major issues
- **Good (60-79)**: Decent session, minor issues
- **Fair (40-59)**: Moderate issues, user may have struggled
- **Poor (0-39)**: Significant issues, poor experience

### 3.3 Session Quality Details

**Click on session → Quality Breakdown**:
- Component scores with explanations
- Issue list with severity
- Performance timeline
- Engagement metrics
- Completion status
- Comparison with user's average
- Comparison with app average

### 3.4 Quality Alerts

- Alert when average quality drops >10% week-over-week
- Alert when specific screen/feature quality drops
- Alert when quality regression in new app version
- Daily quality digest

---

## 4. Technical Implementation

### 4.1 Score Calculation Pipeline

**Real-Time Calculation** (for active sessions):
- Calculate score incrementally as session progresses
- Update score when new events occur
- Store intermediate scores

**Batch Calculation** (for completed sessions):
- Daily batch job processes all completed sessions
- Calculate final quality scores
- Update quality metrics

**Data Sources**:
- `otel_traces` - Screen sessions, interactions, network requests
- `otel_logs` - Session events, crashes, ANRs
- `stack_trace_events` - Crash/ANR details
- Business events

**Storage**:
- **Session Quality Scores**: ClickHouse table `session_quality_scores`
  ```sql
  CREATE TABLE session_quality_scores (
    SessionId String,
    UserId String,
    Timestamp DateTime64(9),
    QualityScore UInt8,  -- 0-100
    PerformanceScore UInt8,
    EngagementScore UInt8,
    CompletionScore UInt8,
    PrimaryIssues Array(String),
    DeviceModel String,
    OsVersion String,
    AppVersion String,
    ...
  )
  ```

### 4.2 API Endpoints

```
GET /api/v1/session-quality/overview
  - Query params: startTime, endTime, filters
  - Returns: Quality overview, distribution, trends

GET /api/v1/session-quality/sessions
  - Query params: qualityRange, limit, offset, filters
  - Returns: List of sessions with quality scores

GET /api/v1/session-quality/session/{sessionId}
  - Returns: Detailed quality breakdown for session

GET /api/v1/session-quality/trends
  - Query params: timeRange, groupBy (device/os/version)
  - Returns: Quality trends

GET /api/v1/session-quality/heatmap
  - Query params: dimension (screen/feature)
  - Returns: Quality heatmap data
```

### 4.3 Frontend Implementation

**New Components**:
- `SessionQualityDashboard.tsx` - Main dashboard
- `QualityScoreCard.tsx` - Score display with breakdown
- `QualityDistributionChart.tsx` - Distribution visualization
- `QualityTrendChart.tsx` - Trend over time
- `LowQualitySessionsTable.tsx` - Problematic sessions
- `QualityHeatmap.tsx` - Heatmap visualization
- `SessionQualityDetail.tsx` - Individual session breakdown

**Integration Points**:
- Add quality score to Session Timeline page
- Add quality filter to session lists
- Add quality metrics to Home dashboard
- Add quality to Screen/Interaction detail pages

---

## 5. Success Criteria

### 5.1 Score Accuracy
- **Correlation with Satisfaction**: >0.8 (if satisfaction data available)
- **Issue Detection**: >90% of low-quality sessions have identifiable issues
- **Score Stability**: <5% variance for same session type

### 5.2 Business Impact
- **Quality Improvement**: 20% improvement in average quality over 3 months
- **Issue Resolution**: 50% of identified issues fixed within 30 days
- **User Retention**: 15% improvement in retention for high-quality sessions

### 5.3 Adoption
- **Coverage**: 100% of sessions scored
- **Usage**: 90% of PMs check quality weekly
- **Actionability**: >80% of low-quality sessions lead to fixes

---

## 6. Implementation Roadmap

### Phase 1: MVP (3-4 weeks)
- [ ] Basic score calculation (performance + engagement)
- [ ] Score storage and API
- [ ] Simple dashboard with overview
- [ ] Low-quality sessions table

### Phase 2: Enhanced Features (3-4 weeks)
- [ ] Completion score component
- [ ] Quality breakdown details
- [ ] Quality trends and comparisons
- [ ] Integration with existing pages

### Phase 3: Advanced Analytics (3-4 weeks)
- [ ] Quality heatmaps
- [ ] Predictive quality (ML model)
- [ ] Quality alerts
- [ ] Quality impact analysis

---

## 7. Open Questions

1. Should quality scores be real-time or batch?
2. How to weight components? (Start with 40/35/25, make configurable)
3. Should we have different quality models for different app types?
4. How to handle very short sessions (<10 seconds)?

