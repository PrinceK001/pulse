package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.projectdao.ProjectDao;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.model.Project;
import org.dreamhorizon.pulseserver.service.configs.DefaultSdkConfigTemplate;

/**
 * Service for project management operations.
 * Handles project creation, updates, and integrates with OpenFGA for permissions.
 * Includes ClickHouse per-project database isolation with dedicated users and row policies.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectService {
    
    private final ProjectDao projectDao;
    private final OpenFgaService openFgaService;
    private final ClickhouseProjectService clickhouseProjectService;
    private final org.dreamhorizon.pulseserver.service.configs.ConfigService configService;
    
    /**
     * Create a new project within a tenant.
     * This method:
     * 1. Generates project ID from sanitized name + UUID
     * 2. Creates project in MySQL
     * 3. Sets up dedicated ClickHouse user and row policies
     * 4. Creates default SDK configuration
     * 5. Assigns creator as project admin in OpenFGA
     * 6. Links project to tenant in OpenFGA
     *
     * Note: API key generation and storage is handled separately via project_api_keys table
     *
     * @param tenantId Parent tenant ID
     * @param name Project name
     * @param description Project description
     * @param createdBy User ID of creator
     * @return Single<Project> Created project
     */
    public Single<Project> createProject(String tenantId, String name, String description, String createdBy) {
        // Sanitize name: remove special chars, convert to lowercase, limit length
        // This ensures project IDs are URL-safe and human-readable
        String sanitizedName = name.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")      // Replace non-alphanumeric with dash
            .replaceAll("-+", "-")              // Collapse multiple dashes
            .replaceAll("^-|-$", "");           // Remove leading/trailing dashes

        // Limit sanitized name to 30 chars and append short UUID
        if (sanitizedName.length() > 30) {
            sanitizedName = sanitizedName.substring(0, 30);
        }

        String projectId = sanitizedName + "-" + UUID.randomUUID().toString().substring(0, 8);

        log.info("Creating project: projectId={}, tenantId={}, name={}, createdBy={}",
            projectId, tenantId, name, createdBy);

        Project project = Project.builder()
            .projectId(projectId)
            .tenantId(tenantId)
            .name(name)
            .description(description)
            .apiKey(null)  // Managed separately via project_api_keys table
            .isActive(true)
            .createdBy(createdBy)
            .build();

        return projectDao.createProject(project)
            .flatMap(created ->
                // Setup ClickHouse user and row policies
                clickhouseProjectService.setupProjectClickhouseUser(projectId)
                    .andThen(createDefaultSdkConfig(projectId, tenantId, createdBy))
                    .andThen(assignRolesAndLink(created, createdBy, tenantId))
            )
            .doOnSuccess(created ->
                log.info("Project created successfully: projectId={}", created.getProjectId())
            )
            .doOnError(error ->
                log.error("Failed to create project: projectId={}, tenantId={}",
                    projectId, tenantId, error)
            );
    }

    private Single<Project> assignRolesAndLink(Project project, String createdBy, String tenantId) {
        return openFgaService.assignProjectRole(createdBy, project.getProjectId(), "admin")
            .andThen(openFgaService.linkProjectToTenant(project.getProjectId(), tenantId))
            .andThen(Single.just(project));
    }

    /**
     * Creates a default SDK configuration for a new project.
     * Uses ProjectContext and TenantContext to ensure the config is associated with the correct project and tenant.
     *
     * @param projectId Project ID
     * @param tenantId Tenant ID
     * @param createdBy User who created the project
     * @return Completable that completes when default config is created
     */
    private io.reactivex.rxjava3.core.Completable createDefaultSdkConfig(String projectId, String tenantId, String createdBy) {
        // Set both project and tenant context BEFORE creating the async chain to avoid race condition
        ProjectContext.setProjectId(projectId);
        org.dreamhorizon.pulseserver.tenant.TenantContext.setTenantId(tenantId);

        return configService.createSdkConfig(DefaultSdkConfigTemplate.createDefaultConfig(createdBy))
            .flatMapCompletable(config -> {
                log.info("Created default SDK config for project: projectId={}, tenantId={}, version={}",
                    projectId, tenantId, config.getVersion());
                return io.reactivex.rxjava3.core.Completable.complete();
            })
            .doFinally(() -> {
                // Clear both contexts after config creation
                ProjectContext.clear();
                org.dreamhorizon.pulseserver.tenant.TenantContext.clear();
            })
            .doOnError(error ->
                log.error("Failed to create default SDK config for project: projectId={}, tenantId={}",
                    projectId, tenantId, error)
            );
    }
    
    /**
     * Get project by ID (internal use, no permission check).
     * 
     * @param projectId Project ID
     * @return Single<Project> The project
     */
    public Single<Project> getProjectById(String projectId) {
        return projectDao.getProjectById(projectId)
            .switchIfEmpty(Single.error(new RuntimeException("Project not found: " + projectId)));
    }
    
    /**
     * Get project by API key (used by SDK authentication).
     * 
     * Note: This method is deprecated - API key lookups will be handled via project_api_keys table
     * @param apiKey API key
     * @return Single<Project> error (API keys not in projects table)
     * @deprecated Use ProjectApiKeyService for API key lookups
     */
    @Deprecated
    public Single<Project> getProjectByApiKey(String apiKey) {
        return Single.error(new RuntimeException("API key lookup not implemented - use ProjectApiKeyService instead"));
    }
    
    /**
     * Update project information.
     * Requires project admin permission (checked by caller).
     * 
     * @param projectId Project ID
     * @param name Updated name
     * @param description Updated description
     * @return Single<Project> Updated project
     */
    public Single<Project> updateProject(String projectId, String name, String description) {
        return projectDao.updateProject(projectId, name, description)
            .andThen(projectDao.getProjectById(projectId))
            .switchIfEmpty(Single.error(new RuntimeException("Project not found after update")))
            .doOnSuccess(project -> 
                log.info("Project updated: projectId={}", projectId)
            );
    }
    
    /**
     * Deactivate a project.
     * Requires project admin permission (checked by caller).
     * 
     * @param projectId Project ID
     * @return Single<Void>
     */
    public Single<Void> deactivateProject(String projectId) {
        return projectDao.deactivateProject(projectId)
            .andThen(Single.just((Void) null))
            .doOnSuccess(v -> 
                log.info("Project deactivated: projectId={}", projectId)
            );
    }
    
    /**
     * Get count of active projects for a tenant (for quota checking).
     * 
     * @param tenantId Tenant ID
     * @return Single<Integer> Active project count
     */
    public Single<Integer> getActiveProjectCount(String tenantId) {
        return projectDao.getActiveProjectCount(tenantId);
    }
}
