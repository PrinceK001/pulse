package org.dreamhorizon.pulseserver.dao.projectdao;

/**
 * SQL queries for project operations.
 */
public final class ProjectQueries {

  private ProjectQueries() {
    // Utility class
  }

  public static final String INSERT_PROJECT =
      "INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by) "
          + "VALUES (?, ?, ?, ?, ?, ?, ?)";

  public static final String GET_PROJECT_BY_ID =
      "SELECT project_id, tenant_id, name, description, slug, is_active, created_at, updated_at, created_by "
          + "FROM projects WHERE project_id = ?";

  public static final String GET_PROJECT_BY_ID_AND_TENANT =
      "SELECT project_id, tenant_id, name, description, slug, is_active, created_at, updated_at, created_by "
          + "FROM projects WHERE project_id = ? AND tenant_id = ?";

  public static final String GET_PROJECTS_BY_TENANT =
      "SELECT project_id, tenant_id, name, description, slug, is_active, created_at, updated_at, created_by "
          + "FROM projects WHERE tenant_id = ? AND is_active = TRUE ORDER BY created_at DESC";

  public static final String GET_ALL_PROJECTS_BY_TENANT =
      "SELECT project_id, tenant_id, name, description, slug, is_active, created_at, updated_at, created_by "
          + "FROM projects WHERE tenant_id = ? ORDER BY created_at DESC";

  public static final String GET_PROJECT_BY_SLUG =
      "SELECT project_id, tenant_id, name, description, slug, is_active, created_at, updated_at, created_by "
          + "FROM projects WHERE tenant_id = ? AND slug = ?";

  public static final String UPDATE_PROJECT =
      "UPDATE projects SET name = ?, description = ?, slug = ?, updated_at = CURRENT_TIMESTAMP "
          + "WHERE project_id = ? AND tenant_id = ?";

  public static final String DEACTIVATE_PROJECT =
      "UPDATE projects SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP "
          + "WHERE project_id = ? AND tenant_id = ?";

  public static final String ACTIVATE_PROJECT =
      "UPDATE projects SET is_active = TRUE, updated_at = CURRENT_TIMESTAMP "
          + "WHERE project_id = ? AND tenant_id = ?";

  public static final String DELETE_PROJECT =
      "DELETE FROM projects WHERE project_id = ? AND tenant_id = ?";

  public static final String CHECK_PROJECT_EXISTS =
      "SELECT COUNT(*) as count FROM projects WHERE project_id = ?";

  public static final String CHECK_SLUG_EXISTS =
      "SELECT COUNT(*) as count FROM projects WHERE tenant_id = ? AND slug = ? AND project_id != ?";

  public static final String COUNT_PROJECTS_BY_TENANT =
      "SELECT COUNT(*) as count FROM projects WHERE tenant_id = ? AND is_active = TRUE";
}

