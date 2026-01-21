export interface ChurnPredictionRequest {
  userId?: string;
  deviceModel?: string;
  osVersion?: string;
  appVersion?: string;
  riskLevel?: "HIGH" | "MEDIUM" | "LOW";
  minRiskScore?: number;
  limit?: number;
}

export interface ChurnRiskUser {
  userId: string;
  riskScore: number;
  riskLevel: "HIGH" | "MEDIUM" | "LOW";
  daysSinceLastSession: number;
  sessionsLast7Days: number;
  sessionsLast30Days: number;
  crashCountLast7Days: number;
  anrCountLast7Days: number;
  frozenFrameRate: number;
  avgSessionDuration: number;
  uniqueScreensLast7Days: number;
  deviceModel: string;
  osVersion: string;
  appVersion: string;
  primaryRiskFactors: string[];
  churnProbability: number;
}

export interface ChurnPredictionResponse {
  users: ChurnRiskUser[];
  totalUsers: number;
  highRiskCount: number;
  mediumRiskCount: number;
  lowRiskCount: number;
  predictionDate: string;
}

