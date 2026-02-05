package org.dreamhorizon.pulseserver.service.firebase;

import com.google.firebase.auth.ExportedUserRecord;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.ListUsersPage;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.auth.multitenancy.TenantAwareFirebaseAuth;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing users within Firebase tenants.
 *
 * <p>Replicates the behavior of the Identity Toolkit API:
 * POST /v1/projects/{projectId}/tenants/{tenantId}/accounts</p>
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FirebaseUserService {

  private final FirebaseAdminService firebaseAdminService;

  /**
   * Creates a user in a specific tenant.
   *
   * <p>Equivalent to:
   * <pre>
   * POST https://identitytoolkit.googleapis.com/v1/projects/{projectId}/tenants/{tenantId}/accounts
   * {
   *   "email": "user@example.com",
   *   "emailVerified": true
   * }
   * </pre>
   * </p>
   *
   * @param gcpTenantId   the Firebase tenant ID
   * @param email         the user's email address
   * @param emailVerified whether the email is verified
   * @return Single emitting the created user info
   */
  public Single<TenantUserInfo> createUser(String gcpTenantId, String email, boolean emailVerified) {
    return Single.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      UserRecord.CreateRequest request = new UserRecord.CreateRequest()
          .setEmail(email)
          .setEmailVerified(emailVerified);

      UserRecord userRecord = tenantAuth.createUser(request);
      log.info("Created user {} in tenant {} (emailVerified={})", email, gcpTenantId, emailVerified);

      return mapToUserInfo(userRecord, gcpTenantId);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Creates a user in a specific tenant with display name.
   */
  public Single<TenantUserInfo> createUser(
      String gcpTenantId,
      String email,
      String displayName,
      boolean emailVerified) {

    return Single.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      UserRecord.CreateRequest request = new UserRecord.CreateRequest()
          .setEmail(email)
          .setEmailVerified(emailVerified);

      if (displayName != null && !displayName.isBlank()) {
        request.setDisplayName(displayName);
      }

      UserRecord userRecord = tenantAuth.createUser(request);
      log.info("Created user {} in tenant {}", email, gcpTenantId);

      return mapToUserInfo(userRecord, gcpTenantId);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Gets a user by email from a specific tenant.
   */
  public Maybe<TenantUserInfo> getUserByEmail(String gcpTenantId, String email) {
    return Maybe.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      try {
        UserRecord userRecord = tenantAuth.getUserByEmail(email);
        log.debug("Found user {} in tenant {}", email, gcpTenantId);
        return mapToUserInfo(userRecord, gcpTenantId);
      } catch (FirebaseAuthException e) {
        if ("USER_NOT_FOUND".equals(e.getAuthErrorCode().name())) {
          log.debug("User {} not found in tenant {}", email, gcpTenantId);
          return null;
        }
        throw e;
      }
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Gets a user by UID from a specific tenant.
   */
  public Maybe<TenantUserInfo> getUserByUid(String gcpTenantId, String uid) {
    return Maybe.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      try {
        UserRecord userRecord = tenantAuth.getUser(uid);
        return mapToUserInfo(userRecord, gcpTenantId);
      } catch (FirebaseAuthException e) {
        if ("USER_NOT_FOUND".equals(e.getAuthErrorCode().name())) {
          return null;
        }
        throw e;
      }
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Updates a user's email verification status.
   */
  public Single<TenantUserInfo> updateUser(
      String gcpTenantId,
      String uid,
      Boolean emailVerified,
      Boolean disabled) {

    return Single.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      UserRecord.UpdateRequest request = new UserRecord.UpdateRequest(uid);

      if (emailVerified != null) {
        request.setEmailVerified(emailVerified);
      }
      if (disabled != null) {
        request.setDisabled(disabled);
      }

      UserRecord userRecord = tenantAuth.updateUser(request);
      log.info("Updated user {} in tenant {}", uid, gcpTenantId);

      return mapToUserInfo(userRecord, gcpTenantId);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Deletes a user from a tenant.
   */
  public Completable deleteUser(String gcpTenantId, String uid) {
    return Completable.fromAction(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);
      tenantAuth.deleteUser(uid);
      log.info("Deleted user {} from tenant {}", uid, gcpTenantId);
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Lists all users in a specific tenant with pagination support.
   *
   * @param gcpTenantId the Firebase tenant ID
   * @param maxResults  maximum number of users to return (default 1000, max 1000)
   * @param pageToken   token for pagination (null for first page)
   * @return Single emitting the paginated user list
   */
  public Single<TenantUserListResult> listUsers(String gcpTenantId, Integer maxResults, String pageToken) {
    return Single.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      int limit = (maxResults != null && maxResults > 0 && maxResults <= 1000) ? maxResults : 1000;

      ListUsersPage page;
      if (pageToken != null && !pageToken.isBlank()) {
        page = tenantAuth.listUsers(pageToken, limit);
      } else {
        page = tenantAuth.listUsers(null, limit);
      }

      List<TenantUserInfo> users = new ArrayList<>();
      for (ExportedUserRecord record : page.getValues()) {
        users.add(mapToUserInfo(record, gcpTenantId));
      }

      String nextPageToken = page.getNextPageToken();
      log.info("Listed {} users in tenant {}", users.size(), gcpTenantId);

      return TenantUserListResult.builder()
          .users(users)
          .nextPageToken(nextPageToken)
          .build();
    }).subscribeOn(Schedulers.io());
  }

  /**
   * Lists all users in a tenant (fetches all pages).
   *
   * @param gcpTenantId the Firebase tenant ID
   * @return Single emitting the complete list of users
   */
  public Single<List<TenantUserInfo>> listAllUsers(String gcpTenantId) {
    return Single.fromCallable(() -> {
      TenantAwareFirebaseAuth tenantAuth = getTenantAuth(gcpTenantId);

      List<TenantUserInfo> allUsers = new ArrayList<>();
      ListUsersPage page = tenantAuth.listUsers(null);

      while (page != null) {
        for (ExportedUserRecord record : page.getValues()) {
          allUsers.add(mapToUserInfo(record, gcpTenantId));
        }
        page = page.getNextPage();
      }

      log.info("Listed all {} users in tenant {}", allUsers.size(), gcpTenantId);
      return allUsers;
    }).subscribeOn(Schedulers.io());
  }

  private TenantAwareFirebaseAuth getTenantAuth(String gcpTenantId) {
    FirebaseAuth auth = firebaseAdminService.getFirebaseAuth();
    return auth.getTenantManager().getAuthForTenant(gcpTenantId);
  }

  private TenantUserInfo mapToUserInfo(UserRecord record, String gcpTenantId) {
    return TenantUserInfo.builder()
        .uid(record.getUid())
        .email(record.getEmail())
        .displayName(record.getDisplayName())
        .emailVerified(record.isEmailVerified())
        .disabled(record.isDisabled())
        .gcpTenantId(gcpTenantId)
        .creationTimestamp(record.getUserMetadata().getCreationTimestamp())
        .lastSignInTimestamp(record.getUserMetadata().getLastSignInTimestamp())
        .build();
  }

  /**
   * User information from a Firebase tenant.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TenantUserInfo {
    private String uid;
    private String email;
    private String displayName;
    private boolean emailVerified;
    private boolean disabled;
    private String gcpTenantId;
    private long creationTimestamp;
    private long lastSignInTimestamp;
  }

  /**
   * Paginated result for listing users.
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class TenantUserListResult {
    private List<TenantUserInfo> users;
    private String nextPageToken;
  }
}

