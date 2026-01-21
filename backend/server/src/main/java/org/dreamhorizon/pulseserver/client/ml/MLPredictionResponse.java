package org.dreamhorizon.pulseserver.client.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLPredictionResponse {
  private Integer riskScore; // 0-100
  private Double churnProbability; // 0.0-1.0
  private String riskLevel; // HIGH, MEDIUM, LOW
}

