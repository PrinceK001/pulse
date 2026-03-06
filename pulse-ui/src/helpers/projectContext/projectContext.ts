import { COOKIES_KEY } from '../../constants';

export interface ProjectInfo {
  projectId: string;
  projectName: string;
}

/**
 * DEPRECATED: Project context is now managed via React Context (ProjectContext)
 * and stored in sessionStorage. These cookie-based functions are no longer used.
 * 
 * @deprecated Use ProjectContext from contexts instead
 */
export const setProjectContext = (projectInfo: ProjectInfo) => {
  console.warn('[projectContext] setProjectContext is deprecated. Use ProjectContext instead.');
  // Legacy function - no longer used
};

/**
 * Get project context from sessionStorage (single source of truth).
 * Project context is managed by ProjectContext React Context.
 */
export const getProjectContext = (): ProjectInfo | null => {
  try {
    const stored = sessionStorage.getItem('pulse_project_context');
    if (stored) {
      const data = JSON.parse(stored);
      if (data.projectId && data.projectId !== 'undefined' && 
          data.projectName && data.projectName !== 'undefined') {
        return {
          projectId: data.projectId,
          projectName: data.projectName
        };
      }
    }
  } catch (error) {
    console.error('[projectContext] Failed to parse project context from sessionStorage:', error);
  }
  
  return null;
};

export const getProjectIdFromPath = (pathname: string): string | null => {
  const match = pathname.match(/^\/projects\/([^/]+)/);
  return match ? match[1] : null;
};

/**
 * DEPRECATED: Project context is now managed via React Context (ProjectContext)
 * and stored in sessionStorage.
 * 
 * @deprecated Use ProjectContext.clearProject() instead
 */
export const clearProjectContext = () => {
  console.warn('[projectContext] clearProjectContext is deprecated. Use ProjectContext.clearProject() instead.');
  // Legacy function - no longer used
};
