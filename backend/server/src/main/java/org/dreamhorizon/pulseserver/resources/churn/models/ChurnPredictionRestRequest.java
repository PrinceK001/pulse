package org.dreamhorizon.pulseserver.resources.churn.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPredictionRestRequest {
  private String userId;
  private String deviceModel;
  private String osVersion;
  private String appVersion;
  private String riskLevel; // HIGH, MEDIUM, LOW
  private Integer minRiskScore; // 0-100
  private Integer limit; // Max number of users to return
}

