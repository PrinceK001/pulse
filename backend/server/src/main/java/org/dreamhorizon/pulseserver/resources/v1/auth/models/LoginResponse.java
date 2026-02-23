package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    @JsonProperty("status")
    private String status;  // "needs_onboarding" or "authenticated"
    
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("refreshToken")
    private String refreshToken;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("tenantId")
    private String tenantId;
    
    @JsonProperty("tenantRole")
    private String tenantRole;
    
    @JsonProperty("needsOnboarding")
    private Boolean needsOnboarding;
    
    @JsonProperty("tokenType")
    private String tokenType;
    
    @JsonProperty("expiresIn")
    private Integer expiresIn;
}
