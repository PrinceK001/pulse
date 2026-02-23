package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.project.ProjectDao;
import org.dreamhorizon.pulseserver.dto.ProjectDetailsDto;
import org.dreamhorizon.pulseserver.dto.ProjectSummaryDto;
import org.dreamhorizon.pulseserver.model.Project;
import org.dreamhorizon.pulseserver.service.configs.DefaultSdkConfigTemplate;
import org.dreamhorizon.pulseserver.context.ProjectContext;

import java.util.List;
import java.util.UUID;

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
     * 1. Generates project ID
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
        String projectId = "proj-" + UUID.randomUUID().toString();
        
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
                    .andThen(createDefaultSdkConfig(projectId, createdBy))
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
     * Uses ProjectContext to ensure the config is associated with the correct project.
     * 
     * @param projectId Project ID
     * @param createdBy User who created the project
     * @return Completable that completes when default config is created
     */
    private io.reactivex.rxjava3.core.Completable createDefaultSdkConfig(String projectId, String createdBy) {
        // Set project context BEFORE creating the async chain to avoid race condition
        ProjectContext.setProjectId(projectId);
        
        return configService.createSdkConfig(DefaultSdkConfigTemplate.createDefaultConfig(createdBy))
            .flatMapCompletable(config -> {
                log.info("Created default SDK config for project: projectId={}, version={}", 
                    projectId, config.getVersion());
                return io.reactivex.rxjava3.core.Completable.complete();
            })
            .doFinally(() -> {
                // Clear context after config creation
                ProjectContext.clear();
            })
            .doOnError(error -> 
                log.error("Failed to create default SDK config for project: projectId={}", 
                    projectId, error)
            );
    }
    
    /**
     * Get all projects for a user within a tenant, enriched with user's role in each project.
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return Single<List<ProjectSummaryDto>> List of project summaries with roles
     */
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
    
    /**
     * Get detailed project information with permission check.
     * API key is only included if user is a project admin.
     * 
     * @param userId User ID
     * @param projectId Project ID
     * @return Single<ProjectDetailsDto> Detailed project information
     */
    public Single<ProjectDetailsDto> getProjectDetails(String userId, String projectId) {
        return projectDao.getProjectById(projectId)
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
                            .apiKey(isAdmin ? project.getApiKey() : null)  // Only admins see API key
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
