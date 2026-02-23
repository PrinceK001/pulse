package org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials;

import static org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsQueries.*;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.model.ClickhouseProjectCredentials;
import org.dreamhorizon.pulseserver.util.PasswordEncryptionUtil;

/**
 * DAO for ClickHouse project credentials.
 * Manages encrypted credentials for per-project ClickHouse users.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ClickhouseProjectCredentialsDao {
    
    private final MysqlClient mysqlClient;
    private final PasswordEncryptionUtil encryptionUtil;
    
    /**
     * Save encrypted credentials for a project.
     * 
     * @param projectId Project ID
     * @param clickhouseUsername ClickHouse username
     * @param plainPassword Plain text password (will be encrypted)
     * @return Single with saved credentials
     */
    public Single<ClickhouseProjectCredentials> saveCredentials(
            String projectId, 
            String clickhouseUsername,
            String plainPassword) {
        
        MySQLPool pool = mysqlClient.getWriterPool();
        
        return Single.fromCallable(() -> {
            // Encrypt password
            String encryptedPassword = encryptionUtil.encrypt(plainPassword);
            String salt = encryptionUtil.generateSalt();
            String digest = encryptionUtil.generateDigest(plainPassword);
            
            return Tuple.of(
                projectId,
                clickhouseUsername,
                encryptedPassword,
                salt,
                digest,
                true
            );
        })
        .flatMap(tuple -> 
            pool.preparedQuery(INSERT_CREDENTIALS)
                .rxExecute(tuple)
                .map(result -> {
                    log.info("Saved ClickHouse credentials: projectId={}, username={}", 
                        projectId, clickhouseUsername);
                    
                    return ClickhouseProjectCredentials.builder()
                        .projectId(projectId)
                        .clickhouseUsername(clickhouseUsername)
                        .clickhousePasswordEncrypted((String) tuple.getValue(2))
                        .encryptionSalt((String) tuple.getValue(3))
                        .passwordDigest((String) tuple.getValue(4))
                        .isActive(true)
                        .build();
                })
        )
        .doOnError(error -> 
            log.error("Failed to save credentials: projectId={}", projectId, error)
        );
    }
    
    /**
     * Get credentials for a project (with decrypted password).
     * 
     * @param projectId Project ID
     * @return Maybe with credentials including decrypted password
     */
    public Maybe<ClickhouseProjectCredentials> getCredentialsByProjectId(String projectId) {
        MySQLPool pool = mysqlClient.getReaderPool();
        
        return pool.preparedQuery(GET_CREDENTIALS_BY_PROJECT_ID)
            .rxExecute(Tuple.of(projectId))
            .flatMapMaybe(rowSet -> {
                if (rowSet.size() == 0) {
                    log.debug("No credentials found for project: {}", projectId);
                    return Maybe.empty();
                }
                
                Row row = rowSet.iterator().next();
                String encryptedPassword = row.getString("clickhouse_password_encrypted");
                
                return Maybe.fromCallable(() -> {
                    String decryptedPassword = encryptionUtil.decrypt(encryptedPassword);
                    
                    return ClickhouseProjectCredentials.builder()
                        .id(row.getLong("id"))
                        .projectId(row.getString("project_id"))
                        .clickhouseUsername(row.getString("clickhouse_username"))
                        .clickhousePasswordEncrypted(decryptedPassword)  // Return decrypted
                        .encryptionSalt(row.getString("encryption_salt"))
                        .passwordDigest(row.getString("password_digest"))
                        .isActive(row.getBoolean("is_active"))
                        .createdAt(row.getLocalDateTime("created_at") != null ?
                            row.getLocalDateTime("created_at").toString() : null)
                        .updatedAt(row.getLocalDateTime("updated_at") != null ?
                            row.getLocalDateTime("updated_at").toString() : null)
                        .build();
                });
            })
            .doOnError(error -> 
                log.error("Failed to get credentials: projectId={}", projectId, error)
            );
    }
    
    /**
     * Deactivate credentials for a project.
     * 
     * @param projectId Project ID
     * @return Completable
     */
    public Completable deactivateCredentials(String projectId) {
        MySQLPool pool = mysqlClient.getWriterPool();
        
        return pool.preparedQuery(DEACTIVATE_CREDENTIALS)
            .rxExecute(Tuple.of(projectId))
            .flatMapCompletable(result -> {
                if (result.rowCount() == 0) {
                    log.warn("No credentials found to deactivate: projectId={}", projectId);
                }
                log.info("Deactivated credentials: projectId={}", projectId);
                return Completable.complete();
            })
            .doOnError(error -> 
                log.error("Failed to deactivate credentials: projectId={}", projectId, error)
            );
    }
}
