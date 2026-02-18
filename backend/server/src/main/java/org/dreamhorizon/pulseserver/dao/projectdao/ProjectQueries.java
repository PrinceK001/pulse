package org.dreamhorizon.pulseserver.dao.projectdao;

/**
 * SQL queries for Project DAO operations.
 */
public class ProjectQueries {
    
    public static final String INSERT_PROJECT = 
        "INSERT INTO projects (project_id, tenant_id, name, description, api_key, is_active, created_by) " +
        "VALUES (?, ?, ?, ?, ?, ?, ?)";
    
    public static final String GET_PROJECT_BY_ID = 
        "SELECT id, project_id, tenant_id, name, description, api_key, is_active, created_by, created_at, updated_at " +
        "FROM projects WHERE project_id = ?";
    
    public static final String GET_PROJECT_BY_API_KEY = 
        "SELECT id, project_id, tenant_id, name, description, api_key, is_active, created_by, created_at, updated_at " +
        "FROM projects WHERE api_key = ? AND is_active = TRUE";
    
    public static final String GET_PROJECTS_BY_TENANT_ID = 
        "SELECT id, project_id, tenant_id, name, description, api_key, is_active, created_by, created_at, updated_at " +
        "FROM projects WHERE tenant_id = ? ORDER BY created_at DESC";
    
    public static final String GET_ACTIVE_PROJECT_COUNT = 
        "SELECT COUNT(*) as count FROM projects WHERE tenant_id = ? AND is_active = TRUE";
    
    public static final String UPDATE_PROJECT = 
        "UPDATE projects SET name = ?, description = ? WHERE project_id = ?";
    
    public static final String DEACTIVATE_PROJECT = 
        "UPDATE projects SET is_active = FALSE WHERE project_id = ?";
    
    public static final String ACTIVATE_PROJECT = 
        "UPDATE projects SET is_active = TRUE WHERE project_id = ?";
}
