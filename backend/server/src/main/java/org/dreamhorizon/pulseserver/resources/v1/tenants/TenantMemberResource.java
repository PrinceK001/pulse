package org.dreamhorizon.pulseserver.resources.v1.tenants;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.resources.v1.members.models.AddMemberRequest;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberListResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.MemberResponse;
import org.dreamhorizon.pulseserver.resources.v1.members.models.UpdateMemberRoleRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.rest.io.RestResponse;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.OpenFgaService;
import org.dreamhorizon.pulseserver.service.TenantMemberService;
import org.dreamhorizon.pulseserver.service.tenant.TenantService;

/**
 * REST resource for tenant member management.
 * Manages adding, removing, and listing members of a tenant.
 */
@Slf4j
@Path("/v1/tenants/{tenantId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantMemberResource {
    
    private final TenantMemberService tenantMemberService;
    private final TenantService tenantService;
    private final OpenFgaService openFgaService;
    private final JwtService jwtService;
    
    /**
     * Add a user to a tenant.
     * Requires tenant admin role.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param request Member details (email, role)
     * @return Added member information
     */
    @POST
    public CompletionStage<Response<MemberResponse>> addMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            AddMemberRequest request) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.info("Adding member to tenant: email={}, tenant={}, role={}, addedBy={}", 
            request.getEmail(), tenantId, request.getRole(), userId);
        
        // Check if user is tenant admin
        return openFgaService.isTenantAdmin(userId, tenantId)
            .flatMap(isAdmin -> {
                if (!isAdmin) {
                    return Single.error(ServiceError.SERVICE_UNKNOWN_EXCEPTION
                        .getCustomException("Unauthorized", "Only tenant admins can add members"));
                }
                
                // Add user to tenant
                return tenantMemberService.addUserToTenant(
                    tenantId, 
                    request.getEmail(), 
                    request.getRole(), 
                    userId, 
                    "Tenant",  // We don't need tenant name for emails in this flow
                    "Admin"    // Admin name placeholder
                );
            })
            .flatMap(user -> {
                // Get role from OpenFGA for response
                return openFgaService.getUserTenantRole(user.getUserId(), tenantId)
                    .map(roleOpt -> MemberResponse.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(roleOpt.orElse(request.getRole()))
                        .status(user.getStatus())
                        .lastLoginAt(user.getLastLoginAt())
                        .build());
            })
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * List all members of a tenant.
     * Requires tenant member role.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @return List of tenant members
     */
    @GET
    public CompletionStage<Response<MemberListResponse>> listMembers(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.debug("Listing tenant members: tenant={}, requestedBy={}", tenantId, userId);
        
        return tenantMemberService.listTenantMembers(tenantId, userId)
            .flatMap(members -> enrichMembersWithRoles(members, tenantId))
            .map(enrichedMembers -> MemberListResponse.builder()
                .members(enrichedMembers)
                .totalCount(enrichedMembers.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Remove a user from a tenant (cascades to all projects).
     * Requires tenant admin role.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param targetUserId User ID to remove
     * @return Success response
     */
    @DELETE
    @Path("/{userId}")
    public CompletionStage<Response<Void>> removeMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            @PathParam("userId") String targetUserId) {
        
        String token = extractToken(authorization);
        String removerId = extractUserId(token);
        
        log.info("Removing member from tenant: user={}, tenant={}, removedBy={}", 
            targetUserId, tenantId, removerId);
        
        // Check if remover is tenant admin
        return openFgaService.isTenantAdmin(removerId, tenantId)
            .flatMapCompletable(isAdmin -> {
                if (!isAdmin) {
                    return io.reactivex.rxjava3.core.Completable.error(
                        ServiceError.SERVICE_UNKNOWN_EXCEPTION
                            .getCustomException("Unauthorized", "Only tenant admins can remove members"));
                }
                
                return tenantMemberService.removeUserFromTenant(
                    tenantId, targetUserId, removerId, "Tenant", "Admin"
                );
            })
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Update a member's role in a tenant.
     * Requires tenant owner role.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @param targetUserId User ID to update
     * @param request New role
     * @return Success response
     */
    @PATCH
    @Path("/{userId}")
    public CompletionStage<Response<Void>> updateMemberRole(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId,
            @PathParam("userId") String targetUserId,
            UpdateMemberRoleRequest request) {
        
        String token = extractToken(authorization);
        String updaterId = extractUserId(token);
        
        log.info("Updating tenant member role: user={}, tenant={}, newRole={}, updatedBy={}", 
            targetUserId, tenantId, request.getNewRole(), updaterId);
        
        // Check if updater is tenant owner
        return openFgaService.isTenantOwner(updaterId, tenantId)
            .flatMapCompletable(isOwner -> {
                if (!isOwner) {
                    return io.reactivex.rxjava3.core.Completable.error(
                        ServiceError.SERVICE_UNKNOWN_EXCEPTION
                            .getCustomException("Unauthorized", "Only tenant owners can update member roles"));
                }
                
                return tenantMemberService.updateTenantRole(
                    tenantId, targetUserId, request.getNewRole(), 
                    updaterId, "Tenant", "Admin"
                );
            })
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Leave a tenant (self-removal).
     * User removes themselves from the tenant.
     * 
     * @param authorization JWT token
     * @param tenantId Tenant ID
     * @return Success response
     */
    @POST
    @Path("/../leave")
    public CompletionStage<Response<Void>> leaveTenant(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("tenantId") String tenantId) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.info("User leaving tenant: user={}, tenant={}", userId, tenantId);
        
        return tenantMemberService.leaveTenant(tenantId, userId, "Tenant")
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Enrich members list with roles from OpenFGA
     */
    private Single<List<MemberResponse>> enrichMembersWithRoles(List<User> members, String tenantId) {
        if (members.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        List<Single<MemberResponse>> enrichments = new ArrayList<>();
        for (User member : members) {
            Single<MemberResponse> enriched = openFgaService.getUserTenantRole(member.getUserId(), tenantId)
                .map(roleOpt -> MemberResponse.builder()
                    .userId(member.getUserId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .role(roleOpt.orElse("member"))
                    .status(member.getStatus())
                    .lastLoginAt(member.getLastLoginAt())
                    .build());
            enrichments.add(enriched);
        }
        
        return Single.zip(enrichments, results -> {
            List<MemberResponse> responses = new ArrayList<>();
            for (Object result : results) {
                responses.add((MemberResponse) result);
            }
            return responses;
        });
    }
    
    /**
     * Extract JWT token from Authorization header
     */
    private String extractToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Missing authorization", "Authorization header required");
        }
        return authorization.substring(7);
    }
    
    /**
     * Extract user ID from JWT token
     */
    private String extractUserId(String token) {
        try {
            var claims = jwtService.verifyToken(token);
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                    .getCustomException("Invalid token", "User ID not found in token");
            }
            return userId;
        } catch (Exception e) {
            throw ServiceError.SERVICE_UNKNOWN_EXCEPTION
                .getCustomException("Invalid token", e.getMessage());
        }
    }
}
