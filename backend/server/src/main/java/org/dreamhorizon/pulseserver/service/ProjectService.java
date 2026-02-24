package org.dreamhorizon.pulseserver.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.apikey.ProjectApiKeyDao;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.dao.configs.SdkConfigsDao;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao;
import org.dreamhorizon.pulseserver.dao.user.UserDao;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.model.Project;
import org.dreamhorizon.pulseserver.service.configs.DefaultSdkConfigTemplate;
import org.dreamhorizon.pulseserver.service.configs.UploadConfigDetailService;
import org.dreamhorizon.pulseserver.service.tier.TierService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.models.RedisUsageLimitCredits;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.util.SecureRandomUtil;
import org.dreamhorizon.pulseserver.util.encryption.EncryptedData;
import org.dreamhorizon.pulseserver.util.encryption.ProjectApiKeyEncryptionUtil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Singleton
public class ProjectService {

    private static final String DEFAULT_API_KEY_DISPLAY_NAME = "Default";
    private static final int API_KEY_RANDOM_LENGTH = 24;

    private final MysqlClient mysqlClient;
    private final ProjectDao projectDao;
    private final ClickhouseProjectCredentialsDao credentialsDao;
    private final ProjectApiKeyDao apiKeyDao;
    private final ProjectUsageLimitDao usageLimitDao;
    private final SdkConfigsDao sdkConfigsDao;
    private final UserDao userDao;
    private final OpenFgaService openFgaService;
    private final ClickhouseProjectService clickhouseProjectService;
    private final TierService tierService;
    private final UsageLimitService usageLimitService;
    private final UploadConfigDetailService uploadConfigDetailService;
    private final EmailService emailService;
    private final RedisService redisService;
    private final ProjectApiKeyEncryptionUtil apiKeyEncryptionUtil;
    private final ObjectMapper objectMapper;

    @Inject
    public ProjectService(
            MysqlClient mysqlClient,
            ProjectDao projectDao,
            ClickhouseProjectCredentialsDao credentialsDao,
            ProjectApiKeyDao apiKeyDao,
            ProjectUsageLimitDao usageLimitDao,
            SdkConfigsDao sdkConfigsDao,
            UserDao userDao,
            OpenFgaService openFgaService,
            ClickhouseProjectService clickhouseProjectService,
            TierService tierService,
            UsageLimitService usageLimitService,
            UploadConfigDetailService uploadConfigDetailService,
            EmailService emailService,
            RedisService redisService,
            ProjectApiKeyEncryptionUtil apiKeyEncryptionUtil,
            ObjectMapper objectMapper) {
        this.mysqlClient = mysqlClient;
        this.projectDao = projectDao;
        this.credentialsDao = credentialsDao;
        this.apiKeyDao = apiKeyDao;
        this.usageLimitDao = usageLimitDao;
        this.sdkConfigsDao = sdkConfigsDao;
        this.userDao = userDao;
        this.openFgaService = openFgaService;
        this.clickhouseProjectService = clickhouseProjectService;
        this.tierService = tierService;
        this.usageLimitService = usageLimitService;
        this.uploadConfigDetailService = uploadConfigDetailService;
        this.emailService = emailService;
        this.redisService = redisService;
        this.apiKeyEncryptionUtil = apiKeyEncryptionUtil;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new project within a tenant.
     * 
     * Flow:
     * 1. Pre-transaction: Generate all values (projectId, API key, CH credentials)
     * 2. Transaction: All DB inserts (project, credentials, API key, usage limits, SDK config)
     * 3. Blocking: OpenFGA role assignment and tenant linking
     * 4. Fire-and-forget: ClickHouse user, S3 upload, email notification
     */
    public Single<Project> createProject(String tenantId, String name, String description, String createdBy) {
        log.info("Creating project: tenantId={}, name={}, createdBy={}", tenantId, name, createdBy);

        // 1. Pre-transaction: Generate all values
        String projectId = generateProjectId();
        String rawApiKey = generateApiKey(projectId);
        String chUsername = clickhouseProjectService.generateUsername(projectId);
        String chPassword = clickhouseProjectService.generatePassword();
        var defaultConfigData = DefaultSdkConfigTemplate.createDefaultConfig(createdBy);

        Project project = Project.builder()
            .projectId(projectId)
            .tenantId(tenantId)
            .name(name)
            .description(description)
            .apiKey(null)
            .isActive(true)
            .createdBy(createdBy)
            .build();

        // Encrypt API key
        EncryptedData encryptedApiKey = apiKeyEncryptionUtil.encrypt(rawApiKey);
        String apiKeyDigest = apiKeyEncryptionUtil.generateDigest(rawApiKey);

        // 2. Get free tier defaults for usage limits
        return tierService.getFreeTierDefaults()
            .flatMap(usageLimits -> {
                // Compute finalThreshold for Redis storage
                usageLimitService.computeFinalThresholds(usageLimits);
                String usageLimitsJson = serializeUsageLimits(usageLimits);
                return Single.just(new UsageLimitsContext(usageLimits, usageLimitsJson));
            })
            .flatMap(usageLimitsCtx ->
                // 3. Execute transaction
                executeTransaction(
                    project, chUsername, chPassword, 
                    encryptedApiKey, apiKeyDigest, 
                    usageLimitsCtx.getJson(), defaultConfigData, createdBy
                )
                .map(createdProject -> new ProjectWithContext(createdProject, usageLimitsCtx.getLimits(), rawApiKey))
            )
            // 4. Blocking: OpenFGA operations
            .flatMap(ctx -> 
                assignRolesAndLink(ctx.getProject(), createdBy, tenantId)
                    .andThen(Single.just(ctx))
            )
            // 5. Fire-and-forget: Async tasks
            .doOnSuccess(ctx -> {
                executeAsyncTasks(ctx.getProject(), tenantId, chUsername, chPassword, createdBy, ctx.getRawApiKey(), ctx.getUsageLimits()).subscribe();
            })
            .map(ProjectWithContext::getProject)
            .doOnSuccess(createdProject -> 
                log.info("Project created successfully: projectId={}", createdProject.getProjectId())
            )
            .doOnError(error -> 
                log.error("Failed to create project: tenantId={}, name={}", tenantId, name, error)
            );
    }

    private Single<Project> executeTransaction(
            Project project,
            String chUsername,
            String chPassword,
            EncryptedData encryptedApiKey,
            String apiKeyDigest,
            String usageLimitsJson,
            org.dreamhorizon.pulseserver.service.configs.models.ConfigData configData,
            String createdBy) {

        return mysqlClient.getWriterPool().rxGetConnection()
            .flatMap(conn -> conn.rxBegin()
                .flatMap(tx -> {
                    log.debug("Transaction started for project: {}", project.getProjectId());

                    return projectDao.createProject(conn, project)
                        .flatMap(createdProject -> 
                            credentialsDao.saveCredentials(conn, project.getProjectId(), chUsername, chPassword)
                                .map(creds -> createdProject)
                        )
                        .flatMap(createdProject ->
                            apiKeyDao.createApiKey(
                                conn,
                                project.getProjectId(),
                                DEFAULT_API_KEY_DISPLAY_NAME,
                                encryptedApiKey.getEncryptedValue(),
                                encryptedApiKey.getSalt(),
                                apiKeyDigest,
                                null, // expiresAt - default key never expires
                                createdBy
                            ).map(apiKey -> createdProject)
                        )
                        .flatMap(createdProject ->
                            usageLimitDao.createUsageLimit(conn, project.getProjectId(), usageLimitsJson, createdBy)
                                .map(limit -> createdProject)
                        )
                        .flatMap(createdProject ->
                            sdkConfigsDao.createInitialConfig(conn, project.getProjectId(), configData)
                                .map(sdkConfig -> createdProject)
                        )
                        .flatMap(createdProject -> 
                            tx.rxCommit()
                                .doOnComplete(() -> log.debug("Transaction committed for project: {}", project.getProjectId()))
                                .toSingleDefault(createdProject)
                        )
                        .onErrorResumeNext(err -> {
                            log.error("Transaction failed for project: {}, rolling back", project.getProjectId(), err);
                            return tx.rxRollback()
                                .andThen(Single.error(err));
                        });
                })
                .doFinally(conn::close)
            );
    }

    private Completable assignRolesAndLink(Project project, String createdBy, String tenantId) {
        log.debug("Assigning roles and linking project: {} to tenant: {}", project.getProjectId(), tenantId);
        return openFgaService.assignProjectRole(createdBy, project.getProjectId(), "admin")
            .andThen(openFgaService.linkProjectToTenant(project.getProjectId(), tenantId))
            .doOnComplete(() -> log.debug("OpenFGA operations completed for project: {}", project.getProjectId()))
            .doOnError(err -> log.error("OpenFGA operations failed for project: {}", project.getProjectId(), err));
    }

    private Completable executeAsyncTasks(
            Project project, 
            String tenantId, 
            String chUsername, 
            String chPassword,
            String createdBy,
            String rawApiKey,
            Map<String, UsageLimitValue> usageLimits) {

        String projectId = project.getProjectId();
        log.debug("Executing async tasks for project: {}", projectId);

        return Completable.mergeArrayDelayError(
            // ClickHouse user creation
            clickhouseProjectService.createClickhouseUserAndPolicies(projectId, chUsername, chPassword)
                .doOnComplete(() -> log.info("ClickHouse user created for project: {}", projectId))
                .doOnError(err -> log.warn("ClickHouse user creation failed for project: {}, will retry on first query", projectId, err))
                .onErrorComplete(),

            // S3 upload of SDK config
            uploadConfigDetailService.pushInteractionDetailsToObjectStore(tenantId, projectId)
                .ignoreElement()
                .doOnComplete(() -> log.info("SDK config uploaded to S3 for project: {}", projectId))
                .doOnError(err -> log.warn("S3 upload failed for project: {}, will serve from DB", projectId, err))
                .onErrorComplete(),

            // Email notification
            sendProjectCreatedEmailAsync(createdBy, project, rawApiKey)
                .doOnComplete(() -> log.info("Email notification sent for project: {}", projectId))
                .doOnError(err -> log.warn("Email notification failed for project: {}", projectId, err))
                .onErrorComplete(),

            // Redis sync (API key mapping + usage limit credits)
            syncProjectToRedis(projectId, rawApiKey, usageLimits)
                .doOnComplete(() -> log.info("Redis sync completed for project: {}", projectId))
                .doOnError(err -> log.warn("Redis sync failed for project: {}, cron will retry", projectId, err))
                .onErrorComplete()
        );
    }

    private Completable sendProjectCreatedEmailAsync(String createdBy, Project project, String rawApiKey) {
        return userDao.getUserById(createdBy)
            .flatMapCompletable(user -> {
                if (user.getEmail() != null) {
                    emailService.sendProjectCreatedEmail(
                        user.getEmail(),
                        project.getName(),
                        project.getProjectId(),
                        rawApiKey
                    );
                }
                return Completable.complete();
            })
            .onErrorComplete(); // Don't fail if user not found
    }

    /**
     * Syncs project data to Redis (API key mapping + usage limit credits).
     * Fire-and-forget - failures are logged but don't fail project creation.
     * A cron job will retry failed syncs.
     */
    private Completable syncProjectToRedis(String projectId, String rawApiKey, Map<String, UsageLimitValue> usageLimits) {
        log.debug("Syncing project to Redis: {}", projectId);

        RedisUsageLimitCredits credits = RedisUsageLimitCredits.fromUsageLimits(projectId, usageLimits);

        return Completable.mergeArrayDelayError(
            redisService.saveApiKeyMapping(rawApiKey, projectId),
            redisService.saveUsageLimitCredits(credits)
        );
    }

    // ==================== HELPER METHODS ====================

    private String generateProjectId() {
        return "proj-" + UUID.randomUUID().toString();
    }

    private String generateApiKey(String projectId) {
        return projectId + "_" + SecureRandomUtil.generateAlphanumeric(API_KEY_RANDOM_LENGTH);
    }

    private String serializeUsageLimits(Map<String, UsageLimitValue> limits) {
        try {
            return objectMapper.writeValueAsString(limits);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize usage limits", e);
        }
    }

    // ==================== EXISTING METHODS (unchanged) ====================

    public Single<List<ProjectSummaryDto>> getProjectsForUser(String userId, String tenantId) {
        return projectDao.getProjectsByTenantId(tenantId)
            .flatMap(projects -> 
                Flowable.fromIterable(projects)
                    .flatMapSingle(project -> 
                        openFgaService.getUserRoleInProject(userId, project.getProjectId())
                            .map(roleOpt -> ProjectSummaryDto.builder()
                                .projectId(project.getProjectId())
                                .name(project.getName())
                                .description(project.getDescription())
                                .role(roleOpt.orElse("none"))
                                .isActive(project.getIsActive())
                                .createdAt(project.getCreatedAt())
                                .build())
                    )
                    .toList()
            )
            .doOnSuccess(projects -> 
                log.debug("Retrieved {} projects for user {} in tenant {}", 
                    projects.size(), userId, tenantId)
            )
            .doOnError(error -> 
                log.error("Failed to get projects for user: userId={}, tenantId={}", 
                    userId, tenantId, error)
            );
    }

    public Single<ProjectDetailsDto> getProjectDetails(String userId, String projectId) {
        return projectDao.getProjectByProjectId(projectId)
            .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)))
            .flatMap(project -> 
                openFgaService.getUserRoleInProject(userId, projectId)
                    .flatMap(roleOpt -> {
                        if (roleOpt.isEmpty()) {
                            return Single.error(new RuntimeException(
                                "Access denied: User has no access to project " + projectId));
                        }
                        
                        String role = roleOpt.get();
                        boolean isAdmin = "admin".equals(role);
                        
                        return Single.just(ProjectDetailsDto.builder()
                            .projectId(project.getProjectId())
                            .tenantId(project.getTenantId())
                            .name(project.getName())
                            .description(project.getDescription())
                            .apiKey(isAdmin ? project.getApiKey() : null)
                            .isActive(project.getIsActive())
                            .createdBy(project.getCreatedBy())
                            .createdAt(project.getCreatedAt())
                            .updatedAt(project.getUpdatedAt())
                            .userRole(role)
                            .build());
                    })
            )
            .doOnSuccess(details -> 
                log.debug("Retrieved project details: projectId={}, userRole={}", 
                    projectId, details.getUserRole())
            )
            .doOnError(error -> 
                log.error("Failed to get project details: projectId={}, userId={}", 
                    projectId, userId, error)
            );
    }

    public Single<Project> getProjectById(String projectId) {
        return projectDao.getProjectByProjectId(projectId)
            .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)));
    }

    @Deprecated
    public Single<Project> getProjectByApiKey(String apiKey) {
        return Single.error(new RuntimeException("API key lookup not implemented - use ProjectApiKeyService instead"));
    }

    public Single<Project> updateProject(String projectId, String name, String description) {
        return projectDao.getProjectByProjectId(projectId)
            .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)))
            .flatMap(existing -> {
                Project updated = Project.builder()
                    .projectId(projectId)
                    .name(name)
                    .description(description)
                    .build();
                return projectDao.updateProject(updated);
            })
            .doOnSuccess(project -> 
                log.info("Project updated: projectId={}", projectId)
            );
    }

    public Single<Void> deactivateProject(String projectId) {
        return projectDao.deactivateProject(projectId)
            .andThen(Single.just((Void) null))
            .doOnSuccess(v -> 
                log.info("Project deactivated: projectId={}", projectId)
            );
    }

    public Single<Integer> getActiveProjectCount(String tenantId) {
        return projectDao.getActiveProjectCount(tenantId)
            .map(Long::intValue);
    }

    // ==================== CONTEXT CLASSES ====================

    @lombok.RequiredArgsConstructor
    @lombok.Getter
    private static class UsageLimitsContext {
        private final Map<String, UsageLimitValue> limits;
        private final String json;
    }

    @lombok.RequiredArgsConstructor
    @lombok.Getter
    private static class ProjectWithContext {
        private final Project project;
        private final Map<String, UsageLimitValue> usageLimits;
        private final String rawApiKey;
    }
}
