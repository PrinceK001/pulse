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
 * Service for managing tenant memberships.
 * Uses OpenFGA as the single source of truth for all member relationships.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantMemberService {
    
    private final UserDao userDao;
    private final OpenFgaService openFgaService;
    private final EmailService emailService;
    
    /**
     * Add a user to a tenant with the specified role.
     * Creates a pending user if they don't exist.
     * 
     * @param tenantId Tenant ID
     * @param email User's email address
     * @param role Role to assign (owner, admin, member)
     * @param addedBy User ID of the person adding this user
     * @param tenantName Tenant name for email notification
     * @param adminName Admin's name for email notification
     * @return Single<User> The user that was added
     */
    public Single<User> addUserToTenant(String tenantId, String email, String role, 
                                         String addedBy, String tenantName, String adminName) {
        log.info("Adding user to tenant: email={}, tenant={}, role={}, addedBy={}", 
            email, tenantId, role, addedBy);
        
        // Validate role
        if (!isValidTenantRole(role)) {
            return Single.error(new IllegalArgumentException(
                "Invalid tenant role: " + role + ". Must be one of: owner, admin, member"));
        }
        
        // Get or create user
        return getOrCreateUserByEmail(email)
            .flatMap(user -> {
                // Assign role in OpenFGA
                return openFgaService.assignTenantRole(user.getUserId(), tenantId, role)
                    .andThen(Single.just(user));
            })
            .doOnSuccess(user -> {
                // Send welcome email
                try {
                    emailService.sendTenantWelcomeEmail(email, tenantName, role, adminName);
                } catch (Exception e) {
                    log.error("Failed to send welcome email to {}", email, e);
                }
                log.info("User added to tenant successfully: userId={}, tenant={}, role={}", 
                    user.getUserId(), tenantId, role);
            })
            .doOnError(error -> 
                log.error("Failed to add user to tenant: email={}, tenant={}", email, tenantId, error)
            );
    }
    
    /**
     * Remove a user from a tenant (cascades to all projects in tenant).
     * Validates that we're not removing the last owner.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID to remove
     * @param removedBy User ID of the person performing the removal
     * @param tenantName Tenant name for email notification
     * @param adminName Admin's name for email notification
     * @return Completable Completes when user is removed
     */
    public Completable removeUserFromTenant(String tenantId, String userId, String removedBy, 
                                             String tenantName, String adminName) {
        log.info("Removing user from tenant: user={}, tenant={}, removedBy={}", 
            userId, tenantId, removedBy);
        
        // Check if user is an owner
        return openFgaService.isTenantOwner(userId, tenantId)
            .flatMapCompletable(isOwner -> {
                if (isOwner) {
                    // Validate not removing last owner
                    return openFgaService.countTenantOwners(tenantId)
                        .flatMapCompletable(ownerCount -> {
                            if (ownerCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot remove the last owner from tenant. " +
                                    "Assign another owner first or delete the tenant."));
                            }
                            return removeUserFromTenantInternal(tenantId, userId, tenantName, adminName);
                        });
                } else {
                    return removeUserFromTenantInternal(tenantId, userId, tenantName, adminName);
                }
            })
            .doOnComplete(() -> 
                log.info("User removed from tenant successfully: user={}, tenant={}", userId, tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to remove user from tenant: user={}, tenant={}", userId, tenantId, error)
            );
    }
    
    private Completable removeUserFromTenantInternal(String tenantId, String userId, 
                                                       String tenantName, String adminName) {
        return openFgaService.removeTenantMember(userId, tenantId)
            .doOnComplete(() -> {
                // Get user email for notification
                userDao.getUserById(userId)
                    .subscribe(
                        user -> {
                            try {
                                emailService.sendAccessRemovedEmail(user.getEmail(), tenantName, adminName);
                            } catch (Exception e) {
                                log.error("Failed to send removal email to {}", user.getEmail(), e);
                            }
                        },
                        error -> log.error("Failed to fetch user for email notification: {}", userId, error)
                    );
            });
    }
    
    /**
     * User leaves a tenant (self-removal).
     * Validates that they're not the last owner.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID
     * @param tenantName Tenant name for logging
     * @return Completable Completes when user leaves
     */
    public Completable leaveTenant(String tenantId, String userId, String tenantName) {
        log.info("User leaving tenant: user={}, tenant={}", userId, tenantId);
        
        return openFgaService.isTenantOwner(userId, tenantId)
            .flatMapCompletable(isOwner -> {
                if (isOwner) {
                    return openFgaService.countTenantOwners(tenantId)
                        .flatMapCompletable(ownerCount -> {
                            if (ownerCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot leave tenant as the last owner. " +
                                    "Transfer ownership first or delete the tenant."));
                            }
                            return openFgaService.removeTenantMember(userId, tenantId);
                        });
                } else {
                    return openFgaService.removeTenantMember(userId, tenantId);
                }
            })
            .doOnComplete(() -> 
                log.info("User left tenant successfully: user={}, tenant={}", userId, tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to leave tenant: user={}, tenant={}", userId, tenantId, error)
            );
    }
    
    /**
     * Update a user's role in a tenant.
     * 
     * @param tenantId Tenant ID
     * @param userId User ID
     * @param newRole New role
     * @param updatedBy User ID of the person updating
     * @param tenantName Tenant name for email
     * @param adminName Admin's name for email
     * @return Completable Completes when role is updated
     */
    public Completable updateTenantRole(String tenantId, String userId, String newRole, 
                                         String updatedBy, String tenantName, String adminName) {
        log.info("Updating tenant role: user={}, tenant={}, newRole={}, updatedBy={}", 
            userId, tenantId, newRole, updatedBy);
        
        // Validate role
        if (!isValidTenantRole(newRole)) {
            return Completable.error(new IllegalArgumentException(
                "Invalid tenant role: " + newRole + ". Must be one of: owner, admin, member"));
        }
        
        // Check if downgrading from owner
        return openFgaService.isTenantOwner(userId, tenantId)
            .flatMapCompletable(isCurrentlyOwner -> {
                if (isCurrentlyOwner && !"owner".equals(newRole)) {
                    // Downgrading from owner - check owner count
                    return openFgaService.countTenantOwners(tenantId)
                        .flatMapCompletable(ownerCount -> {
                            if (ownerCount <= 1) {
                                return Completable.error(new IllegalStateException(
                                    "Cannot downgrade the last owner. Assign another owner first."));
                            }
                            return updateRoleInternal(tenantId, userId, newRole, tenantName, adminName);
                        });
                } else {
                    return updateRoleInternal(tenantId, userId, newRole, tenantName, adminName);
                }
            })
            .doOnComplete(() -> 
                log.info("Tenant role updated successfully: user={}, tenant={}, newRole={}", 
                    userId, tenantId, newRole)
            )
            .doOnError(error -> 
                log.error("Failed to update tenant role: user={}, tenant={}", userId, tenantId, error)
            );
    }
    
    private Completable updateRoleInternal(String tenantId, String userId, String newRole, 
                                            String tenantName, String adminName) {
        return openFgaService.updateTenantRole(userId, tenantId, newRole)
            .doOnComplete(() -> {
                // Send notification
                userDao.getUserById(userId)
                    .subscribe(
                        user -> {
                            try {
                                emailService.sendRoleUpdatedEmail(user.getEmail(), tenantName, newRole, adminName);
                            } catch (Exception e) {
                                log.error("Failed to send role update email to {}", user.getEmail(), e);
                            }
                        },
                        error -> log.error("Failed to fetch user for email notification: {}", userId, error)
                    );
            });
    }
    
    /**
     * List all members of a tenant with their roles.
     * Queries OpenFGA for user IDs and enriches with user data from DB.
     * 
     * @param tenantId Tenant ID
     * @param requesterId User ID requesting the list (for authorization)
     * @return Single<List<User>> List of users with access to tenant
     */
    public Single<List<User>> listTenantMembers(String tenantId, String requesterId) {
        log.debug("Listing tenant members: tenant={}, requestedBy={}", tenantId, requesterId);
        
        return openFgaService.getTenantMembers(tenantId)
            .flatMap(userIds -> {
                if (userIds.isEmpty()) {
                    return Single.just(new ArrayList<User>());
                }
                
                // Fetch user details from database
                return userDao.getUsersByIds(new ArrayList<>(userIds));
            })
            .doOnSuccess(members -> 
                log.debug("Retrieved {} tenant members for tenant: {}", members.size(), tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to list tenant members: tenant={}", tenantId, error)
            );
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
     * Validate tenant role
     */
    private boolean isValidTenantRole(String role) {
        return "owner".equals(role) || "admin".equals(role) || "member".equals(role);
    }
}
