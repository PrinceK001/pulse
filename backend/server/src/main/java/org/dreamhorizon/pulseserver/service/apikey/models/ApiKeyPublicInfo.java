package org.dreamhorizon.pulseserver.service.apikey.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public API key information without sensitive data.
 * Never includes the raw key, encrypted key, salt, or digest.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyPublicInfo {
  private Long apiKeyId;
  private String projectId;
  private String displayName;
  private Boolean isActive;
  private String expiresAt;
  private String gracePeriodEndsAt;
  private String createdBy;
  private String createdAt;
  private String deactivatedAt;
  private String deactivatedBy;
  private String deactivationReason;
}

