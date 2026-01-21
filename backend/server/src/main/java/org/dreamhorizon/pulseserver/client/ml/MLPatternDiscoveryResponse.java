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
public class MLPatternDiscoveryResponse {
  private List<Pattern> patterns;
  
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Pattern {
    private Integer patternId;
    private Integer userCount;
    private Double avgRiskScore;
    private Double avgChurnProbability;
    private Map<String, Object> characteristics;
  }
}

