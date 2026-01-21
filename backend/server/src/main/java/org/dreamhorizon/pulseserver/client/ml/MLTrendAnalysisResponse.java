package org.dreamhorizon.pulseserver.client.ml;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MLTrendAnalysisResponse {
  private String trendDirection; // "increasing", "decreasing", "unknown"
  private Double trendStrength;
  private Boolean statisticalSignificance;
  private Double deviationFromExpected;
  private Boolean isAnomaly;
  private Double currentMean;
  private Double expectedValue;
  private Double pValue;
}

