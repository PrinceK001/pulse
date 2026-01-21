package org.dreamhorizon.pulseserver.resources.churn.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnRiskUserRestResponse {
  private String userId;
  private Integer riskScore; // 0-100
  private String riskLevel; // HIGH, MEDIUM, LOW
  private Integer daysSinceLastSession;
  private Integer sessionsLast7Days;
  private Integer sessionsLast30Days;
  private Integer crashCountLast7Days;
  private Integer anrCountLast7Days;
  private Double frozenFrameRate;
  private Long avgSessionDuration; // milliseconds
  private Integer uniqueScreensLast7Days;
  private String deviceModel;
  private String osVersion;
  private String appVersion;
  private List<String> primaryRiskFactors;
  private Double churnProbability; // 0.0-1.0
}

