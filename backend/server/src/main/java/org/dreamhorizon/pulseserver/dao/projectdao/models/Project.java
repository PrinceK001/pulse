package org.dreamhorizon.pulseserver.dao.projectdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Project {
  private Integer projectId;
  private String tenantId;
  private String name;
  private String description;
  private Boolean isActive;
  private String createdBy;
  private String createdAt;
  private String updatedAt;
}

