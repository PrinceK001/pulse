package org.dreamhorizon.pulseserver.service.apikey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyDao;
import org.dreamhorizon.pulseserver.dao.apikey.models.ProjectApiKey;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.ApiKeyPublicInfo;
import org.dreamhorizon.pulseserver.service.apikey.models.CreateApiKeyRequest;
import org.dreamhorizon.pulseserver.service.apikey.models.RevokeApiKeyRequest;
import org.dreamhorizon.pulseserver.util.SecureRandomUtil;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectApiKeyService {

  private static final int API_KEY_RANDOM_LENGTH = 24;
  private static final String DEACTIVATION_REASON_REVOKED = "revoked";

  private final ProjectApiKeyDao apiKeyDao;
  private final ProjectDao projectDao;
  private final ProjectApiKeyEncryptionUtil encryptionUtil;

  // ==================== PUBLIC API ====================

  /**
   * Creates a new API key for a project.
   * Returns the raw API key (only visible on creation).
   */
  public Single<ApiKeyInfo> createApiKey(CreateApiKeyRequest request) {
    log.info("Creating API key for project: {} by: {}", request.getProjectId(), request.getCreatedBy());

    return validateProjectExists(request.getProjectId())
        .flatMap(exists -> {
          if (!exists) {
            return Single.error(new RuntimeException("Project not found: " + request.getProjectId()));
          }

          // Generate API key: {projectId}_{24-char-alphanumeric}
          String rawApiKey = generateApiKey(request.getProjectId());

          // Encrypt and generate digest
          EncryptedData encrypted = encryptionUtil.encrypt(rawApiKey);
          String digest = encryptionUtil.generateDigest(rawApiKey);

          return apiKeyDao.createApiKey(
              request.getProjectId(),
              request.getDisplayName(),
              encrypted.getEncryptedValue(),
              encrypted.getSalt(),
              digest,
              request.getExpiresAt(),
              request.getCreatedBy()
          ).map(apiKey -> mapToInfoWithRawKey(apiKey, rawApiKey));
        })
        .doOnSuccess(info -> log.info("Created API key {} for project: {}", info.getApiKeyId(), request.getProjectId()))
        .doOnError(error -> log.error("Failed to create API key for project: {}", request.getProjectId(), error));
  }

  /**
   * Gets all active API keys for a project (public, no raw key).
   */
  public Single<List<ApiKeyPublicInfo>> getActiveApiKeys(String projectId) {
    return apiKeyDao.getActiveApiKeysByProjectId(projectId)
        .map(this::mapToPublicInfo)
        .toList()
        .doOnError(error -> log.error("Failed to get active API keys for project: {}", projectId, error));
  }

  /**
   * Revokes an API key with optional grace period.
   */
  public Completable revokeApiKey(RevokeApiKeyRequest request) {
    int gracePeriodDays = request.getGracePeriodDays() != null ? request.getGracePeriodDays() : 0;
    LocalDateTime gracePeriodEndsAt = LocalDateTime.now().plusDays(gracePeriodDays);

    log.info("Revoking API key {} for project: {} with grace period: {} days by: {}",
        request.getApiKeyId(), request.getProjectId(), gracePeriodDays, request.getRevokedBy());

    return apiKeyDao.deactivateApiKey(
        request.getApiKeyId(),
        request.getProjectId(),
        request.getRevokedBy(),
        DEACTIVATION_REASON_REVOKED,
        gracePeriodEndsAt
    )
        .doOnComplete(() -> log.info("Revoked API key {} for project: {}", request.getApiKeyId(), request.getProjectId()))
        .doOnError(error -> log.error("Failed to revoke API key {} for project: {}",
            request.getApiKeyId(), request.getProjectId(), error));
  }

  // ==================== INTERNAL API ====================

  /**
   * Gets all valid API keys with raw keys (for cron to sync to Redis).
   * Valid means: active OR (inactive but in grace period), AND not expired.
   */
  public Flowable<ApiKeyInfo> getAllValidApiKeys() {
    return apiKeyDao.getAllValidApiKeys()
        .map(this::mapToInfoWithDecryptedKey)
        .doOnError(error -> log.error("Failed to get all valid API keys", error));
  }

  // ==================== HELPER METHODS ====================

  /**
   * Generates API key in format: {projectId}_{24-char-alphanumeric}
   */
  private String generateApiKey(String projectId) {
    return projectId + "_" + SecureRandomUtil.generateAlphanumeric(API_KEY_RANDOM_LENGTH);
  }

  private Single<Boolean> validateProjectExists(String projectId) {
    return projectDao.projectExists(projectId);
  }

  private ApiKeyInfo mapToInfoWithRawKey(ProjectApiKey apiKey, String rawApiKey) {
    return ApiKeyInfo.builder()
        .apiKeyId(apiKey.getProjectApiKeyId())
        .projectId(apiKey.getProjectId())
        .displayName(apiKey.getDisplayName())
        .rawApiKey(rawApiKey)
        .isActive(apiKey.getIsActive())
        .expiresAt(apiKey.getExpiresAt())
        .gracePeriodEndsAt(apiKey.getGracePeriodEndsAt())
        .createdBy(apiKey.getCreatedBy())
        .createdAt(apiKey.getCreatedAt())
        .deactivatedAt(apiKey.getDeactivatedAt())
        .deactivatedBy(apiKey.getDeactivatedBy())
        .deactivationReason(apiKey.getDeactivationReason())
        .build();
  }

  private ApiKeyInfo mapToInfoWithDecryptedKey(ProjectApiKey apiKey) {
    String rawApiKey = encryptionUtil.decrypt(apiKey.getApiKeyEncrypted());
    return mapToInfoWithRawKey(apiKey, rawApiKey);
  }

  private ApiKeyPublicInfo mapToPublicInfo(ProjectApiKey apiKey) {
    return ApiKeyPublicInfo.builder()
        .apiKeyId(apiKey.getProjectApiKeyId())
        .projectId(apiKey.getProjectId())
        .displayName(apiKey.getDisplayName())
        .isActive(apiKey.getIsActive())
        .expiresAt(apiKey.getExpiresAt())
        .gracePeriodEndsAt(apiKey.getGracePeriodEndsAt())
        .createdBy(apiKey.getCreatedBy())
        .createdAt(apiKey.getCreatedAt())
        .deactivatedAt(apiKey.getDeactivatedAt())
        .deactivatedBy(apiKey.getDeactivatedBy())
        .deactivationReason(apiKey.getDeactivationReason())
        .build();
  }
}

