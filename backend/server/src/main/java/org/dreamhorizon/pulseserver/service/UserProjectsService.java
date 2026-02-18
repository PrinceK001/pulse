package org.dreamhorizon.pulseserver.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.projectdao.ProjectDao;
import org.dreamhorizon.pulseserver.model.Project;

import java.util.List;

/**
 * Service for fetching user's accessible projects.
 * Uses OpenFGA to determine which projects a user can access.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class UserProjectsService {
    
    private final OpenFgaService openFgaService;
    private final ProjectDao projectDao;
    
    /**
     * Get all projects accessible to a user within a tenant.
     * 
     * @param userId User ID
     * @param tenantId Tenant ID
     * @return UserProjectsResult with project list and redirect hint
     */
    public Single<UserProjectsResult> getUserProjects(String userId, String tenantId) {
        log.info("Fetching projects for user: userId={}, tenantId={}", userId, tenantId);
        
        return openFgaService.getUserProjectsInTenant(userId, tenantId)
            .flatMap(projectIds -> {
                if (projectIds == null || projectIds.isEmpty()) {
                    log.info("User has no projects in tenant: userId={}, tenantId={}", userId, tenantId);
                    return Single.just(UserProjectsResult.builder()
                        .projects(List.of())
                        .redirectTo(null)
                        .build());
                }
                
                // Fetch project details for each project ID
                return Flowable.fromIterable(projectIds)
                    .flatMapSingle(projectId -> 
                        projectDao.getProjectById(projectId)
                            .toSingle()
                            .flatMap(project -> 
                                openFgaService.getUserRoleInProject(userId, projectId)
                                    .map(roleOpt -> ProjectSummary.builder()
                                        .projectId(project.getProjectId())
                                        .name(project.getName())
                                        .description(project.getDescription())
                                        .isActive(project.getIsActive())
                                        .role(roleOpt.orElse("viewer"))
                                        .build())
                            )
                    )
                    .toList()
                    .map(projects -> {
                        // Calculate redirect hint
                        String redirectTo;
                        if (projects.size() == 1) {
                            redirectTo = "/projects/" + projects.get(0).getProjectId();
                            log.info("Single project, redirecting to: {}", redirectTo);
                        } else {
                            redirectTo = "/projects/select";
                            log.info("Multiple projects ({}), redirecting to selection page", projects.size());
                        }
                        
                        return UserProjectsResult.builder()
                            .projects(projects)
                            .redirectTo(redirectTo)
                            .build();
                    });
            })
            .doOnError(error -> 
                log.error("Failed to fetch projects: userId={}, tenantId={}", userId, tenantId, error)
            );
    }
    
    /**
     * Result of getUserProjects operation.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserProjectsResult {
        private List<ProjectSummary> projects;
        private String redirectTo;
    }
    
    /**
     * Summary of a project with user's role.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ProjectSummary {
        private String projectId;
        private String name;
        private String description;
        private Boolean isActive;
        private String role;
    }
}
