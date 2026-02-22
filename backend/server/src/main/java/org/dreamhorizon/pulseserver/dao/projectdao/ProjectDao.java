package org.dreamhorizon.pulseserver.dao.projectdao;

import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.DEACTIVATE_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_ACTIVE_PROJECT_COUNT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECTS_BY_TENANT_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.INSERT_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.UPDATE_PROJECT;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.model.Project;

/**
 * Data Access Object for Project operations.
 * Handles CRUD operations for projects within tenants.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectDao {
  private final MysqlClient mysqlClient;

  /**
   * Create a new project in the database.
   * Note: api_key is NOT inserted here - it will be managed via project_api_keys table
   *
   * @param project Project object to create
   * @return Single containing the created project
   */
  public Single<Project> createProject(Project project) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_PROJECT)
        .rxExecute(
            Tuple.wrap(java.util.Arrays.asList(
                project.getProjectId(),
                project.getTenantId(),
                project.getName(),
                project.getDescription(),
                true,
                project.getCreatedBy())))
        .map(result -> {
          log.info("Created project: {} for tenant: {}", project.getProjectId(), project.getTenantId());
          return project;
        })
        .doOnError(error -> log.error("Failed to create project: {}", project.getProjectId(), error));
  }

  /**
   * Get project by project ID.
   *
   * @param projectId Project ID (format: proj-{uuid})
   * @return Maybe containing the project if found
   */
  public Maybe<Project> getProjectById(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECT_BY_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            log.debug("Project not found: {}", projectId);
            return Maybe.empty();
          }
          return Maybe.just(mapRowToProject(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch project: {}", projectId, error));
  }

  /**
   * Get project by API key.
   * Note: This method is deprecated - API key lookups should use project_api_keys table
   *
   * @param apiKey API key
   * @return Maybe empty (API keys not stored in projects table)
   * @deprecated API keys are managed in project_api_keys table
   */
  @Deprecated
  public Maybe<Project> getProjectByApiKey(String apiKey) {
    log.warn("getProjectByApiKey called but api_key is not in projects table - use ProjectApiKeyDao instead");
    return Maybe.empty();
  }

  /**
   * Get all projects for a tenant.
   *
   * @param tenantId Tenant ID
   * @return Single containing list of projects
   */
  public Single<List<Project>> getProjectsByTenantId(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECTS_BY_TENANT_ID)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          List<Project> projects = new ArrayList<>();
          for (Row row : rowSet) {
            projects.add(mapRowToProject(row));
          }
          log.debug("Found {} projects for tenant: {}", projects.size(), tenantId);
          return projects;
        })
        .doOnError(error -> log.error("Failed to fetch projects for tenant: {}", tenantId, error));
  }

  /**
   * Get count of active projects for a tenant.
   *
   * @param tenantId Tenant ID
   * @return Single containing the count
   */
  public Single<Integer> getActiveProjectCount(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ACTIVE_PROJECT_COUNT)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          int count = row.getLong("count").intValue();
          log.debug("Active project count for tenant {}: {}", tenantId, count);
          return count;
        })
        .doOnError(error -> log.error("Failed to get project count for tenant: {}", tenantId, error));
  }

  /**
   * Update project information.
   *
   * @param projectId   Project ID
   * @param name        Updated name
   * @param description Updated description
   * @return Completable that completes when update is successful
   */
  public Completable updateProject(String projectId, String name, String description) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_PROJECT)
        .rxExecute(Tuple.of(name, description, projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("Project not found: " + projectId));
          }
          log.info("Updated project: {}", projectId);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to update project: {}", projectId, error));
  }

  /**
   * Deactivate a project.
   *
   * @param projectId Project ID
   * @return Completable that completes when deactivation is successful
   */
  public Completable deactivateProject(String projectId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No project found to deactivate: {}", projectId);
          }
          log.info("Deactivated project: {}", projectId);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate project: {}", projectId, error));
  }

  /**
   * Maps a database row to a Project object.
   * Note: apiKey field will be null - it's managed separately via project_api_keys table
   */
  private Project mapRowToProject(Row row) {
    return Project.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .apiKey(null)  // Not stored in projects table
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .apiKey(row.getString("api_key"))
        .isActive(row.getBoolean("is_active"))
        .createdBy(row.getString("created_by"))
        .createdAt(row.getLocalDateTime("created_at") != null ?
            row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null ?
            row.getLocalDateTime("updated_at").toString() : null)
        .build();
  }
}