package org.dreamhorizon.pulseserver.dao.projectdao.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectApiKey {
  private Long projectApiKeyId;
  private Integer projectId;
  private String apiKeyEncrypted;
  private String encryptionSalt;
  private String apiKeyDigest;
  private Boolean isActive;
  private String gracePeriodEndsAt;
  private String createdBy;
  private String createdAt;
  private String deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

