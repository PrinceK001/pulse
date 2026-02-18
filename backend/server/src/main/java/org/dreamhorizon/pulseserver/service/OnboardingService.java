package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.model.Project;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;

import java.util.UUID;

/**
 * Service for onboarding new users.
 * Handles the first-time user flow:
 * 1. Create organization (tenant)
 * 2. Create first project within tenant
 * 3. Setup ClickHouse per-project user and row policies
 * 4. Assign roles in OpenFGA (user as tenant owner, user as project admin)
 * 5. Generate JWT tokens with tenantId
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OnboardingService {
    
    private final TenantService tenantService;
    private final ProjectService projectService;
    private final OpenFgaService openFgaService;
    private final JwtService jwtService;
    
    /**
     * Complete onboarding for a new user.
     * Creates a tenant and first project, sets up authorization.
     * 
     * @param userId User ID (from Firebase)
     * @param email User email
     * @param name User display name
     * @param organizationName Organization/tenant name
     * @param projectName First project name
     * @param projectDescription First project description
     * @return OnboardingResult with tenant, project, and JWT tokens
     */
    public Single<OnboardingResult> completeOnboarding(
            String userId,
            String email,
            String name,
            String organizationName,
            String projectName,
            String projectDescription) {
        
        String tenantId = "tenant-" + UUID.randomUUID().toString();
        
        log.info("Starting onboarding: userId={}, tenantId={}, org={}, project={}", 
            userId, tenantId, organizationName, projectName);
        
        // Step 1: Create tenant
        CreateTenantRequest tenantRequest = CreateTenantRequest.builder()
            .tenantId(tenantId)
            .name(organizationName)
            .description("Organization created during onboarding")
            .gcpTenantId(null)  // No Firebase tenant ID
            .domainName(null)
            .build();
        
        return tenantService.createTenant(tenantRequest)
            .flatMap(tenant -> 
                // Step 2: Create first project within tenant
                projectService.createProject(tenantId, projectName, projectDescription, userId)
                    .flatMap(project ->
                        // Step 3: Assign tenant owner role to user
                        openFgaService.assignTenantRole(userId, tenantId, "owner")
                            .andThen(Single.just(project))
                    )
                    .map(project -> {
                        // Step 4: Generate JWT tokens with tenantId
                        String accessToken = jwtService.generateAccessToken(userId, email, name, tenantId);
                        String refreshToken = jwtService.generateRefreshToken(userId, email, name, tenantId);
                        
                        log.info("Onboarding completed: userId={}, tenantId={}, projectId={}", 
                            userId, tenantId, project.getProjectId());
                        
                        return OnboardingResult.builder()
                            .userId(userId)
                            .email(email)
                            .name(name)
                            .tenantId(tenantId)
                            .tenantName(organizationName)
                            .projectId(project.getProjectId())
                            .projectName(project.getName())
                            .projectApiKey(project.getApiKey())
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .tokenType("Bearer")
                            .expiresIn(JwtService.ACCESS_TOKEN_VALIDITY_SECONDS)
                            .redirectTo("/projects/" + project.getProjectId())
                            .build();
                    })
            )
            .doOnError(error -> 
                log.error("Onboarding failed: userId={}, tenantId={}", userId, tenantId, error)
            );
    }
    
    /**
     * Result of onboarding operation.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class OnboardingResult {
        private String userId;
        private String email;
        private String name;
        private String tenantId;
        private String tenantName;
        private String projectId;
        private String projectName;
        private String projectApiKey;
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private Integer expiresIn;
        private String redirectTo;
    }
}
