package org.dreamhorizon.pulseserver.service.churn.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChurnPredictionResponse {
  private List<ChurnRiskUser> users;
  private Integer totalUsers;
  private Integer highRiskCount;
  private Integer mediumRiskCount;
  private Integer lowRiskCount;
  private LocalDateTime predictionDate;
}

