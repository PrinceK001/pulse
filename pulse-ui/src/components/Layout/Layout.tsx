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

export function Layout({ children }: LayoutProps) {
  const navigate = useNavigate();
  const [opened, { toggle }] = useDisclosure(false);
  const { pathname } = useLocation();
  const [checkingCredentials, setCheckingCredentials] = useState(true);
  const displayMessage = useRef<string>(
    LAYOUT_PAGE_CONSTANTS.CHECKING_CREDENTIALS,
  );

  // Check if we're on a project route
  const isProjectRoute = pathname.startsWith('/projects/');

  useEffect(() => {
    const token = getCookies(COOKIES_KEY.ACCESS_TOKEN);
    if (!token || token === "undefined") {
      setCheckingCredentials(false);
      navigate(ROUTES.LOGIN.basePath);
    } else {
      setCheckingCredentials(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (checkingCredentials) {
    return <LoaderWithMessage loadingMessage={displayMessage.current} />;
  }

  if (pathname === ROUTES.LOGIN.path) {
    return <Login />;
  }

  return (
    <AppShell
      header={isProjectRoute ? HEADER_CONFIG : undefined}
      navbar={{
        width: opened ? 255 : 95,
        breakpoint: "sm",
        collapsed: { mobile: !opened },
      }}
      padding="md"
    >
      {isProjectRoute && <Header toggle={toggle} opened={opened} />}
      <Navbar toggle={toggle} opened={opened} />
      <Main>
        <ProjectGuard>
          {children}
        </ProjectGuard>
      </Main>
    </AppShell>
  );
}
