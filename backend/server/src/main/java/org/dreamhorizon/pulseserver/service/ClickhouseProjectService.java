package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.spi.Connection;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseProjectConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Service for managing per-project ClickHouse users and row policies.
 * This service automates the creation of dedicated ClickHouse users
 * with row-level security policies for each project.
 */
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
    
    /**
     * Complete setup for a project's ClickHouse access.
     * Creates user, row policies, and saves credentials.
     * 
     * @param projectId Project ID
     * @return Completable that completes when setup is done
     */
    public Completable setupProjectClickhouseUser(String projectId) {
        String username = generateUsername(projectId);
        String password = generateSecurePassword();
        
        log.info("Setting up ClickHouse user for project: {}, username: {}", projectId, username);
        
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
        .andThen(
            // Step 4: Save encrypted credentials to MySQL
            credentialsDao.saveCredentials(projectId, username, password)
                .ignoreElement()
        )
        .doOnComplete(() -> 
            log.info("Successfully set up ClickHouse access for project: {}", projectId)
        )
        .doOnError(error -> 
            log.error("Failed to setup ClickHouse user: projectId={}, error={}", 
                projectId, error.getMessage(), error)
        );
    }
    
    /**
     * Remove ClickHouse access for a project.
     * Drops user and row policies.
     * 
     * @param projectId Project ID
     * @return Completable
     */
    public Completable removeProjectClickhouseUser(String projectId) {
        String username = generateUsername(projectId);
        
        log.info("Removing ClickHouse user for project: {}, username: {}", projectId, username);
        
        return Completable.fromAction(() -> {
            ConnectionPool adminPool = poolManager.getAdminPool();
            
            // Drop row policies
            for (String table : CLICKHOUSE_TABLES) {
                String policyName = generatePolicyName(projectId, table);
                String dropPolicySQL = String.format(
                    "DROP ROW POLICY IF EXISTS %s ON %s",
                    policyName, table
                );
                executeSQL(adminPool, dropPolicySQL);
            }
            
            // Drop user
            String dropUserSQL = String.format("DROP USER IF EXISTS %s", username);
            executeSQL(adminPool, dropUserSQL);
            
            log.info("Removed ClickHouse user: {}", username);
        })
        .andThen(
            // Deactivate credentials in MySQL
            credentialsDao.deactivateCredentials(projectId)
        )
        .andThen(
            // Close connection pool
            Completable.fromAction(() -> poolManager.closePoolForProject(projectId))
        )
        .doOnComplete(() -> 
            log.info("Successfully removed ClickHouse access for project: {}", projectId)
        )
        .doOnError(error -> 
            log.error("Failed to remove ClickHouse user: projectId={}", projectId, error)
        );
    }
    
    /**
     * Generate ClickHouse username from project ID.
     * Format: project_{sanitized_project_id}
     */
    private String generateUsername(String projectId) {
        // Remove hyphens and make safe for ClickHouse
        String sanitized = projectId.replace("-", "_").replace("proj_", "");
        return "project_" + sanitized;
    }
    
    /**
     * Generate row policy name.
     * Format: proj_{id}_policy_{table}
     */
    private String generatePolicyName(String projectId, String tableName) {
        String sanitized = projectId.replace("-", "_").replace("proj_", "");
        String tableShort = tableName.replace("otel.", "").replace("_", "");
        return String.format("proj_%s_policy_%s", sanitized, tableShort);
    }
    
    /**
     * Generate cryptographically secure password.
     */
    private String generateSecurePassword() {
        byte[] bytes = new byte[PASSWORD_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Execute SQL statement using admin connection.
     * This is a synchronous blocking call - use within Completable.fromAction().
     */
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
}
