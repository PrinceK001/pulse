import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useProjectContext, useTenantContext } from '../../contexts';
import { ROUTES } from '../../constants';

interface ProjectGuardProps {
  children: React.ReactNode;
}

/**
 * Guard component that ensures a project is selected before accessing protected routes
 * Also syncs project context from URL to context
 */
export function ProjectGuard({ children }: ProjectGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { projectId: contextProjectId, switchProject } = useProjectContext();
  const { projects, tenantId } = useTenantContext();

  useEffect(() => {
    const excludedPaths = [
      ROUTES.LOGIN.basePath,
      ROUTES.ONBOARDING.basePath,
      ROUTES.PRICING.basePath,
    ];

    // Check if path is organization-scoped (/:organizationId/...)
    const isOrganizationPath = /^\/[^/]+\/(projects|members)/.test(location.pathname);
    
    // Check if path is onboarding page for a project (should not trigger guard)
    const isOnboardingPath = /^\/projects\/[^/]+\/onboarding/.test(location.pathname);

    const isExcludedPath = excludedPaths.some(path => 
      location.pathname.startsWith(path)
    );

    console.log('[ProjectGuard] Check:', {
      pathname: location.pathname,
      contextProjectId,
      isExcludedPath,
      isOrganizationPath,
      isOnboardingPath,
      projectsCount: projects.length
    });

    // Skip guard for onboarding pages
    if (isOnboardingPath) {
      console.log('[ProjectGuard] Skipping guard - on onboarding page');
      return;
    }

    // Extract project ID from URL if on project-scoped route
    const projectIdMatch = location.pathname.match(/^\/projects\/([^/]+)/);
    const urlProjectId = projectIdMatch ? projectIdMatch[1] : null;

    // If we're on a project-scoped route
    if (urlProjectId && !isExcludedPath) {
      // Sync URL project ID with context if different
      if (!contextProjectId || contextProjectId !== urlProjectId) {
        console.log(`[ProjectGuard] Syncing project context from URL: ${urlProjectId}`);
        
        // Check if user has access to this project
        const hasAccess = projects.some(p => p.projectId === urlProjectId);
        console.log(`[ProjectGuard] Access check: hasAccess=${hasAccess}, projectsInContext=${projects.length}`);
        
        if (hasAccess) {
          console.log('[ProjectGuard] User has access, switching to project');
          switchProject(urlProjectId);
        } else if (projects.length > 0 && tenantId) {
          // Project not found or no access - redirect to organization projects
          console.log('[ProjectGuard] No access to project, redirecting to organization projects');
          navigate(`/${tenantId}/projects`);
        } else {
          console.log('[ProjectGuard] Projects not loaded yet, waiting...');
        }
        // If projects array is empty, wait for it to load (handled by TenantContext)
      }
    } else if (!contextProjectId && !isExcludedPath && !urlProjectId && !isOrganizationPath) {
      // No project context and not on excluded route, project-scoped route, or organization route
      console.log('[ProjectGuard] No project context, redirecting to organization projects');
      if (tenantId) {
        navigate(`/${tenantId}/projects`);
      }
    }
  }, [contextProjectId, location.pathname, navigate, projects, switchProject, tenantId]);

  return <>{children}</>;
}
