package org.dreamhorizon.pulseserver.service.firebase.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents tenant information from Firebase Identity Platform.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FirebaseTenantInfo {
  
  /**
   * The Firebase/GCP-generated tenant ID (e.g., "tenant-abc123").
   * This is the ID used in Firebase tokens and for API calls.
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

