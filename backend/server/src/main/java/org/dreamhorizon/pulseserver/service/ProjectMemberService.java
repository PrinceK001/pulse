package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.userdao.UserDao;
import org.dreamhorizon.pulseserver.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing project memberships.
 * Uses OpenFGA as the single source of truth for all member relationships.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectMemberService {
    
    private final UserDao userDao;
    private final OpenFgaService openFgaService;
    private final EmailService emailService;
    private final TenantMemberService tenantMemberService;
    
    /**
     * Add a member to a project with the specified role.
     * Ensures user is also added to the parent tenant if not already a member.
     * 
     * @param projectId Project ID
     * @param email User's email address
     * @param role Role to assign (admin, editor, viewer)
     * @param addedBy User ID of the person adding this user
     * @param projectName Project name for email notification
     * @param adminName Admin's name for email notification
     * @param tenantId Parent tenant ID
     * @param tenantName Tenant name for email notification
     * @return Single<User> The user that was added
     */
    public Single<User> addMemberToProject(String projectId, String email, String role, 
                                            String addedBy, String projectName, String adminName,
                                            String tenantId, String tenantName) {
        log.info("Adding member to project: email={}, project={}, role={}, addedBy={}", 
            email, projectId, role, addedBy);
        
        // Validate role
        if (!isValidProjectRole(role)) {
            return Single.error(new IllegalArgumentException(
                "Invalid project role: " + role + ". Must be one of: admin, editor, viewer"));
        }
        
        // Get or create user
        return getOrCreateUserByEmail(email)
            .flatMap(user -> {
                // Ensure user is in tenant (add as member if not present)
                return ensureUserInTenant(user, tenantId, tenantName, adminName)
                    .andThen(Single.just(user));
            })
            .flatMap(user -> {
                // Assign project role in OpenFGA
                return openFgaService.assignProjectRole(user.getUserId(), projectId, role)
                    .andThen(Single.just(user));
            })
            .doOnSuccess(user -> {
                // Send notification email
                try {
                    emailService.sendProjectAccessEmail(email, projectName, role, adminName, projectId);
                } catch (Exception e) {
                    log.error("Failed to send project access email to {}", email, e);
                }
                log.info("Member added to project successfully: userId={}, project={}, role={}", 
                    user.getUserId(), projectId, role);
            })
            .doOnError(error -> 
                log.error("Failed to add member to project: email={}, project={}", email, projectId, error)
            );
    }
    
    /**
     * Remove a member from a project.
     * Validates that we're not removing the last admin.
     * 
     * @param projectId Project ID
     * @param userId User ID to remove
     * @param removedBy User ID of the person performing the removal
     * @param projectName Project name for email notification
     * @param adminName Admin's name for email notification
     * @return Completable Completes when member is removed
     */
    public Completable removeMemberFromProject(String projectId, String userId, String removedBy, 
                                                String projectName, String adminName) {
        log.info("Removing member from project: user={}, project={}, removedBy={}", 
            userId, projectId, removedBy);
        
        // Check if user is an admin
        return openFgaService.isProjectAdmin(userId, projectId)
            .flatMapCompletable(isAdmin -> {
                if (isAdmin) {
                    // Validate not removing last admin
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMapCompletable(adminCount -> {
                            if (adminCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot remove the last admin from project. " +
                                    "Assign another admin first or delete the project."));
                            }
                            return removeMemberInternal(projectId, userId, projectName, adminName);
                        });
                } else {
                    return removeMemberInternal(projectId, userId, projectName, adminName);
                }
            })
            .doOnComplete(() -> 
                log.info("Member removed from project successfully: user={}, project={}", userId, projectId)
            )
            .doOnError(error -> 
                log.error("Failed to remove member from project: user={}, project={}", userId, projectId, error)
            );
    }
    
    private Completable removeMemberInternal(String projectId, String userId, 
                                              String projectName, String adminName) {
        return openFgaService.removeProjectMember(userId, projectId)
            .doOnComplete(() -> {
                // Get user email for notification
                userDao.getUserById(userId)
                    .subscribe(
                        user -> {
                            try {
                                emailService.sendAccessRemovedEmail(user.getEmail(), projectName, adminName);
                            } catch (Exception e) {
                                log.error("Failed to send removal email to {}", user.getEmail(), e);
                            }
                        },
                        error -> log.error("Failed to fetch user for email notification: {}", userId, error)
                    );
            });
    }
    
    /**
     * User leaves a project (self-removal).
     * Validates that they're not the last admin.
     * 
     * @param projectId Project ID
     * @param userId User ID
     * @param projectName Project name for logging
     * @return Completable Completes when user leaves
     */
    public Completable leaveProject(String projectId, String userId, String projectName) {
        log.info("User leaving project: user={}, project={}", userId, projectId);
        
        return openFgaService.isProjectAdmin(userId, projectId)
            .flatMapCompletable(isAdmin -> {
                if (isAdmin) {
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMapCompletable(adminCount -> {
                            if (adminCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot leave project as the last admin. " +
                                    "Transfer admin role first or delete the project."));
                            }
                            return openFgaService.removeProjectMember(userId, projectId);
                        });
                } else {
                    return openFgaService.removeProjectMember(userId, projectId);
                }
            })
            .doOnComplete(() -> 
                log.info("User left project successfully: user={}, project={}", userId, projectId)
            )
            .doOnError(error -> 
                log.error("Failed to leave project: user={}, project={}", userId, projectId, error)
            );
    }
    
    /**
     * Update a member's role in a project.
     * 
     * @param projectId Project ID
     * @param userId User ID
     * @param newRole New role
     * @param updatedBy User ID of the person updating
     * @param projectName Project name for email
     * @param adminName Admin's name for email
     * @return Completable Completes when role is updated
     */
    public Completable updateMemberRole(String projectId, String userId, String newRole, 
                                         String updatedBy, String projectName, String adminName) {
        log.info("Updating project role: user={}, project={}, newRole={}, updatedBy={}", 
            userId, projectId, newRole, updatedBy);
        
        // Validate role
        if (!isValidProjectRole(newRole)) {
            return Completable.error(new IllegalArgumentException(
                "Invalid project role: " + newRole + ". Must be one of: admin, editor, viewer"));
        }
        
        // Check if downgrading from admin
        return openFgaService.isProjectAdmin(userId, projectId)
            .flatMapCompletable(isCurrentlyAdmin -> {
                if (isCurrentlyAdmin && !"admin".equals(newRole)) {
                    // Downgrading from admin - check admin count
                    return openFgaService.countProjectAdmins(projectId)
                        .flatMapCompletable(adminCount -> {
                            if (adminCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot downgrade the last admin. Assign another admin first."));
                            }
                            return updateRoleInternal(projectId, userId, newRole, projectName, adminName);
                        });
                } else {
                    return updateRoleInternal(projectId, userId, newRole, projectName, adminName);
                }
            })
            .doOnComplete(() -> 
                log.info("Project role updated successfully: user={}, project={}, newRole={}", 
                    userId, projectId, newRole)
            )
            .doOnError(error -> 
                log.error("Failed to update project role: user={}, project={}", userId, projectId, error)
            );
    }
    
    private Completable updateRoleInternal(String projectId, String userId, String newRole, 
                                            String projectName, String adminName) {
        return openFgaService.updateProjectRole(userId, projectId, newRole)
            .doOnComplete(() -> {
                // Send notification
                userDao.getUserById(userId)
                    .subscribe(
                        user -> {
                            try {
                                emailService.sendRoleUpdatedEmail(user.getEmail(), projectName, newRole, adminName);
                            } catch (Exception e) {
                                log.error("Failed to send role update email to {}", user.getEmail(), e);
                            }
                        },
                        error -> log.error("Failed to fetch user for email notification: {}", userId, error)
                    );
            });
    }
    
    /**
     * List all members of a project with their roles.
     * Queries OpenFGA for user IDs and enriches with user data from DB.
     * 
     * @param projectId Project ID
     * @param requesterId User ID requesting the list (for authorization)
     * @return Single<List<User>> List of users with access to project
     */
    public Single<List<User>> listProjectMembers(String projectId, String requesterId) {
        log.debug("Listing project members: project={}, requestedBy={}", projectId, requesterId);
        
        return openFgaService.getProjectMembers(projectId)
            .flatMap(userIds -> {
                if (userIds.isEmpty()) {
                    return Single.just(new ArrayList<User>());
                }
                
                // Fetch user details from database
                return userDao.getUsersByIds(new ArrayList<>(userIds));
            })
            .doOnSuccess(members -> 
                log.debug("Retrieved {} project members for project: {}", members.size(), projectId)
            )
            .doOnError(error -> 
                log.error("Failed to list project members: project={}", projectId, error)
            );
    }
    
    /**
     * Ensure user is in the parent tenant.
     * If not, add them as a member.
     */
    private Completable ensureUserInTenant(User user, String tenantId, String tenantName, String adminName) {
        return openFgaService.getUserTenantRole(user.getUserId(), tenantId)
            .flatMapCompletable(roleOpt -> {
                if (roleOpt.isPresent()) {
                    // User already in tenant
                    log.debug("User already in tenant: user={}, tenant={}, role={}", 
                        user.getUserId(), tenantId, roleOpt.get());
                    return Completable.complete();
                } else {
                    // Add user to tenant as member
                    log.info("Auto-adding user to tenant as member: user={}, tenant={}", 
                        user.getUserId(), tenantId);
                    return tenantMemberService.addUserToTenant(
                        tenantId, user.getEmail(), "member", "system", tenantName, adminName
                    ).ignoreElement();
                }
            });
    }
    
    /**
     * Get or create a user by email.
     * If user doesn't exist, creates a pending user (to be activated on first login).
     * 
     * @param email User email
     * @return Single<User> Existing or newly created user
     */
    private Single<User> getOrCreateUserByEmail(String email) {
        return userDao.getUserByEmail(email)
            .switchIfEmpty(Single.defer(() -> {
                String userId = "user-" + UUID.randomUUID().toString();
                User newUser = User.builder()
                    .userId(userId)
                    .email(email)
                    .name(email) // Use email as temporary name
                    .status("pending") // Will be activated on first login
                    .isActive(true)
                    .build();
                
                log.info("Creating pending user: email={}, userId={}", email, userId);
                return userDao.createUser(newUser);
            }));
    }
    
    /**
     * Validate project role
     */
    private boolean isValidProjectRole(String role) {
        return "admin".equals(role) || "editor".equals(role) || "viewer".equals(role);
    }
}
