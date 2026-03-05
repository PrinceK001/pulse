import { AppShell } from "@mantine/core";
import { LayoutProps } from "./Layout.interface";
import { COOKIES_KEY, HEADER_CONFIG, LAYOUT_PAGE_CONSTANTS, ROUTES } from "../../constants";
import { useDisclosure } from "@mantine/hooks";
import { Header } from "../Header";
import { Navbar } from "../Navbar";
import { Main } from "../Main";
import { useLocation, useNavigate } from "react-router-dom";
import { Login } from "../../screens/Login";
import { useEffect, useRef, useState } from "react";
import { LoaderWithMessage } from "../LoaderWithMessage";
import { getCookies } from "../../helpers/cookies";
import { ProjectGuard } from "../ProjectGuard";
import { useTenantContext } from "../../contexts";

export function Layout({ children }: LayoutProps) {
  const navigate = useNavigate();
  const [opened, { toggle }] = useDisclosure(false);
  const { pathname } = useLocation();
  const { setTenantInfo, tenantId } = useTenantContext();
  const [checkingCredentials, setCheckingCredentials] = useState(true);
  const displayMessage = useRef<string>(
    LAYOUT_PAGE_CONSTANTS.CHECKING_CREDENTIALS,
  );

  // Show header on all authenticated pages except login and initial onboarding
  // This includes: project routes, organization routes (/:orgId/projects, /:orgId/members)
  const isLoginPage = pathname === ROUTES.LOGIN.path;
  const isInitialOnboarding = pathname === ROUTES.ONBOARDING.basePath;
  const shouldShowHeader = !isLoginPage && !isInitialOnboarding;

  useEffect(() => {
    const initializeAuth = async () => {
      const token = getCookies(COOKIES_KEY.ACCESS_TOKEN);
      if (!token || token === "undefined") {
        setCheckingCredentials(false);
        navigate(ROUTES.LOGIN.basePath);
        return;
      }

      // Initialize tenant context if tenantId exists in cookies but not in context
      const cookieTenantId = getCookies(COOKIES_KEY.TENANT_ID);
      const cookieTenantName = getCookies(COOKIES_KEY.TENANT_NAME);
      const cookieTier = getCookies(COOKIES_KEY.TIER);
      if (cookieTenantId && cookieTenantId !== 'undefined' && !tenantId) {
        console.log('[Layout] Initializing tenant context from cookies');
        try {
          // Set tenant info (which will automatically trigger project fetch)
          setTenantInfo({
            tenantId: cookieTenantId,
            tenantName: cookieTenantName || '', // Get tenantName from cookie
            userRole: 'member', // Default role, will be updated from projects API
            tier: (cookieTier as 'free' | 'enterprise') || 'free', // Get tier from cookie
          });
        } catch (error) {
          console.error('[Layout] Failed to initialize tenant context:', error);
        }
      }

      setCheckingCredentials(false);
    };

    initializeAuth();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (checkingCredentials) {
    return <LoaderWithMessage loadingMessage={displayMessage.current} />;
  }

  if (pathname === ROUTES.LOGIN.path) {
    return <Login />;
  }

  const navbarWidth = opened ? 255 : 95;

  return (
    <AppShell
      header={shouldShowHeader ? HEADER_CONFIG : undefined}
      navbar={{
        width: navbarWidth,
        breakpoint: "sm",
        collapsed: { mobile: !opened },
      }}
      padding="md"
      styles={{
        navbar: {
          height: '100vh',
          top: 0,
          zIndex: 100,
        },
        header: shouldShowHeader ? {
          left: navbarWidth,
          width: `calc(100% - ${navbarWidth}px)`,
          zIndex: 100,
        } : undefined,
      }}
    >
      {shouldShowHeader && <Header toggle={toggle} opened={opened} />}
      <Navbar toggle={toggle} opened={opened} />
      <Main >
        <ProjectGuard>
          {children}
        </ProjectGuard>
      </Main>
    </AppShell>
  );
}
