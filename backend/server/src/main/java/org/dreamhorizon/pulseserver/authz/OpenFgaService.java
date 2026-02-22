package org.dreamhorizon.pulseserver.authz;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.client.model.ClientCheckRequest;
import dev.openfga.sdk.api.client.model.ClientReadRequest;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.api.model.Tuple;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.OpenFgaConfig;

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

}
