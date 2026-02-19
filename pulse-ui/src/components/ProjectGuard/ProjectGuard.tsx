import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getProjectContext } from '../../helpers/projectContext';
import { ROUTES } from '../../constants';

interface ProjectGuardProps {
  children: React.ReactNode;
}

/**
 * Guard component that ensures a project is selected before accessing protected routes
 */
export function ProjectGuard({ children }: ProjectGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const projectContext = getProjectContext();

  useEffect(() => {
    // If no project context and not on excluded routes, redirect to project selection
    const excludedPaths = [
      ROUTES.LOGIN.basePath,
      ROUTES.ONBOARDING.basePath,
      ROUTES.PROJECT_SELECTION.basePath,
    ];

    const isExcludedPath = excludedPaths.some(path => 
      location.pathname.startsWith(path)
    );

    if (!projectContext && !isExcludedPath) {
      navigate(ROUTES.PROJECT_SELECTION.basePath);
    }
  }, [projectContext, location.pathname, navigate]);

  return <>{children}</>;
}
