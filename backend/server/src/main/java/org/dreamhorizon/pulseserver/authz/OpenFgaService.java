package org.dreamhorizon.pulseserver.authz;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.Tuple;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;

import java.util.List;
import java.util.Optional;

/**
 * Service for interacting with OpenFGA for Relationship-Based Access Control (ReBAC).
 *
 * <p>This service provides methods for:</p>
 * <ul>
 *   <li>Checking permissions (can user X do action Y on resource Z?)</li>
 *   <li>Writing relationship tuples (user X is admin of tenant Y)</li>
 *   <li>Managing hierarchical relationships (project belongs to tenant)</li>
 * </ul>
 */
@Slf4j
@Singleton
public class OpenFgaService {

  private final OpenFgaClient client;
  private final String storeId;
  private final String authorizationModelId;

  @Inject
  public OpenFgaService(OpenFgaConfig config) throws Exception {
    ClientConfiguration configuration = new ClientConfiguration()
        .apiUrl(config.getApiUrl())
        .storeId(config.getStoreId())
        .authorizationModelId(config.getAuthorizationModelId());

    this.client = new OpenFgaClient(configuration);
    this.storeId = config.getStoreId();
    this.authorizationModelId = config.getAuthorizationModelId();
    log.info("OpenFGA client initialized - store: {}, model: {}", storeId, authorizationModelId);
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // PERMISSION CHECKS
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Check if a user has a specific permission on an object.
   *
   * @param userId     User identifier (e.g., email or Firebase UID)
   * @param relation   The relation/permission to check (e.g., "can_view", "can_edit")
   * @param objectType The type of object (e.g., "tenant", "project", "alert")
   * @param objectId   The object identifier
   * @return Single emitting true if allowed, false otherwise
   */
  public Single<Boolean> check(String userId, String relation, String objectType, String objectId) {
    return Single.fromCallable(() -> {
      ClientCheckRequest request = new ClientCheckRequest()
          .user("user:" + userId)
          .relation(relation)
          ._object(objectType + ":" + objectId);

      var response = client.check(request).get();
      boolean allowed = Boolean.TRUE.equals(response.getAllowed());
      log.debug("Permission check: user:{} {} {}:{} = {}",
          userId, relation, objectType, objectId, allowed);
      return allowed;
    });
  }

  /**
   * Check if user can view a resource.
   */
  public Single<Boolean> canView(String userId, String objectType, String objectId) {
    return check(userId, "can_view", objectType, objectId);
  }

  /**
   * Check if user can edit a resource.
   */
  public Single<Boolean> canEdit(String userId, String objectType, String objectId) {
    return check(userId, "can_edit", objectType, objectId);
  }

  /**
   * Check if user can delete a resource.
   */
  public Single<Boolean> canDelete(String userId, String objectType, String objectId) {
    return check(userId, "can_delete", objectType, objectId);
  }

  /**
   * Check if user can manage tenant settings.
   */
  public Single<Boolean> canManageTenant(String userId, String tenantId) {
    return check(userId, "can_manage_tenant", "tenant", tenantId);
  }

  /**
   * Check if user can manage project settings.
   */
  public Single<Boolean> canManageProject(String userId, String projectId) {
    return check(userId, "can_manage_settings", "project", projectId);
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // TENANT LOOKUP
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Find the tenant ID that a user belongs to.
   * Queries OpenFGA for relations where user has 'admin' or 'member' relation to a tenant.
   *
   * @param userEmail The user's email address
   * @return Maybe emitting the tenant ID if found, empty if user has no tenant relations
   */
  public Maybe<String> getTenantForUser(String userEmail) {
    return Maybe.fromCallable(() -> {
      String userIdentifier = "user:" + userEmail;
      
      // Query for tuples where this user has any relation to any tenant
      // We check both 'admin' and 'member' relations
      ClientReadRequest request = new ClientReadRequest()
          .user(userIdentifier);
      
      var response = client.read(request).get();
      var tuples = response.getTuples();
      
      if (tuples == null || tuples.isEmpty()) {
        log.debug("No relations found for user: {}", userEmail);
        return null;
      }
      
      // Find the first tenant relation (admin or member)
      Optional<String> tenantId = tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getObject() != null && key.getObject().startsWith("tenant:"))
          .filter(key -> "admin".equals(key.getRelation()) || "member".equals(key.getRelation()))
          .map(key -> key.getObject().substring("tenant:".length()))
          .findFirst();
      
      if (tenantId.isPresent()) {
        log.info("Found tenant '{}' for user '{}'", tenantId.get(), userEmail);
        return tenantId.get();
      }
      
      log.debug("No tenant relation found for user: {}", userEmail);
      return null;
    });
  }

  /**
   * Get all tenants a user belongs to.
   *
   * @param userEmail The user's email address
   * @return Single emitting list of tenant IDs
   */
  public Single<List<String>> getTenantsForUser(String userEmail) {
    return Single.fromCallable(() -> {
      String userIdentifier = "user:" + userEmail;
      
      ClientReadRequest request = new ClientReadRequest()
          .user(userIdentifier);
      
      var response = client.read(request).get();
      var tuples = response.getTuples();
      
      if (tuples == null || tuples.isEmpty()) {
        return List.<String>of();
      }
      
      return tuples.stream()
          .map(Tuple::getKey)
          .filter(key -> key.getObject() != null && key.getObject().startsWith("tenant:"))
          .filter(key -> "admin".equals(key.getRelation()) || "member".equals(key.getRelation()))
          .map(key -> key.getObject().substring("tenant:".length()))
          .distinct()
          .toList();
    });
  }

  /**
   * Check if user belongs to a specific tenant.
   *
   * @param userEmail The user's email address
   * @param tenantId  The tenant ID to check
   * @return Single emitting true if user belongs to tenant
   */
  public Single<Boolean> userBelongsToTenant(String userEmail, String tenantId) {
    // Check if user is admin or member of the tenant
    return check(userEmail, "admin", "tenant", tenantId)
        .flatMap(isAdmin -> {
          if (Boolean.TRUE.equals(isAdmin)) {
            return Single.just(true);
          }
          return check(userEmail, "member", "tenant", tenantId);
        });
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // RELATIONSHIP WRITES
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Add a relationship tuple.
   *
   * @param user       The user (format: "user:userId" or "objectType:objectId")
   * @param relation   The relation (e.g., "admin", "member", "parent")
   * @param objectType The target object type
   * @param objectId   The target object ID
   * @return Completable that completes when the write succeeds
   */
  public Completable addRelation(String user, String relation, String objectType, String objectId) {
    return Completable.fromCallable(() -> {
      ClientWriteRequest request = new ClientWriteRequest()
          .writes(List.of(
              new ClientTupleKey()
                  .user(user.contains(":") ? user : "user:" + user)
                  .relation(relation)
                  ._object(objectType + ":" + objectId)
          ));

      client.write(request).get();
      log.info("Added relation: {} {} {}:{}", user, relation, objectType, objectId);
      return null;
    });
  }

  /**
   * Remove a relationship tuple.
   */
  public Completable removeRelation(String user, String relation, String objectType, String objectId) {
    return Completable.fromCallable(() -> {
      ClientWriteRequest request = new ClientWriteRequest()
          .deletes(List.of(
              new ClientTupleKeyWithoutCondition()
                  .user(user.contains(":") ? user : "user:" + user)
                  .relation(relation)
                  ._object(objectType + ":" + objectId)
          ));

      client.write(request).get();
      log.info("Removed relation: {} {} {}:{}", user, relation, objectType, objectId);
      return null;
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // TENANT ROLE MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Add a user as tenant admin.
   */
  public Completable addTenantAdmin(String userId, String tenantId) {
    return addRelation(userId, "admin", "tenant", tenantId);
  }

  /**
   * Add a user as tenant member.
   */
  public Completable addTenantMember(String userId, String tenantId) {
    return addRelation(userId, "member", "tenant", tenantId);
  }

  /**
   * Remove a user from tenant admin role.
   */
  public Completable removeTenantAdmin(String userId, String tenantId) {
    return removeRelation(userId, "admin", "tenant", tenantId);
  }

  /**
   * Remove a user from tenant member role.
   */
  public Completable removeTenantMember(String userId, String tenantId) {
    return removeRelation(userId, "member", "tenant", tenantId);
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // PROJECT MANAGEMENT
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Set the parent tenant for a project.
   * This establishes the hierarchy: tenant -> project
   */
  public Completable setProjectParent(String projectId, String tenantId) {
    return addRelation("tenant:" + tenantId, "parent", "project", projectId);
  }

  /**
   * Add a user as project admin.
   */
  public Completable addProjectAdmin(String userId, String projectId) {
    return addRelation(userId, "admin", "project", projectId);
  }

  /**
   * Add a user as project editor.
   */
  public Completable addProjectEditor(String userId, String projectId) {
    return addRelation(userId, "editor", "project", projectId);
  }

  /**
   * Add a user as project member (viewer).
   */
  public Completable addProjectMember(String userId, String projectId) {
    return addRelation(userId, "member", "project", projectId);
  }

  /**
   * Remove a user from project admin role.
   */
  public Completable removeProjectAdmin(String userId, String projectId) {
    return removeRelation(userId, "admin", "project", projectId);
  }

  /**
   * Remove a user from project editor role.
   */
  public Completable removeProjectEditor(String userId, String projectId) {
    return removeRelation(userId, "editor", "project", projectId);
  }

  /**
   * Remove a user from project member role.
   */
  public Completable removeProjectMember(String userId, String projectId) {
    return removeRelation(userId, "member", "project", projectId);
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // RESOURCE HIERARCHY
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Set the parent project for an alert.
   * Permissions are inherited from the project.
   */
  public Completable setAlertParent(String alertId, String projectId) {
    return addRelation("project:" + projectId, "parent", "alert", alertId);
  }

  /**
   * Set the parent project for an SDK config.
   */
  public Completable setSdkConfigParent(String configId, String projectId) {
    return addRelation("project:" + projectId, "parent", "sdk_config", configId);
  }

  /**
   * Set the parent project for a dashboard.
   */
  public Completable setDashboardParent(String dashboardId, String projectId) {
    return addRelation("project:" + projectId, "parent", "dashboard", dashboardId);
  }

  // ═══════════════════════════════════════════════════════════════════════════════
  // BATCH OPERATIONS
  // ═══════════════════════════════════════════════════════════════════════════════

  /**
   * Write multiple relationship tuples in a single request.
   */
  public Completable writeTuples(List<ClientTupleKey> tuples) {
    return Completable.fromCallable(() -> {
      ClientWriteRequest request = new ClientWriteRequest().writes(tuples);
      client.write(request).get();
      log.info("Wrote {} tuples", tuples.size());
      return null;
    });
  }

  /**
   * Delete multiple relationship tuples in a single request.
   */
  public Completable deleteTuples(List<ClientTupleKeyWithoutCondition> tuples) {
    return Completable.fromCallable(() -> {
      ClientWriteRequest request = new ClientWriteRequest().deletes(tuples);
      client.write(request).get();
      log.info("Deleted {} tuples", tuples.size());
      return null;
    });
  }
}
