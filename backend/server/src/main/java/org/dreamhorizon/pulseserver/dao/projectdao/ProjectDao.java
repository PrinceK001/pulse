package org.dreamhorizon.pulseserver.dao.projectdao;

import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.ACTIVATE_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.CHECK_PROJECT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.CHECK_SLUG_EXISTS;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.COUNT_PROJECTS_BY_TENANT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.DEACTIVATE_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.DELETE_PROJECT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_ALL_PROJECTS_BY_TENANT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECTS_BY_TENANT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECT_BY_ID_AND_TENANT;
import static org.dreamhorizon.pulseserver.dao.projectdao.ProjectQueries.GET_PROJECT_BY_SLUG;
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
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.projectdao.models.Project;

/**
 * Data Access Object for project operations.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectDao {

  private final MysqlClient mysqlClient;

  /**
   * Create a new project.
   *
   * @param tenantId    The tenant ID
   * @param name        Project name
   * @param description Project description (optional)
   * @param slug        URL-friendly slug (optional, will be generated from name if not provided)
   * @param createdBy   User ID of the creator
   * @return Single emitting the created project
   */
  public Single<Project> createProject(String tenantId, String name, String description,
                                       String slug, String createdBy) {
    String projectId = UUID.randomUUID().toString();
    String projectSlug = slug != null && !slug.isBlank() ? slug : generateSlug(name);

    MySQLPool pool = mysqlClient.getWriterPool();
    Tuple params = Tuple.tuple()
        .addString(projectId)
        .addString(tenantId)
        .addString(name)
        .addString(description)
        .addString(projectSlug)
        .addBoolean(true)
        .addString(createdBy);
    
    return pool.preparedQuery(INSERT_PROJECT)
        .rxExecute(params)
        .flatMap(result -> {
          log.info("Created project: {} in tenant: {}", projectId, tenantId);
          return getProjectById(projectId)
              .switchIfEmpty(Single.error(new RuntimeException("Failed to retrieve created project")));
        })
        .doOnError(error -> log.error("Failed to create project: {} in tenant: {}", name, tenantId, error));
  }

  /**
   * Get a project by ID.
   *
   * @param projectId The project ID
   * @return Maybe emitting the project if found
   */
  public Maybe<Project> getProjectById(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECT_BY_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToProject(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch project: {}", projectId, error));
  }

  /**
   * Get a project by ID, ensuring it belongs to the specified tenant.
   *
   * @param projectId The project ID
   * @param tenantId  The tenant ID
   * @return Maybe emitting the project if found
   */
  public Maybe<Project> getProjectByIdAndTenant(String projectId, String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECT_BY_ID_AND_TENANT)
        .rxExecute(Tuple.of(projectId, tenantId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToProject(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch project: {} in tenant: {}", projectId, tenantId, error));
  }

  /**
   * Get all active projects for a tenant.
   *
   * @param tenantId The tenant ID
   * @return Single emitting list of projects
   */
  public Single<List<Project>> getProjectsByTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECTS_BY_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          List<Project> projects = new ArrayList<>();
          for (Row row : rowSet) {
            projects.add(mapRowToProject(row));
          }
          return projects;
        })
        .doOnError(error -> log.error("Failed to fetch projects for tenant: {}", tenantId, error));
  }

  /**
   * Get all projects (including inactive) for a tenant.
   *
   * @param tenantId The tenant ID
   * @return Single emitting list of projects
   */
  public Single<List<Project>> getAllProjectsByTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ALL_PROJECTS_BY_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          List<Project> projects = new ArrayList<>();
          for (Row row : rowSet) {
            projects.add(mapRowToProject(row));
          }
          return projects;
        })
        .doOnError(error -> log.error("Failed to fetch all projects for tenant: {}", tenantId, error));
  }

  /**
   * Get a project by slug within a tenant.
   *
   * @param tenantId The tenant ID
   * @param slug     The project slug
   * @return Maybe emitting the project if found
   */
  public Maybe<Project> getProjectBySlug(String tenantId, String slug) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_PROJECT_BY_SLUG)
        .rxExecute(Tuple.of(tenantId, slug))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToProject(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch project by slug: {} in tenant: {}", slug, tenantId, error));
  }

  /**
   * Update a project.
   *
   * @param projectId   The project ID
   * @param tenantId    The tenant ID
   * @param name        New name
   * @param description New description
   * @param slug        New slug
   * @return Completable that completes when update succeeds
   */
  public Completable updateProject(String projectId, String tenantId,
                                   String name, String description, String slug) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(UPDATE_PROJECT)
        .rxExecute(Tuple.of(name, description, slug, projectId, tenantId))
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
   * Deactivate a project (soft delete).
   *
   * @param projectId The project ID
   * @param tenantId  The tenant ID
   * @return Completable that completes when deactivation succeeds
   */
  public Completable deactivateProject(String projectId, String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_PROJECT)
        .rxExecute(Tuple.of(projectId, tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No project found to deactivate: {}", projectId);
          } else {
            log.info("Deactivated project: {}", projectId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to deactivate project: {}", projectId, error));
  }

  /**
   * Activate a project.
   *
   * @param projectId The project ID
   * @param tenantId  The tenant ID
   * @return Completable that completes when activation succeeds
   */
  public Completable activateProject(String projectId, String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(ACTIVATE_PROJECT)
        .rxExecute(Tuple.of(projectId, tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            return Completable.error(new RuntimeException("Project not found: " + projectId));
          }
          log.info("Activated project: {}", projectId);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to activate project: {}", projectId, error));
  }

  /**
   * Delete a project permanently.
   *
   * @param projectId The project ID
   * @param tenantId  The tenant ID
   * @return Completable that completes when deletion succeeds
   */
  public Completable deleteProject(String projectId, String tenantId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DELETE_PROJECT)
        .rxExecute(Tuple.of(projectId, tenantId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No project found to delete: {}", projectId);
          } else {
            log.info("Deleted project: {}", projectId);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to delete project: {}", projectId, error));
  }

  /**
   * Check if a project exists.
   *
   * @param projectId The project ID
   * @return Single emitting true if project exists
   */
  public Single<Boolean> projectExists(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_PROJECT_EXISTS)
        .rxExecute(Tuple.of(projectId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check project existence: {}", projectId, error));
  }

  /**
   * Check if a slug is already used by another project in the tenant.
   *
   * @param tenantId  The tenant ID
   * @param slug      The slug to check
   * @param projectId The current project ID (to exclude from check)
   * @return Single emitting true if slug is already in use
   */
  public Single<Boolean> slugExists(String tenantId, String slug, String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_SLUG_EXISTS)
        .rxExecute(Tuple.of(tenantId, slug, projectId != null ? projectId : ""))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check slug existence: {} in tenant: {}", slug, tenantId, error));
  }

  /**
   * Count active projects for a tenant.
   *
   * @param tenantId The tenant ID
   * @return Single emitting the count
   */
  public Single<Long> countProjectsByTenant(String tenantId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(COUNT_PROJECTS_BY_TENANT)
        .rxExecute(Tuple.of(tenantId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count");
        })
        .doOnError(error -> log.error("Failed to count projects for tenant: {}", tenantId, error));
  }

  private Project mapRowToProject(Row row) {
    return Project.builder()
        .projectId(row.getString("project_id"))
        .tenantId(row.getString("tenant_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .slug(row.getString("slug"))
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toString() : null)
        .updatedAt(row.getLocalDateTime("updated_at") != null
            ? row.getLocalDateTime("updated_at").toString() : null)
        .createdBy(row.getString("created_by"))
        .build();
  }

  private String generateSlug(String name) {
    if (name == null || name.isBlank()) {
      return UUID.randomUUID().toString().substring(0, 8);
    }
    return name.toLowerCase()
        .replaceAll("[^a-z0-9\\s-]", "")
        .replaceAll("\\s+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }
}
