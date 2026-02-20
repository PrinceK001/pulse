import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getProjectContext, getProjectIdFromPath, setProjectContext } from '../../helpers/projectContext';
import { getUserProjects } from '../../helpers/getUserProjects';
import { ROUTES } from '../../constants';

interface ProjectGuardProps {
  children: React.ReactNode;
}

/**
 * Guard component that ensures a project is selected before accessing protected routes
 * Also syncs project context from URL to cookies
 */
export function ProjectGuard({ children }: ProjectGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const projectContext = getProjectContext();
  const projectIdFromUrl = getProjectIdFromPath(location.pathname);

  useEffect(() => {
    const excludedPaths = [
      ROUTES.LOGIN.basePath,
      ROUTES.ONBOARDING.basePath,
      ROUTES.PRICING.basePath,
      ROUTES.PROJECT_SELECTION.basePath,
      '/organization',
    ];

    const isExcludedPath = excludedPaths.some(path => 
      location.pathname.startsWith(path)
    );

    // If we're on a project-scoped route
    if (projectIdFromUrl && !isExcludedPath) {
      // Sync URL project ID with cookie if different
      if (!projectContext || projectContext.projectId !== projectIdFromUrl) {
        console.log(`[ProjectGuard] Syncing project context from URL: ${projectIdFromUrl}`);
        
        // Fetch project details and update context
        getUserProjects().then(response => {
          const project = response.data?.projects.find(p => p.projectId === projectIdFromUrl);
          if (project) {
            setProjectContext({
              projectId: project.projectId,
              projectName: project.name,
            });
          } else {
            // Project not found or no access
            console.log('[ProjectGuard] Project not found, redirecting to project selection');
            navigate(ROUTES.PROJECT_SELECTION.basePath);
          }
        });
      }
    } else if (!projectContext && !isExcludedPath && !projectIdFromUrl) {
      // No project context and not on excluded route or project-scoped route
      console.log('[ProjectGuard] No project context, redirecting to project selection');
      navigate(ROUTES.PROJECT_SELECTION.basePath);
    }
  }, [projectContext, projectIdFromUrl, location.pathname, navigate]);

  return <>{children}</>;
}
