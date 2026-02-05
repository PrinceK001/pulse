package org.dreamhorizon.pulseserver.resources.tenants.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to create a user in a Firebase tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRestRequest {
  
  @NotBlank(message = "email is required")
  @Email(message = "email must be a valid email address")
  private String email;
  
  /**
   * Whether the email is verified. Defaults to true.
   */
  private Boolean emailVerified;
  
  /**
   * Optional display name for the user.
   */
  private String displayName;
}

