package org.dreamhorizon.pulseserver.context;

/**
 * Thread-local context for storing the current project ID.
 * Used to pass project information through the request pipeline
 * without explicit parameter passing.
 * 
 * The project ID is extracted from the X-Project-ID header by TenantFilter
 * and used throughout the request processing for authorization and data filtering.
 */
public class ProjectContext {
    
    private static final ThreadLocal<String> projectIdContext = new ThreadLocal<>();
    
    /**
     * Set the project ID for the current thread/request.
     * 
     * @param projectId Project ID
     */
    public static void setProjectId(String projectId) {
        projectIdContext.set(projectId);
    }
    
    /**
     * Get the project ID for the current thread/request.
     * 
     * @return Project ID, or null if not set
     */
    public static String getProjectId() {
        return projectIdContext.get();
    }
    
    /**
     * Clear the project ID context.
     * MUST be called at the end of request processing to prevent memory leaks.
     */
    public static void clear() {
        projectIdContext.remove();
    }
    
    /**
     * Check if project ID is set in the current context.
     * 
     * @return true if project ID is set
     */
    public static boolean hasProjectId() {
        return projectIdContext.get() != null;
    }
}
