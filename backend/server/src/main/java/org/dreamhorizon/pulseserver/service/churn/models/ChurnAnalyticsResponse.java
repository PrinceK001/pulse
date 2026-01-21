package org.dreamhorizon.pulseserver.service.churn.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnAnalyticsResponse {
  // Overall metrics
  private Integer totalUsers;
  private Integer highRiskCount;
  private Integer mediumRiskCount;
  private Integer lowRiskCount;
  private Double averageRiskScore;
  private Double overallChurnProbability;
  
  // Risk distribution
  private Map<String, Integer> riskDistribution; // "0-20": count, "20-40": count, etc.
  
  // Top risk factors (aggregated)
  private List<RiskFactorFrequency> topRiskFactors;
  
  // Segment analysis
  private Map<String, SegmentStats> deviceSegments;
  private Map<String, SegmentStats> osSegments;
  private Map<String, SegmentStats> appVersionSegments;
  
  // Performance impact
  private PerformanceImpactMetrics performanceImpact;
  
  // Engagement patterns
  private EngagementPatternMetrics engagementPatterns;
  
  // NEW: ML-driven insights
  private RootCauseAnalysis rootCauseAnalysis;
  private List<PriorityFix> priorityFixes;
  private TrendAnalysis trendAnalysis;
  private PatternInsights patternInsights;
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RiskFactorFrequency {
    private String factor;
    private Integer userCount;
    private Double percentage;
    private String severity; // HIGH, MEDIUM, LOW
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class SegmentStats {
    private Integer userCount;
    private Double averageRiskScore;
    private Integer highRiskCount;
    private Double highRiskPercentage;
    private Double churnProbability;
    private List<String> topRiskFactors;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PerformanceImpactMetrics {
    private Integer usersWithCrashes;
    private Integer usersWithAnrs;
    private Integer usersWithFrozenFrames;
    private Double avgCrashRate;
    private Double avgAnrRate;
    private Double avgFrozenFrameRate;
    private Double performanceRiskCorrelation; // How much performance issues contribute to churn
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EngagementPatternMetrics {
    private Integer inactiveUsers; // No session in 7+ days
    private Integer decliningUsers; // Session frequency declining
    private Double avgDaysSinceLastSession;
    private Double avgSessionsLast7Days;
    private Double avgSessionsLast30Days;
    private Double avgSessionDuration;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RootCauseAnalysis {
    private List<RootCause> primaryCauses;
    private Map<String, Double> aggregateFeatureImportance;
    private Integer totalAtRiskUsers;
    private Map<String, Double> correlations;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RootCause {
    private String cause;
    private Integer affectedUserCount;
    private Double averageSeverity;
    private Double impactScore;
    private List<String> affectedSegments;
    private String recommendedFix;
    private Double estimatedChurnReduction;
    private Double importance; // From ML model
    private Double correlationWithHighRisk;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PriorityFix {
    private String issue;
    private Integer priority; // 1-10
    private Integer estimatedAffectedUsers;
    private Double impactScore;
    private String fixDescription;
    private String estimatedEffort; // "Low", "Medium", "High"
    private Double estimatedChurnReduction;
    private List<String> affectedSegments;
    private String pattern;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TrendAnalysis {
    private TrendMetrics currentPeriod;
    private TrendMetrics previousPeriod;
    private Double trendDirection; // % change
    private List<Anomaly> anomalies;
    private Boolean isAnomaly;
    private String trendDirectionLabel; // "increasing", "decreasing"
    private Double trendStrength;
    private Boolean statisticalSignificance;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TrendMetrics {
    private Double averageRiskScore;
    private Integer highRiskUserCount;
    private Integer totalUsers;
    private Map<String, Integer> riskDistribution;
    private Map<String, Double> topRiskFactors; // Factor -> % of users
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Anomaly {
    private String type; // "SPIKE", "DROP", "PATTERN_CHANGE"
    private String detectedAt;
    private String description;
    private Double severity;
    private String potentialCause;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PatternInsights {
    private List<ChurnPattern> commonPatterns;
    private Map<String, Double> segmentRiskPatterns;
    private Map<String, Double> temporalPatterns;
  }
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ChurnPattern {
    private String pattern;
    private Integer userCount;
    private Double averageRiskScore;
    private Double churnProbability;
    private List<String> commonSegments;
    private Map<String, Object> characteristics;
  }
}

