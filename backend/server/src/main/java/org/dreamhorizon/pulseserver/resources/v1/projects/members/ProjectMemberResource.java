package org.dreamhorizon.pulseserver.resources.v1.projects.members;

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
import org.dreamhorizon.pulseserver.service.ProjectMemberService;
import org.dreamhorizon.pulseserver.service.ProjectService;
import org.dreamhorizon.pulseserver.dao.userdao.UserDao;

/**
 * REST resource for project member management.
 * Manages adding, removing, and listing members of a project.
 */
@Slf4j
@Path("/v1/projects/{projectId}/members")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectMemberResource {
    
    private final ProjectMemberService projectMemberService;
    private final ProjectService projectService;
    private final OpenFgaService openFgaService;
    private final JwtService jwtService;
    private final UserDao userDao;
    
    /**
     * Add a member to a project.
     * Requires project admin role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param request Member details (email, role)
     * @return Added member information
     */
    @POST
    public CompletionStage<Response<MemberResponse>> addMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            AddMemberRequest request) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.info("Adding member to project: email={}, project={}, role={}, addedBy={}", 
            request.getEmail(), projectId, request.getRole(), userId);
        
        // Check if user is project admin
        return openFgaService.isProjectAdmin(userId, projectId)
            .flatMap(isAdmin -> {
                if (!isAdmin) {
                    return Single.error(ServiceError.SERVICE_UNKNOWN_EXCEPTION
                        .getCustomException("Unauthorized", "Only project admins can add members"));
                }
                
                // Get project details and tenant ID
                return projectService.getProjectById(projectId)
                    .flatMap(project -> {
                        // Get admin name from user table (default if not found)
                        return userDao.getUserById(userId)
                            .defaultIfEmpty(User.builder()
                                .userId(userId)
                                .name("Admin")
                                .email("admin@example.com")
                                .build())
                            .flatMap(adminUser -> {
                                return projectMemberService.addMemberToProject(
                                    projectId, 
                                    request.getEmail(), 
                                    request.getRole(), 
                                    userId, 
                                    project.getName(), 
                                    adminUser.getName(),
                                    project.getTenantId(),
                                    "Tenant" // Placeholder for tenant name
                                );
                            });
                    });
            })
            .flatMap(user -> {
                // Get role from OpenFGA for response
                return openFgaService.getUserRoleInProject(user.getUserId(), projectId)
                    .map(roleOpt -> MemberResponse.builder()
                        .userId(user.getUserId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .role(roleOpt.orElse(request.getRole()))
                        .status(user.getStatus())
                        .profilePicture(user.getProfilePicture())
                        .lastLoginAt(user.getLastLoginAt())
                        .build());
            })
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * List all members of a project.
     * Requires project member role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @return List of project members
     */
    @GET
    public CompletionStage<Response<MemberListResponse>> listMembers(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.debug("Listing project members: project={}, requestedBy={}", projectId, userId);
        
        return projectMemberService.listProjectMembers(projectId, userId)
            .flatMap(members -> enrichMembersWithRoles(members, projectId))
            .map(enrichedMembers -> MemberListResponse.builder()
                .members(enrichedMembers)
                .totalCount(enrichedMembers.size())
                .build())
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Remove a member from a project.
     * Requires project admin role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param targetUserId User ID to remove
     * @return Success response
     */
    @DELETE
    @Path("/{userId}")
    public CompletionStage<Response<Void>> removeMember(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            @PathParam("userId") String targetUserId) {
        
        String token = extractToken(authorization);
        String removerId = extractUserId(token);
        
        log.info("Removing member from project: user={}, project={}, removedBy={}", 
            targetUserId, projectId, removerId);
        
        // Check if remover is project admin
        return openFgaService.isProjectAdmin(removerId, projectId)
            .flatMapCompletable(isAdmin -> {
                if (!isAdmin) {
                    return io.reactivex.rxjava3.core.Completable.error(
                        ServiceError.SERVICE_UNKNOWN_EXCEPTION
                            .getCustomException("Unauthorized", "Only project admins can remove members"));
                }
                
                // Get project details
                return projectService.getProjectById(projectId)
                    .flatMapCompletable(project -> {
                        return userDao.getUserById(removerId)
                            .defaultIfEmpty(User.builder()
                                .userId(removerId)
                                .name("Admin")
                                .email("admin@example.com")
                                .build())
                            .flatMapCompletable(adminUser -> {
                                return projectMemberService.removeMemberFromProject(
                                    projectId, targetUserId, removerId, project.getName(), adminUser.getName()
                                );
                            });
                    });
            })
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Update a member's role in a project.
     * Requires project admin role.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @param targetUserId User ID to update
     * @param request New role
     * @return Success response
     */
    @PATCH
    @Path("/{userId}")
    public CompletionStage<Response<Void>> updateMemberRole(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId,
            @PathParam("userId") String targetUserId,
            UpdateMemberRoleRequest request) {
        
        String token = extractToken(authorization);
        String updaterId = extractUserId(token);
        
        log.info("Updating project member role: user={}, project={}, newRole={}, updatedBy={}", 
            targetUserId, projectId, request.getNewRole(), updaterId);
        
        // Check if updater is project admin
        return openFgaService.isProjectAdmin(updaterId, projectId)
            .flatMapCompletable(isAdmin -> {
                if (!isAdmin) {
                    return io.reactivex.rxjava3.core.Completable.error(
                        ServiceError.SERVICE_UNKNOWN_EXCEPTION
                            .getCustomException("Unauthorized", "Only project admins can update member roles"));
                }
                
                // Get project details
                return projectService.getProjectById(projectId)
                    .flatMapCompletable(project -> {
                        return userDao.getUserById(updaterId)
                            .defaultIfEmpty(User.builder()
                                .userId(updaterId)
                                .name("Admin")
                                .email("admin@example.com")
                                .build())
                            .flatMapCompletable(adminUser -> {
                                return projectMemberService.updateMemberRole(
                                    projectId, targetUserId, request.getNewRole(), 
                                    updaterId, project.getName(), adminUser.getName()
                                );
                            });
                    });
            })
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Leave a project (self-removal).
     * User removes themselves from the project.
     * 
     * @param authorization JWT token
     * @param projectId Project ID
     * @return Success response
     */
    @POST
    @Path("/../leave")
    public CompletionStage<Response<Void>> leaveProject(
            @HeaderParam(HttpHeaders.AUTHORIZATION) String authorization,
            @PathParam("projectId") String projectId) {
        
        String token = extractToken(authorization);
        String userId = extractUserId(token);
        
        log.info("User leaving project: user={}, project={}", userId, projectId);
        
        return projectService.getProjectById(projectId)
            .flatMapCompletable(project -> {
                return projectMemberService.leaveProject(projectId, userId, project.getName());
            })
            .toSingleDefault((Void) null)
            .to(RestResponse.jaxrsRestHandler());
    }
    
    /**
     * Enrich members list with roles from OpenFGA
     */
    private Single<List<MemberResponse>> enrichMembersWithRoles(List<User> members, String projectId) {
        if (members.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        
        List<Single<MemberResponse>> enrichments = new ArrayList<>();
        for (User member : members) {
            Single<MemberResponse> enriched = openFgaService.getUserRoleInProject(member.getUserId(), projectId)
                .map(roleOpt -> MemberResponse.builder()
                    .userId(member.getUserId())
                    .email(member.getEmail())
                    .name(member.getName())
                    .role(roleOpt.orElse("viewer"))
                    .status(member.getStatus())
                    .profilePicture(member.getProfilePicture())
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
