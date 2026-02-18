package org.dreamhorizon.pulseserver.authz;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientTupleKey;
import dev.openfga.sdk.api.client.model.ClientTupleKeyWithoutCondition;
import dev.openfga.sdk.api.client.model.ClientWriteRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;

import java.util.List;

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
