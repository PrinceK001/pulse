export interface ChurnAnalyticsRequest {
  riskLevel?: "HIGH" | "MEDIUM" | "LOW";
  minRiskScore?: number;
  limit?: number;
}

export interface ChurnAnalyticsResponse {
  totalUsers: number;
  highRiskCount: number;
  mediumRiskCount: number;
  lowRiskCount: number;
  averageRiskScore: number;
  overallChurnProbability: number;
  riskDistribution: Record<string, number>;
  topRiskFactors: RiskFactorFrequency[];
  deviceSegments: Record<string, SegmentStats>;
  osSegments: Record<string, SegmentStats>;
  appVersionSegments: Record<string, SegmentStats>;
  performanceImpact: PerformanceImpactMetrics;
  engagementPatterns: EngagementPatternMetrics;
  // NEW: ML-driven insights
  rootCauseAnalysis?: RootCauseAnalysis;
  priorityFixes?: PriorityFix[];
  trendAnalysis?: TrendAnalysis;
  patternInsights?: PatternInsights;
}

export interface RiskFactorFrequency {
  factor: string;
  userCount: number;
  percentage: number;
  severity: "HIGH" | "MEDIUM" | "LOW";
}

export interface SegmentStats {
  userCount: number;
  averageRiskScore: number;
  highRiskCount: number;
  highRiskPercentage: number;
  churnProbability: number;
  topRiskFactors: string[];
}

export interface PerformanceImpactMetrics {
  usersWithCrashes: number;
  usersWithAnrs: number;
  usersWithFrozenFrames: number;
  avgCrashRate: number;
  avgAnrRate: number;
  avgFrozenFrameRate: number;
  performanceRiskCorrelation: number;
}

export interface EngagementPatternMetrics {
  inactiveUsers: number;
  decliningUsers: number;
  avgDaysSinceLastSession: number;
  avgSessionsLast7Days: number;
  avgSessionsLast30Days: number;
  avgSessionDuration: number;
}

// NEW: ML-driven insights interfaces
export interface RootCauseAnalysis {
  primaryCauses: RootCause[];
  aggregateFeatureImportance: Record<string, number>;
  totalAtRiskUsers: number;
  correlations?: Record<string, number>;
}

export interface RootCause {
  cause: string;
  affectedUserCount: number;
  averageSeverity: number;
  impactScore: number;
  affectedSegments: string[];
  recommendedFix: string;
  estimatedChurnReduction: number;
  importance: number;
  correlationWithHighRisk: number;
}

export interface PriorityFix {
  issue: string;
  priority: number; // 1-10
  estimatedAffectedUsers: number;
  impactScore: number;
  fixDescription: string;
  estimatedEffort: "Low" | "Medium" | "High";
  estimatedChurnReduction: number;
  affectedSegments: string[];
  pattern: string;
}

export interface TrendAnalysis {
  currentPeriod: TrendMetrics;
  previousPeriod?: TrendMetrics;
  trendDirection: number; // % change
  anomalies: Anomaly[];
  isAnomaly: boolean;
  trendDirectionLabel: string; // "increasing", "decreasing"
  trendStrength: number;
  statisticalSignificance: boolean;
}

export interface TrendMetrics {
  averageRiskScore: number;
  highRiskUserCount: number;
  totalUsers: number;
  riskDistribution: Record<string, number>;
  topRiskFactors: Record<string, number>;
}

export interface Anomaly {
  type: "SPIKE" | "DROP" | "PATTERN_CHANGE";
  detectedAt: string;
  description: string;
  severity: number;
  potentialCause: string;
}

export interface PatternInsights {
  commonPatterns: ChurnPattern[];
  segmentRiskPatterns: Record<string, number>;
  temporalPatterns?: Record<string, number>;
}

export interface ChurnPattern {
  pattern: string;
  userCount: number;
  averageRiskScore: number;
  churnProbability: number;
  commonSegments: string[];
  characteristics?: Record<string, any>;
}

