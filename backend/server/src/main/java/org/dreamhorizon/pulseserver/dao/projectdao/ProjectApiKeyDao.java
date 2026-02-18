package org.dreamhorizon.pulseserver.dao.projectdao;

import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.CHECK_ACTIVE_API_KEY_EXISTS;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.DEACTIVATE_ACTIVE_API_KEY_FOR_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.DEACTIVATE_API_KEY;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_ACTIVE_API_KEY_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_ALL_ACTIVE_API_KEYS;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_ALL_API_KEYS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_API_KEYS_IN_GRACE_PERIOD;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_API_KEY_BY_DIGEST;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_API_KEY_BY_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.GET_VALID_API_KEYS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectApiKeyQueries.INSERT_API_KEY;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.projectdao.models.ProjectApiKey;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectApiKeyDao {
  private final MysqlClient mysqlClient;

  public Single<ProjectApiKey> createApiKey(
      int projectId,
      String apiKeyEncrypted,
      String encryptionSalt,
      String apiKeyDigest,
      String createdBy) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_API_KEY)
        .rxExecute(Tuple.of(projectId, apiKeyEncrypted, encryptionSalt, apiKeyDigest, createdBy))
        .map(result -> {
          long generatedId = result.property(io.vertx.sqlclient.PropertyKind.create("last-inserted-id", Long.class));
          log.info("Created API key {} for project: {}", generatedId, projectId);
          return ProjectApiKey.builder()
              .projectApiKeyId(generatedId)
              .projectId(projectId)
              .apiKeyEncrypted(apiKeyEncrypted)
              .encryptionSalt(encryptionSalt)
              .apiKeyDigest(apiKeyDigest)
              .isActive(true)
              .createdBy(createdBy)
              .build();
        })
        .doOnError(error -> log.error("Failed to create API key for project: {}", projectId, error));
  }

  public Maybe<ProjectApiKey> getApiKeyById(long apiKeyId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_API_KEY_BY_ID)
        .rxExecute(Tuple.of(apiKeyId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToApiKey(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch API key by id: {}", apiKeyId, error));
  }

  public Maybe<ProjectApiKey> getActiveApiKeyByProjectId(int projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ACTIVE_API_KEY_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToApiKey(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch active API key for project: {}", projectId, error));
  }

  public Maybe<ProjectApiKey> getApiKeyByDigest(String apiKeyDigest) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_API_KEY_BY_DIGEST)
        .rxExecute(Tuple.of(apiKeyDigest))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToApiKey(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch API key by digest", error));
  }

  public Flowable<ProjectApiKey> getAllApiKeysByProjectId(int projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ALL_API_KEYS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all API keys for project: {}", projectId, error));
  }

  public Flowable<ProjectApiKey> getValidApiKeysByProjectId(int projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_VALID_API_KEYS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch valid API keys for project: {}", projectId, error));
  }

  public Completable deactivateApiKey(
      long apiKeyId,
      String deactivatedBy,
      String deactivationReason,
      LocalDateTime gracePeriodEndsAt) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_API_KEY)
        .rxExecute(Tuple.of(deactivatedBy, deactivationReason, gracePeriodEndsAt, apiKeyId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No API key found to deactivate: {}", apiKeyId);
          } else {
            log.info("Deactivated API key: {} reason: {}", apiKeyId, deactivationReason);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate API key: {}", apiKeyId, error));
  }

  public Completable deactivateActiveApiKeyForProject(
      int projectId,
      String deactivatedBy,
      String deactivationReason,
      LocalDateTime gracePeriodEndsAt) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_ACTIVE_API_KEY_FOR_PROJECT)
        .rxExecute(Tuple.of(deactivatedBy, deactivationReason, gracePeriodEndsAt, projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.debug("No active API key found to deactivate for project: {}", projectId);
          } else {
            log.info("Deactivated active API key for project: {} reason: {}", projectId, deactivationReason);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate active API key for project: {}", projectId, error));
  }

  public Single<Boolean> hasActiveApiKey(int projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_ACTIVE_API_KEY_EXISTS)
        .rxExecute(Tuple.of(projectId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check active API key existence for project: {}", projectId, error));
  }

  public Flowable<ProjectApiKey> getAllActiveApiKeys() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_ACTIVE_API_KEYS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all active API keys", error));
  }

  public Flowable<ProjectApiKey> getApiKeysInGracePeriod() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_API_KEYS_IN_GRACE_PERIOD)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToApiKey((Row) row)))
        .doOnError(error -> log.error("Failed to fetch API keys in grace period", error));
  }

  /**
   * Rotates the API key for a project. Deactivates the current key (with optional grace period)
   * and creates a new one. This operation is NOT transactional - use ProjectTransactionDao
   * for atomic operations.
   */
  public Single<ProjectApiKey> rotateApiKey(
      int projectId,
      String newApiKeyEncrypted,
      String newEncryptionSalt,
      String newApiKeyDigest,
      String performedBy,
      LocalDateTime gracePeriodEndsAt) {
    return deactivateActiveApiKeyForProject(projectId, performedBy, "key_rotation", gracePeriodEndsAt)
        .andThen(createApiKey(projectId, newApiKeyEncrypted, newEncryptionSalt, newApiKeyDigest, performedBy));
  }

  private ProjectApiKey mapRowToApiKey(Row row) {
    return ProjectApiKey.builder()
        .projectApiKeyId(row.getLong("project_api_key_id"))
        .projectId(row.getInteger("project_id"))
        .apiKeyEncrypted(row.getString("api_key_encrypted"))
        .encryptionSalt(row.getString("encryption_salt"))
        .apiKeyDigest(row.getString("api_key_digest"))
        .isActive(row.getBoolean("is_active"))
        .gracePeriodEndsAt(row.getLocalDateTime("grace_period_ends_at") != null 
            ? row.getLocalDateTime("grace_period_ends_at").toString() : null)
        .createdBy(row.getString("created_by"))
        .createdAt(row.getLocalDateTime("created_at") != null 
            ? row.getLocalDateTime("created_at").toString() : null)
        .deactivatedAt(row.getLocalDateTime("deactivated_at") != null 
            ? row.getLocalDateTime("deactivated_at").toString() : null)
        .deactivatedBy(row.getString("deactivated_by"))
        .deactivationReason(row.getString("deactivation_reason"))
        .build();
  }
}

