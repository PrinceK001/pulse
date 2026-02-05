package org.dreamhorizon.pulseserver.service.tenant;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseTenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.ClickhouseCredentialsDao;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.dao.tenantdao.TenantDao;
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;
import org.dreamhorizon.pulseserver.service.firebase.FirebaseTenantService;
import org.dreamhorizon.pulseserver.service.firebase.models.FirebaseTenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.CreateTenantRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.TenantInfo;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateCredentialsRequest;
import org.dreamhorizon.pulseserver.service.tenant.models.UpdateTenantRequest;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class TenantService {

  private final TenantDao tenantDao;
  private final ClickhouseCredentialsDao credentialsDao;
  private final ClickhouseTenantConnectionPoolManager poolManager;
  private final FirebaseTenantService firebaseTenantService;

  /**
   * Creates a new tenant in the local database.
   * 
   * <p>The Firebase tenant must be created in Firebase Console first.
   * Provide the GCP tenant ID from Firebase Console in the request.</p>
   * 
   * @param request the tenant creation request (must include gcpTenantId from Firebase Console)
   * @return Single emitting the created tenant
   */
  public Single<Tenant> createTenant(CreateTenantRequest request) {
    log.info("Creating tenant: {}", request.getTenantId());

    if (request.getGcpTenantId() == null || request.getGcpTenantId().isBlank()) {
      return Single.error(new IllegalArgumentException(
          "gcpTenantId is required - create the tenant in Firebase Console first"));
    }

    Tenant tenant = Tenant.builder()
        .tenantId(request.getTenantId())
        .name(request.getName())
        .description(request.getDescription())
        .gcpTenantId(request.getGcpTenantId())
        .domainName(request.getDomainName())
        .isActive(true)
        .build();

    return tenantDao.createTenant(tenant)
        .doOnSuccess(t -> log.info("Tenant created: {} with gcpTenantId: {}", 
            t.getTenantId(), t.getGcpTenantId()))
        .doOnError(error -> log.error("Failed to create tenant: {}", request.getTenantId(), error));
  }

  public Maybe<Tenant> getTenant(String tenantId) {
    return tenantDao.getTenantById(tenantId)
        .doOnError(error -> log.error("Failed to get tenant: {}", tenantId, error));
  }

  public Flowable<Tenant> getAllActiveTenants() {
    return tenantDao.getAllActiveTenants()
        .doOnError(error -> log.error("Failed to get all active tenants", error));
  }

  public Flowable<Tenant> getAllTenants() {
    return tenantDao.getAllTenants()
        .doOnError(error -> log.error("Failed to get all tenants", error));
  }

  public Single<Tenant> updateTenant(UpdateTenantRequest request) {
    log.info("Updating tenant: {}", request.getTenantId());

    return tenantDao.updateTenant(request.getTenantId(), request.getName(), request.getDescription())
        .doOnSuccess(t -> log.info("Tenant updated: {}", t.getTenantId()))
        .doOnError(error -> log.error("Failed to update tenant: {}", request.getTenantId(), error));
  }

  public Completable deactivateTenant(String tenantId) {
    log.info("Deactivating tenant: {}", tenantId);

    return tenantDao.deactivateTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant deactivated: {}", tenantId))
        .doOnError(error -> log.error("Failed to deactivate tenant: {}", tenantId, error));
  }

  public Completable activateTenant(String tenantId) {
    log.info("Activating tenant: {}", tenantId);

    return tenantDao.activateTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant activated: {}", tenantId))
        .doOnError(error -> log.error("Failed to activate tenant: {}", tenantId, error));
  }

  /**
   * Deletes a tenant from the local database.
   * 
   * <p>Note: This does NOT delete the tenant from Firebase. Use {@link #deleteTenantWithFirebase}
   * to delete from both Firebase and local database.</p>
   * 
   * @param tenantId the tenant ID to delete
   * @return Completable that completes when deletion is successful
   */
  public Completable deleteTenant(String tenantId) {
    log.info("Deleting tenant: {}", tenantId);

    return tenantDao.deleteTenant(tenantId)
        .doOnComplete(() -> log.info("Tenant deleted: {}", tenantId))
        .doOnError(error -> log.error("Failed to delete tenant: {}", tenantId, error));
  }

  /**
   * Deletes a tenant from both Firebase Identity Platform and local database.
   * 
   * @param tenantId the local tenant ID
   * @param deleteFromFirebase if true, also delete from Firebase
   * @return Completable that completes when deletion is successful
   */
  public Completable deleteTenantWithFirebase(String tenantId, boolean deleteFromFirebase) {
    log.info("Deleting tenant {} (deleteFromFirebase={})", tenantId, deleteFromFirebase);

    if (!deleteFromFirebase) {
      return deleteTenant(tenantId);
    }

    // Get tenant to find gcpTenantId, then delete from Firebase and local DB
    return tenantDao.getTenantById(tenantId)
        .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
        .flatMapCompletable(tenant -> {
          String gcpTenantId = tenant.getGcpTenantId();
          
          if (gcpTenantId == null || gcpTenantId.isBlank()) {
            log.warn("Tenant {} has no gcpTenantId, skipping Firebase deletion", tenantId);
            return deleteTenant(tenantId);
          }
          
          return firebaseTenantService.deleteTenant(gcpTenantId)
              .doOnComplete(() -> log.info("Deleted Firebase tenant: {}", gcpTenantId))
              .onErrorResumeNext(error -> {
                log.warn("Failed to delete Firebase tenant {}: {}", gcpTenantId, error.getMessage());
                // Continue with local deletion even if Firebase fails
                return Completable.complete();
              })
              .andThen(deleteTenant(tenantId));
        })
        .doOnError(error -> log.error("Failed to delete tenant with Firebase: {}", tenantId, error));
  }

  public Single<Boolean> tenantExists(String tenantId) {
    return tenantDao.tenantExists(tenantId);
  }

  public Single<TenantInfo> createClickhouseCredentials(CreateCredentialsRequest request, String performedBy) {
    String tenantId = request.getTenantId();
    log.info("Creating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    String plainPassword = request.getClickhousePassword();

    return credentialsDao.saveTenantCredentials(tenantId, plainPassword)
        .flatMap(credentials -> {
          // Initialize connection pool
          try {
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), plainPassword);
            log.info("Connection pool created for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to create connection pool for tenant: {}, will be created on first query", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("clickhouseUsername", credentials.getClickhouseUsername())
              .put("action", "ClickHouse credentials created");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_CREATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(true)
                  .message("ClickHouse credentials created. Please create ClickHouse user and row policies.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials created for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to create ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Single<ClickhouseCredentials> getClickhouseCredentials(String tenantId) {
    return credentialsDao.getCredentialsByTenantId(tenantId)
        .doOnError(error -> log.error("Failed to get ClickHouse credentials: {}", tenantId, error));
  }

  public Flowable<ClickhouseCredentials> getAllActiveClickhouseCredentials() {
    return credentialsDao.getAllActiveTenantCredentials()
        .doOnError(error -> log.error("Failed to get all active ClickHouse credentials", error));
  }

  public Single<TenantInfo> updateClickhouseCredentials(UpdateCredentialsRequest request, String performedBy) {
    String tenantId = request.getTenantId();
    log.info("Updating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    String newPassword = request.getNewPassword();

    return credentialsDao.updateTenantCredentials(tenantId, newPassword)
        .flatMap(credentials -> {
          // Recreate connection pool with new credentials
          try {
            poolManager.closePoolForTenant(tenantId);
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), newPassword);
            log.info("Connection pool recreated for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to recreate connection pool for tenant: {}", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("action", "ClickHouse credentials updated")
              .put("reason", request.getReason() != null ? request.getReason() : "Manual update");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_UPDATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(credentials.getIsActive())
                  .message("ClickHouse credentials updated. Update ClickHouse user password to match.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials updated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to update ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Completable deactivateClickhouseCredentials(String tenantId, String performedBy) {
    log.info("Deactivating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    JsonObject auditDetails = new JsonObject()
        .put("action", "ClickHouse credentials deactivated");

    return credentialsDao.deactivateTenantCredentials(tenantId)
        .andThen(Completable.fromAction(() -> {
          try {
            poolManager.closePoolForTenant(tenantId);
            log.info("Connection pool closed for tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to close pool during deactivation: {}", tenantId, e);
          }
        }))
        .andThen(credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_DEACTIVATED, performedBy, auditDetails))
        .doOnComplete(() -> log.info("ClickHouse credentials deactivated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to deactivate ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Single<TenantInfo> reactivateClickhouseCredentials(String tenantId, String performedBy) {
    log.info("Reactivating ClickHouse credentials for tenant: {} by user: {}", tenantId, performedBy);

    return credentialsDao.reactivateTenantCredentials(tenantId)
        .andThen(credentialsDao.getCredentialsByTenantId(tenantId))
        .flatMap(credentials -> {
          // Re-initialize connection pool
          try {
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), credentials.getClickhousePassword());
            log.info("Connection pool created for reactivated tenant: {}", tenantId);
          } catch (Exception e) {
            log.warn("Failed to create pool during reactivation: {}", tenantId, e);
          }

          // Audit log
          JsonObject auditDetails = new JsonObject()
              .put("action", "ClickHouse credentials reactivated");

          return credentialsDao.insertAuditLog(tenantId, TenantAuditAction.CREDENTIALS_REACTIVATED, performedBy, auditDetails)
              .andThen(Single.just(TenantInfo.builder()
                  .tenantId(tenantId)
                  .clickhouseUsername(credentials.getClickhouseUsername())
                  .isActive(true)
                  .message("ClickHouse credentials reactivated.")
                  .build()));
        })
        .doOnSuccess(info -> log.info("ClickHouse credentials reactivated for tenant: {}", tenantId))
        .doOnError(error -> log.error("Failed to reactivate ClickHouse credentials for tenant: {}", tenantId, error));
  }

  public Flowable<ClickhouseTenantCredentialAudit> getCredentialsAuditHistory(String tenantId) {
    return credentialsDao.getAuditLogsByTenantId(tenantId)
        .doOnError(error -> log.error("Failed to get audit history for tenant: {}", tenantId, error));
  }

  public Flowable<ClickhouseTenantCredentialAudit> getRecentCredentialsAuditLogs(int limit) {
    return credentialsDao.getRecentAuditLogs(limit)
        .doOnError(error -> log.error("Failed to get recent audit logs", error));
  }

  public Completable refreshConnectionPool(String tenantId) {
    log.info("Refreshing connection pool for tenant: {}", tenantId);

    return credentialsDao.getCredentialsByTenantId(tenantId)
        .flatMapCompletable(credentials -> {
          try {
            poolManager.closePoolForTenant(tenantId);
            poolManager.getPoolForTenant(tenantId, credentials.getClickhouseUsername(), credentials.getClickhousePassword());
            log.info("Connection pool refreshed for tenant: {}", tenantId);
            return Completable.complete();
          } catch (Exception e) {
            log.error("Failed to refresh pool for tenant: {}", tenantId, e);
            return Completable.error(e);
          }
        })
        .doOnError(error -> log.error("Failed to refresh pool for tenant: {}", tenantId, error));
  }

  public ClickhouseTenantConnectionPoolManager.PoolStatistics getPoolStatistics(String tenantId) {
    return poolManager.getPoolStatistics(tenantId);
  }

  // ==================== Firebase Tenant Management Methods ====================

  /**
   * Gets a tenant from Firebase by its GCP tenant ID.
   * 
   * @param gcpTenantId the Firebase tenant ID
   * @return Maybe emitting the Firebase tenant info, or empty if not found
   */
  public Maybe<FirebaseTenantInfo> getFirebaseTenant(String gcpTenantId) {
    return firebaseTenantService.getTenant(gcpTenantId)
        .doOnError(error -> log.error("Failed to get Firebase tenant: {}", gcpTenantId, error));
  }

  /**
   * Lists all tenants from Firebase Identity Platform.
   * 
   * @return Single emitting a list of all Firebase tenants
   */
  public Single<List<FirebaseTenantInfo>> listAllFirebaseTenants() {
    return firebaseTenantService.listAllTenants()
        .doOnError(error -> log.error("Failed to list Firebase tenants", error));
  }

  /**
   * Updates a tenant's display name in Firebase Identity Platform.
   * 
   * @param gcpTenantId the Firebase tenant ID
   * @param displayName the new display name
   * @return Single emitting the updated Firebase tenant info
   */
  public Single<FirebaseTenantInfo> updateFirebaseTenant(String gcpTenantId, String displayName) {
    return firebaseTenantService.updateTenant(gcpTenantId, displayName)
        .doOnSuccess(tenant -> log.info("Updated Firebase tenant: {}", gcpTenantId))
        .doOnError(error -> log.error("Failed to update Firebase tenant: {}", gcpTenantId, error));
  }

  /**
   * Syncs a local tenant with Firebase by updating the Firebase tenant's display name.
   * 
   * @param tenantId the local tenant ID
   * @return Single emitting the updated Firebase tenant info
   */
  public Single<FirebaseTenantInfo> syncTenantToFirebase(String tenantId) {
    return tenantDao.getTenantById(tenantId)
        .switchIfEmpty(Single.error(new RuntimeException("Tenant not found: " + tenantId)))
        .flatMap(tenant -> {
          String gcpTenantId = tenant.getGcpTenantId();
          if (gcpTenantId == null || gcpTenantId.isBlank()) {
            return Single.error(new RuntimeException("Tenant has no gcpTenantId: " + tenantId));
          }
          return firebaseTenantService.updateTenant(gcpTenantId, tenant.getName());
        })
        .doOnSuccess(info -> log.info("Synced tenant {} to Firebase", tenantId))
        .doOnError(error -> log.error("Failed to sync tenant to Firebase: {}", tenantId, error));
  }

  /**
   * Checks if a Firebase tenant exists.
   * 
   * @param gcpTenantId the Firebase tenant ID
   * @return Single emitting true if exists, false otherwise
   */
  public Single<Boolean> firebaseTenantExists(String gcpTenantId) {
    return firebaseTenantService.tenantExists(gcpTenantId);
  }
}
