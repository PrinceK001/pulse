package org.dreamhorizon.pulseserver.dao.projectdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectUsageLimit {
  private Long projectUsageLimitId;
  private Integer projectId; // References projects.project_id
  private String usageLimits; // JSON string
  private Boolean isActive;
  private String createdAt;
  private String disabledAt;
  private String disabledBy;
  private String disabledReason;
  private String createdBy;
}

