package org.dreamhorizon.pulseserver.resources.tenants.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for user operations in a Firebase tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRestResponse {
  private String uid;
  private String email;
  private String displayName;
  private boolean emailVerified;
  private boolean disabled;
  private String gcpTenantId;
  private long creationTimestamp;
  private long lastSignInTimestamp;
}

