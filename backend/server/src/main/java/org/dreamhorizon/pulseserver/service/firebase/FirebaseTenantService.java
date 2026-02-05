package org.dreamhorizon.pulseserver.service.firebase;

import com.google.firebase.auth.multitenancy.ListTenantsPage;
import com.google.firebase.auth.multitenancy.Tenant;
import com.google.firebase.auth.multitenancy.Tenant.CreateRequest;
import com.google.firebase.auth.multitenancy.Tenant.UpdateRequest;
import com.google.firebase.auth.multitenancy.TenantManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.firebase.models.FirebaseTenantInfo;

/**
 * Service for managing tenants in Firebase Identity Platform using Firebase Admin SDK.
 * 
 * <p>This service provides CRUD operations for Firebase tenants, enabling multi-tenancy
 * for authentication. Each tenant can have its own users and auth providers.</p>
 * 
 * <p>Firebase tenants are identified by a GCP-generated tenant ID (e.g., "tenant-abc123"),
 * which is different from your internal tenant ID.</p>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FirebaseTenantService {

  private final FirebaseAdminService firebaseAdminService;

  /**
   * Creates a new tenant in Firebase Identity Platform with only Google sign-in support.
   * 
   * <p>The tenant is created with:
   * <ul>
   *   <li>Email/Password: DISABLED</li>
   *   <li>Email Link: DISABLED</li>
   *   <li>Anonymous: DISABLED</li>
   * </ul>
   * Google sign-in must be configured separately using {@link FirebaseIdentityProviderService}.
   * </p>
   * 
   * @param displayName the human-readable name for the tenant
   * @return Single emitting the created tenant info with the GCP tenant ID
   */
  public Single<FirebaseTenantInfo> createTenant(String displayName) {
    return Single.fromCallable(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      
      // Create tenant with all built-in auth methods DISABLED
      // Google sign-in is configured via Identity Platform API separately
      CreateRequest request = new CreateRequest()
          .setDisplayName(displayName)
          .setEmailLinkSignInEnabled(false)
          .setPasswordSignInAllowed(false);

      Tenant tenant = tenantManager.createTenant(request);
      log.info("Created Firebase tenant: {} with ID: {}", displayName, tenant.getTenantId());
      
      return mapToFirebaseTenantInfo(tenant);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Gets a tenant by its Firebase/GCP tenant ID.
   * 
   * @param gcpTenantId the Firebase tenant ID (e.g., "tenant-abc123")
   * @return Maybe emitting the tenant info, or empty if not found
   */
  public Maybe<FirebaseTenantInfo> getTenant(String gcpTenantId) {
    return Maybe.fromCallable(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      
      try {
        Tenant tenant = tenantManager.getTenant(gcpTenantId);
        log.debug("Found Firebase tenant: {}", gcpTenantId);
        return mapToFirebaseTenantInfo(tenant);
      } catch (com.google.firebase.auth.FirebaseAuthException e) {
        if ("TENANT_NOT_FOUND".equals(e.getAuthErrorCode().name())) {
          log.debug("Firebase tenant not found: {}", gcpTenantId);
          return null;
        }
        throw e;
      }
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Updates an existing tenant's display name in Firebase.
   * 
   * <p>Note: Only display name can be updated. Sign-in methods remain as:
   * <ul>
   *   <li>Email/Password: DISABLED</li>
   *   <li>Email Link: DISABLED</li>
   *   <li>Google: Managed via Identity Platform API</li>
   * </ul>
   * </p>
   * 
   * @param gcpTenantId the Firebase tenant ID to update
   * @param displayName the new display name
   * @return Single emitting the updated tenant info
   */
  public Single<FirebaseTenantInfo> updateTenant(String gcpTenantId, String displayName) {
    return Single.fromCallable(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      
      UpdateRequest request = new UpdateRequest(gcpTenantId);
      
      if (displayName != null && !displayName.isBlank()) {
        request.setDisplayName(displayName);
      }

      Tenant tenant = tenantManager.updateTenant(request);
      log.info("Updated Firebase tenant: {}", gcpTenantId);
      
      return mapToFirebaseTenantInfo(tenant);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Deletes a tenant from Firebase Identity Platform.
   * 
   * <p><strong>Warning:</strong> This is a destructive operation. All users associated
   * with the tenant will no longer be able to authenticate.</p>
   * 
   * @param gcpTenantId the Firebase tenant ID to delete
   * @return Completable that completes when deletion is successful
   */
  public Completable deleteTenant(String gcpTenantId) {
    return Completable.fromAction(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      tenantManager.deleteTenant(gcpTenantId);
      log.info("Deleted Firebase tenant: {}", gcpTenantId);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Lists all tenants in Firebase Identity Platform.
   * 
   * @return Single emitting a list of all tenant infos
   */
  public Single<List<FirebaseTenantInfo>> listAllTenants() {
    return Single.fromCallable(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      List<FirebaseTenantInfo> tenants = new ArrayList<>();
      
      ListTenantsPage page = tenantManager.listTenants(null);
      while (page != null) {
        for (Tenant tenant : page.getValues()) {
          tenants.add(mapToFirebaseTenantInfo(tenant));
        }
        page = page.getNextPage();
      }
      
      log.debug("Listed {} Firebase tenants", tenants.size());
      return tenants;
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Lists tenants with pagination.
   * 
   * @param maxResults maximum number of results per page (max 1000)
   * @param pageToken token for the next page, null for first page
   * @return Single emitting a page of tenant infos
   */
  public Single<TenantListResult> listTenants(int maxResults, String pageToken) {
    return Single.fromCallable(() -> {
      TenantManager tenantManager = firebaseAdminService.getTenantManager();
      List<FirebaseTenantInfo> tenants = new ArrayList<>();
      
      ListTenantsPage page = pageToken != null 
          ? tenantManager.listTenants(pageToken, maxResults)
          : tenantManager.listTenants(null, maxResults);
      
      for (Tenant tenant : page.getValues()) {
        tenants.add(mapToFirebaseTenantInfo(tenant));
      }
      
      String nextPageToken = page.hasNextPage() ? page.getNextPageToken() : null;
      
      return new TenantListResult(tenants, nextPageToken);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Checks if a tenant exists in Firebase.
   * 
   * @param gcpTenantId the Firebase tenant ID to check
   * @return Single emitting true if tenant exists, false otherwise
   */
  public Single<Boolean> tenantExists(String gcpTenantId) {
    return getTenant(gcpTenantId)
        .map(t -> true)
        .defaultIfEmpty(false);
  }

  private FirebaseTenantInfo mapToFirebaseTenantInfo(Tenant tenant) {
    return FirebaseTenantInfo.builder()
        .gcpTenantId(tenant.getTenantId())
        .displayName(tenant.getDisplayName())
        .emailLinkSignInEnabled(tenant.isEmailLinkSignInEnabled())
        .passwordSignInAllowed(tenant.isPasswordSignInAllowed())
        .build();
  }

  /**
   * Result of a paginated tenant list operation.
   */
  public record TenantListResult(
      List<FirebaseTenantInfo> tenants,
      String nextPageToken
  ) {}
}

