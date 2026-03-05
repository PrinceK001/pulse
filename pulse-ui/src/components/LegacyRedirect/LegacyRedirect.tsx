import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { getProjectContext } from '../../helpers/projectContext';
import { Box, Loader, Text } from '@mantine/core';
import { getCookies } from '../../helpers/cookies';
import { COOKIES_KEY } from '../../constants';

export function LegacyRedirect() {
  const navigate = useNavigate();
  const location = useLocation();
  const projectContext = getProjectContext();

  useEffect(() => {
    if (projectContext) {
      const path = location.pathname;
      const newPath = `/projects/${projectContext.projectId}${path}`;
      console.log(`[LegacyRedirect] Redirecting ${path} -> ${newPath}`);
      navigate(newPath, { replace: true });
    } else {
      console.log('[LegacyRedirect] No project context, redirecting to organization projects');
      const tenantId = getCookies(COOKIES_KEY.TENANT_ID);
      if (tenantId && tenantId !== "undefined") {
        navigate(`/${tenantId}/projects`, { replace: true });
      } else {
        navigate('/', { replace: true });
      }
    }
  }, [projectContext, location, navigate]);

  return (
    <Box
      style={{
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100vh',
        gap: '1rem',
      }}
    >
      <Loader size="lg" />
      <Text size="lg" c="dimmed">Redirecting...</Text>
    </Box>
  );
}
