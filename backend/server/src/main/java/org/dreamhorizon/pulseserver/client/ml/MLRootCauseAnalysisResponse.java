package org.dreamhorizon.pulseserver.client.ml;

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
public class MLRootCauseAnalysisResponse {
  private List<RootCause> rootCauses;
  private Map<String, Object> aggregateImportance;
  private Map<String, Object> correlations;
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RootCause {
    private String feature;
    private Double importance;
    private Double correlationWithHighRisk;
  }
}

