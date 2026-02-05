package org.dreamhorizon.pulseserver.resources.tenants.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST response model for Firebase tenant information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseTenantRestResponse {
  
  /**
   * The Firebase/GCP-generated tenant ID (e.g., "tenant-abc123").
   */
  private String gcpTenantId;
  
  /**
   * Human-readable display name for the tenant.
   */
  private String displayName;
  
  /**
   * Whether email link (passwordless) sign-in is enabled.
   */
  private boolean emailLinkSignInEnabled;
  
  /**
   * Whether password-based sign-in is allowed.
   */
  private boolean passwordSignInAllowed;
}

