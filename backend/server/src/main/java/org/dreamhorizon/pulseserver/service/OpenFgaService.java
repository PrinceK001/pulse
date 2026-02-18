package org.dreamhorizon.pulseserver.service;

import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DUMMY OpenFGA Service - For development/testing with in-memory store.
 * Maintains user-project-tenant relationships for testing without real OpenFGA.
 * 
 * This implementation:
 * - Stores role assignments in memory
 * - Returns actual projects after assignment
 * - Allows testing the full authentication flow
 * - Provides member listing with reverse lookups
 * - Supports cascading deletes
 * 
 * TODO: Replace this with real OpenFGA implementation before production!
 */
@Slf4j
@Singleton
public class OpenFgaService {
    
    // In-memory store for testing
    // Map<userId, Map<projectId, role>>
    private final Map<String, Map<String, String>> userProjectRoles = new ConcurrentHashMap<>();
    
    // Map<userId, Map<tenantId, role>>
    private final Map<String, Map<String, String>> userTenantRoles = new ConcurrentHashMap<>();
    
    // Map<projectId, tenantId>
    private final Map<String, String> projectTenants = new ConcurrentHashMap<>();
    
    // Reverse lookup maps for efficient member listing
    // Map<tenantId, Set<userId>>
    private final Map<String, Set<String>> tenantMembers = new ConcurrentHashMap<>();
    
    // Map<projectId, Set<userId>>
    private final Map<String, Set<String>> projectMembers = new ConcurrentHashMap<>();
    
    public OpenFgaService() {
        log.warn("⚠️  Using DUMMY OpenFGA Service with in-memory store!");
        log.warn("⚠️  This is for testing only - replace with real OpenFGA before production!");
    }
    
    /**
     * DUMMY: Always returns true
     * 
     * @param userId User ID
     * @param action Action/relation to check (e.g., "view", "edit", "delete")
     * @param resourceType Resource type (e.g., "project", "tenant")
     * @param resourceId Resource ID
     * @return Single<Boolean> Always true in dummy implementation
     */
    public Single<Boolean> checkPermission(String userId, String action, String resourceType, String resourceId) {
        log.debug("[DUMMY] Permission check: user={}, action={}, resource={}:{} -> ALLOWED", 
            userId, action, resourceType, resourceId);
        return Single.just(true);
    }
    
    /**
     * DUMMY: Stores role assignment in memory with reverse lookup
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @param role Role (e.g., "owner", "admin", "member")
     * @return Completable Always completes successfully
     */
    public Completable assignTenantRole(String userId, String tenantId, String role) {
        log.info("[DUMMY] Assigning tenant role: user={}, tenant={}, role={}", userId, tenantId, role);
        userTenantRoles.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(tenantId, role);
        tenantMembers.computeIfAbsent(tenantId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        return Completable.complete();
    }
    
    /**
     * DUMMY: Stores role assignment in memory with reverse lookup
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @param role Role (e.g., "admin", "editor", "viewer")
     * @return Completable Always completes successfully
     */
    public Completable assignProjectRole(String userId, String projectId, String role) {
        log.info("[DUMMY] Assigning project role: user={}, project={}, role={}", userId, projectId, role);
        userProjectRoles.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(projectId, role);
        projectMembers.computeIfAbsent(projectId, k -> ConcurrentHashMap.newKeySet()).add(userId);
        return Completable.complete();
    }
    
    /**
     * DUMMY: Updates role in memory
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @param newRole New role
     * @return Completable Always completes successfully
     */
    public Completable updateProjectRole(String userId, String projectId, String newRole) {
        log.info("[DUMMY] Updating project role: user={}, project={}, newRole={}", userId, projectId, newRole);
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null && projects.containsKey(projectId)) {
            projects.put(projectId, newRole);
        }
        return Completable.complete();
    }
    
    /**
     * DUMMY: Updates tenant role in memory
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @param newRole New role
     * @return Completable Always completes successfully
     */
    public Completable updateTenantRole(String userId, String tenantId, String newRole) {
        log.info("[DUMMY] Updating tenant role: user={}, tenant={}, newRole={}", userId, tenantId, newRole);
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null && tenants.containsKey(tenantId)) {
            tenants.put(tenantId, newRole);
        }
        return Completable.complete();
    }
    
    /**
     * DUMMY: Returns role from in-memory store
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @return Single<Optional<String>> Role if assigned, empty otherwise
     */
    public Single<Optional<String>> getUserRoleInProject(String userId, String projectId) {
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null && projects.containsKey(projectId)) {
            String role = projects.get(projectId);
            log.debug("[DUMMY] User role in project: user={}, project={} -> {}", userId, projectId, role);
            return Single.just(Optional.of(role));
        }
        log.debug("[DUMMY] User role in project: user={}, project={} -> no role", userId, projectId);
        return Single.just(Optional.empty());
    }
    
    /**
     * DUMMY: Returns tenant role from in-memory store
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<Optional<String>> Role if assigned, empty otherwise
     */
    public Single<Optional<String>> getUserTenantRole(String userId, String tenantId) {
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null && tenants.containsKey(tenantId)) {
            String role = tenants.get(tenantId);
            log.debug("[DUMMY] User role in tenant: user={}, tenant={} -> {}", userId, tenantId, role);
            return Single.just(Optional.of(role));
        }
        log.debug("[DUMMY] User role in tenant: user={}, tenant={} -> no role", userId, tenantId);
        return Single.just(Optional.empty());
    }
    
    /**
     * DUMMY: Returns tenants from in-memory store
     * 
     * @param userId User ID
     * @return Single<List<String>> List of tenant IDs
     */
    public Single<List<String>> getUserTenants(String userId) {
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null && !tenants.isEmpty()) {
            List<String> tenantIds = new ArrayList<>(tenants.keySet());
            log.debug("[DUMMY] User tenants: user={} -> {} tenant(s)", userId, tenantIds.size());
            return Single.just(tenantIds);
        }
        log.debug("[DUMMY] User tenants: user={} -> empty list", userId);
        return Single.just(new ArrayList<>());
    }
    
    /**
     * DUMMY: Returns all members of a tenant (reverse lookup)
     * 
     * @param tenantId Tenant ID
     * @return Single<Set<String>> Set of user IDs with access to tenant
     */
    public Single<Set<String>> getTenantMembers(String tenantId) {
        Set<String> members = tenantMembers.get(tenantId);
        if (members != null && !members.isEmpty()) {
            log.debug("[DUMMY] Get tenant members: tenant={} -> {} member(s)", tenantId, members.size());
            return Single.just(new HashSet<>(members));
        }
        log.debug("[DUMMY] Get tenant members: tenant={} -> empty set", tenantId);
        return Single.just(new HashSet<>());
    }
    
    /**
     * DUMMY: Returns all members of a project (reverse lookup)
     * 
     * @param projectId Project ID
     * @return Single<Set<String>> Set of user IDs with access to project
     */
    public Single<Set<String>> getProjectMembers(String projectId) {
        Set<String> members = projectMembers.get(projectId);
        if (members != null && !members.isEmpty()) {
            log.debug("[DUMMY] Get project members: project={} -> {} member(s)", projectId, members.size());
            return Single.just(new HashSet<>(members));
        }
        log.debug("[DUMMY] Get project members: project={} -> empty set", projectId);
        return Single.just(new HashSet<>());
    }
    
    /**
     * DUMMY: Removes user from project in memory (including reverse lookup)
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @return Completable Always completes successfully
     */
    public Completable removeProjectMember(String userId, String projectId) {
        log.info("[DUMMY] Removing user from project: user={}, project={}", userId, projectId);
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null) {
            projects.remove(projectId);
        }
        Set<String> members = projectMembers.get(projectId);
        if (members != null) {
            members.remove(userId);
        }
        return Completable.complete();
    }
    
    /**
     * DUMMY: Removes user from tenant and all tenant projects (cascading delete)
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Completable Always completes successfully
     */
    public Completable removeTenantMember(String userId, String tenantId) {
        log.info("[DUMMY] Removing user from tenant (with cascade): user={}, tenant={}", userId, tenantId);
        
        // Remove from tenant
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null) {
            tenants.remove(tenantId);
        }
        Set<String> tenantMemberSet = tenantMembers.get(tenantId);
        if (tenantMemberSet != null) {
            tenantMemberSet.remove(userId);
        }
        
        // Cascade: Remove from all projects in this tenant
        List<String> projectsInTenant = getProjectsInTenant(tenantId).blockingGet();
        for (String projectId : projectsInTenant) {
            removeProjectMember(userId, projectId).blockingAwait();
        }
        
        return Completable.complete();
    }
    
    /**
     * DUMMY: Returns all projects in a tenant
     * 
     * @param tenantId Tenant ID
     * @return Single<List<String>> List of project IDs
     */
    public Single<List<String>> getProjectsInTenant(String tenantId) {
        List<String> projects = projectTenants.entrySet().stream()
            .filter(entry -> tenantId.equals(entry.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        log.debug("[DUMMY] Get projects in tenant: tenant={} -> {} project(s)", tenantId, projects.size());
        return Single.just(projects);
    }
    
    /**
     * DUMMY: Counts owners in a tenant
     * 
     * @param tenantId Tenant ID
     * @return Single<Integer> Number of owners
     */
    public Single<Integer> countTenantOwners(String tenantId) {
        Set<String> members = tenantMembers.get(tenantId);
        if (members == null || members.isEmpty()) {
            return Single.just(0);
        }
        
        int ownerCount = 0;
        for (String userId : members) {
            Map<String, String> tenants = userTenantRoles.get(userId);
            if (tenants != null && "owner".equals(tenants.get(tenantId))) {
                ownerCount++;
            }
        }
        
        log.debug("[DUMMY] Count tenant owners: tenant={} -> {} owner(s)", tenantId, ownerCount);
        return Single.just(ownerCount);
    }
    
    /**
     * DUMMY: Counts admins in a project
     * 
     * @param projectId Project ID
     * @return Single<Integer> Number of admins
     */
    public Single<Integer> countProjectAdmins(String projectId) {
        Set<String> members = projectMembers.get(projectId);
        if (members == null || members.isEmpty()) {
            return Single.just(0);
        }
        
        int adminCount = 0;
        for (String userId : members) {
            Map<String, String> projects = userProjectRoles.get(userId);
            if (projects != null && "admin".equals(projects.get(projectId))) {
                adminCount++;
            }
        }
        
        log.debug("[DUMMY] Count project admins: project={} -> {} admin(s)", projectId, adminCount);
        return Single.just(adminCount);
    }
    
    /**
     * DUMMY: Checks if user is tenant owner
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<Boolean> True if owner
     */
    public Single<Boolean> isTenantOwner(String userId, String tenantId) {
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null) {
            boolean isOwner = "owner".equals(tenants.get(tenantId));
            log.debug("[DUMMY] Is tenant owner: user={}, tenant={} -> {}", userId, tenantId, isOwner);
            return Single.just(isOwner);
        }
        return Single.just(false);
    }
    
    /**
     * DUMMY: Checks if user is project admin
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @return Single<Boolean> True if admin
     */
    public Single<Boolean> isProjectAdmin(String userId, String projectId) {
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null) {
            boolean isAdmin = "admin".equals(projects.get(projectId));
            log.debug("[DUMMY] Is project admin: user={}, project={} -> {}", userId, projectId, isAdmin);
            return Single.just(isAdmin);
        }
        return Single.just(false);
    }
    
    /**
     * DUMMY: Stores project-tenant link in memory
     * 
     * @param projectId Project ID
     * @param tenantId Tenant ID
     * @return Completable Always completes successfully
     */
    public Completable linkProjectToTenant(String projectId, String tenantId) {
        log.info("[DUMMY] Linking project to tenant: project={}, tenant={}", projectId, tenantId);
        projectTenants.put(projectId, tenantId);
        return Completable.complete();
    }
    
    /**
     * DUMMY: Checks tenant admin role from memory
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<Boolean> True if user has admin/owner role
     */
    public Single<Boolean> isTenantAdmin(String userId, String tenantId) {
        Map<String, String> tenants = userTenantRoles.get(userId);
        if (tenants != null) {
            String role = tenants.get(tenantId);
            boolean isAdmin = "owner".equals(role) || "admin".equals(role);
            log.debug("[DUMMY] Is tenant admin: user={}, tenant={}, role={} -> {}", 
                userId, tenantId, role, isAdmin);
            return Single.just(isAdmin);
        }
        log.debug("[DUMMY] Is tenant admin: user={}, tenant={} -> false (no role)", userId, tenantId);
        return Single.just(false);
    }
    
    /**
     * DUMMY: Returns user's projects from in-memory store
     * 
     * @param userId User ID
     * @return Single<List<String>> List of project IDs user has access to
     */
    public Single<List<String>> getUserProjects(String userId) {
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null && !projects.isEmpty()) {
            List<String> projectIds = new ArrayList<>(projects.keySet());
            log.info("[DUMMY] Get user projects: user={} -> {} project(s): {}", 
                userId, projectIds.size(), projectIds);
            return Single.just(projectIds);
        }
        log.info("[DUMMY] Get user projects: user={} -> empty list (triggers onboarding)", userId);
        return Single.just(new ArrayList<>());
    }
    
    /**
     * DUMMY: Returns user's projects in tenant from in-memory store
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<List<String>> List of project IDs in the specified tenant
     */
    public Single<List<String>> getUserProjectsInTenant(String userId, String tenantId) {
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null && !projects.isEmpty()) {
            List<String> filteredProjects = projects.keySet().stream()
                .filter(projectId -> tenantId.equals(projectTenants.get(projectId)))
                .collect(Collectors.toList());
            log.debug("[DUMMY] Get user projects in tenant: user={}, tenant={} -> {} project(s)", 
                userId, tenantId, filteredProjects.size());
            return Single.just(filteredProjects);
        }
        log.debug("[DUMMY] Get user projects in tenant: user={}, tenant={} -> empty list", userId, tenantId);
        return Single.just(new ArrayList<>());
    }
    
    /**
     * DUMMY: Returns tenant from in-memory store
     * 
     * @param projectId Project ID
     * @return Single<String> Tenant ID
     */
    public Single<String> getProjectTenant(String projectId) {
        String tenantId = projectTenants.get(projectId);
        if (tenantId != null) {
            log.debug("[DUMMY] Get project tenant: project={} -> {}", projectId, tenantId);
            return Single.just(tenantId);
        }
        // Fallback to mock ID if not found
        String mockTenantId = "tenant-mock-" + projectId.substring(5, 10);
        log.debug("[DUMMY] Get project tenant: project={} -> {} (mock)", projectId, mockTenantId);
        return Single.just(mockTenantId);
    }
    
    /**
     * DUMMY: Checks role from in-memory store
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @param role Role to check
     * @return Single<Boolean> True if user has the specified role
     */
    public Single<Boolean> hasProjectRole(String userId, String projectId, String role) {
        Map<String, String> projects = userProjectRoles.get(userId);
        if (projects != null) {
            String userRole = projects.get(projectId);
            boolean hasRole = role.equals(userRole);
            log.debug("[DUMMY] Has project role: user={}, project={}, role={} -> {} (user role: {})", 
                userId, projectId, role, hasRole, userRole);
            return Single.just(hasRole);
        }
        log.debug("[DUMMY] Has project role: user={}, project={}, role={} -> false (no assignment)", 
            userId, projectId, role);
        return Single.just(false);
    }
    
    // Backward compatibility - keeping old method name
    @Deprecated
    public Completable removeUserFromProject(String userId, String projectId) {
        return removeProjectMember(userId, projectId);
    }
}
