package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import reactor.core.publisher.Mono;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseProjectService {

  private final ClickhouseProjectConnectionPoolManager poolManager;
  private final ClickhouseProjectCredentialsDao credentialsDao;

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int PASSWORD_LENGTH = 32;

  private static final List<String> CLICKHOUSE_TABLES = List.of(
      "otel.otel_traces",
      "otel.otel_logs",
      "otel.otel_metrics_gauge",
      "otel.stack_trace_events"
  );

  // ==================== CREDENTIAL GENERATION (Pure, no I/O) ====================

  public String generateUsername(String projectId) {
    String sanitized = projectId.replace("-", "_").replace("proj_", "");
    return "project_" + sanitized;
  }

  public String generatePassword() {
    byte[] bytes = new byte[PASSWORD_LENGTH];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  // ==================== TRANSACTIONAL METHODS ====================

  /**
   * Saves ClickHouse credentials to MySQL within a transaction.
   * Used during project creation to include credentials insertion in the main transaction.
   *
   * @param conn      The SQL connection for the transaction
   * @param projectId The project ID
   * @return Single containing the credentials result with plain password for later CH user creation
   */
  public Single<CredentialsResult> saveCredentials(SqlConnection conn, String projectId) {
    String username = generateUsername(projectId);
    String plainPassword = generatePassword();

    log.debug("Saving ClickHouse credentials for project: {} within transaction", projectId);

    return credentialsDao.saveCredentials(conn, projectId, username, plainPassword)
        .map(creds -> new CredentialsResult(projectId, username, plainPassword))
        .doOnSuccess(result -> log.debug("Saved ClickHouse credentials for project: {} within transaction", projectId))
        .doOnError(error -> log.error("Failed to save ClickHouse credentials for project: {} within transaction", projectId, error));
  }

  // ==================== CLICKHOUSE OPERATIONS (No MySQL) ====================

  public Completable createClickhouseUserAndPolicies(String projectId, String username, String password) {
    log.info("Creating ClickHouse user and policies for project: {}, username: {}", projectId, username);

    return Completable.fromAction(() -> {
          ConnectionPool adminPool = poolManager.getAdminPool();

          // Step 1: Create ClickHouse user
          String createUserSQL = String.format(
              "CREATE USER IF NOT EXISTS %s IDENTIFIED WITH plaintext_password BY '%s'",
              username, password
          );
          executeSQL(adminPool, createUserSQL);
          log.info("Created ClickHouse user: {}", username);

          // Step 2: Create row policies for all tables
          for (String table : CLICKHOUSE_TABLES) {
            String policyName = generatePolicyName(projectId, table);
            String createPolicySQL = String.format(
                "CREATE ROW POLICY IF NOT EXISTS %s ON %s " +
                    "FOR SELECT USING ProjectId = '%s' TO %s",
                policyName, table, projectId, username
            );
            executeSQL(adminPool, createPolicySQL);
            log.debug("Created row policy: {} for table: {}", policyName, table);
          }

          // Step 3: Grant SELECT permissions
          String grantSQL = String.format("GRANT SELECT ON otel.* TO %s", username);
          executeSQL(adminPool, grantSQL);
          log.info("Granted SELECT permissions to: {}", username);
        })
        .doOnComplete(() ->
            log.info("Successfully created ClickHouse user and policies for project: {}", projectId)
        )
        .doOnError(error ->
            log.error("Failed to create ClickHouse user: projectId={}, error={}",
                projectId, error.getMessage(), error)
        );
  }

  // ==================== COMBINED OPERATIONS (Backward Compatible) ====================

  // ==================== PRIVATE HELPERS ====================

  private String generatePolicyName(String projectId, String tableName) {
    String sanitized = projectId.replace("-", "_").replace("proj_", "");
    String tableShort = tableName.replace("otel.", "").replace("_", "");
    return String.format("proj_%s_policy_%s", sanitized, tableShort);
  }

  private void executeSQL(ConnectionPool adminPool, String sql) {
    Connection connection = null;
    try {
      connection = Mono.from(adminPool.create()).block();
      if (connection != null) {
        Mono.from(connection.createStatement(sql)
                .execute())
            .block();
      }
    } catch (Exception e) {
      log.error("Failed to execute SQL: {}", sql, e);
      throw new RuntimeException("SQL execution failed: " + e.getMessage(), e);
    } finally {
      if (connection != null) {
        try {
          Mono.from(connection.close()).block();
        } catch (Exception e) {
          log.warn("Failed to close connection", e);
        }
      }
    }
  }

  // ==================== NESTED CLASSES ====================

  /**
   * Result of saving ClickHouse credentials.
   * Contains the plain password needed for async CH user creation.
   */
  @Getter
  @RequiredArgsConstructor
  public static class CredentialsResult {
    private final String projectId;
    private final String username;
    private final String plainPassword;
  }
}
