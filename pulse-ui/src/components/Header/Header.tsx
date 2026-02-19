import {
  AppShell,
  Tooltip,
  Group,
  Text,
  Image,
  Box,
  Popover,
  Avatar,
  Button,
  Stack,
  Select,
} from "@mantine/core";

import classes from "./Header.module.css";
import { HeaderProps } from "./Header.interface";
import { useNavigate } from "react-router-dom";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  HEADER_CONSTANTS,
  ROUTES,
  TOOLTIP_LABLES,
} from "../../constants";
import {
  IconCircleChevronLeft,
  IconCircleChevronRight,
  IconLogout,
  IconUserScan,
  IconFolder,
} from "@tabler/icons-react";
import Cookies from "js-cookie";
import { useRef, useEffect, useState } from "react";
import { googleLogout } from "@react-oauth/google";
import { getCookies, removeAllCookies } from "../../helpers/cookies";
import {
  signOutFirebase,
  isGcpMultiTenantEnabled,
} from "../../helpers/gcpAuth";
import { MULTI_TENANT_CONSTANTS } from "../../constants";
import { getProjectContext, setProjectContext } from "../../helpers/projectContext";
import { getUserProjects, ProjectSummary } from "../../helpers/getUserProjects";

export function Header({ toggle: toogle, opened }: HeaderProps) {
  const navigate = useNavigate();
  const userProfilePicture = useRef<string>(
    Cookies.get(COOKIES_KEY.USER_PICTURE) ?? "",
  );
  const gcpMultiTenantEnabled = isGcpMultiTenantEnabled();
  const currentTenantId = getCookies(COOKIES_KEY.TENANT_ID);
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const currentProject = getProjectContext();

  useEffect(() => {
    // Fetch projects for dropdown
    getUserProjects().then(response => {
      if (response.data) {
        setProjects(response.data.projects);
      }
    });
  }, []);

  const onClick = () => {
    navigate("/");
  };

  const onLogoutClick = async () => {
    if (gcpMultiTenantEnabled) {
      await signOutFirebase();
    } else {
      googleLogout();
    }
    removeAllCookies();
    navigate(ROUTES.LOGIN.basePath);
  };

  const handleProjectSwitch = (projectId: string | null) => {
    if (!projectId) return;
    
    const selectedProject = projects.find(p => p.projectId === projectId);
    if (selectedProject) {
      setProjectContext({
        projectId: selectedProject.projectId,
        projectName: selectedProject.name,
      });
      // Reload the page to refresh all data with new project context
      window.location.reload();
    }
  };

  return (
    <AppShell.Header>
      <Box className={classes.headerContainer}>
        <Group h="100%" px="md" grow>
          {opened ? (
            <Tooltip label={TOOLTIP_LABLES.CLOSE_NAVBAR}>
              <IconCircleChevronLeft
                onClick={toogle}
                className={classes.cheveronIcon}
              />
            </Tooltip>
          ) : (
            <Tooltip label={TOOLTIP_LABLES.OPEN_NAVBAR}>
              <IconCircleChevronRight
                onClick={toogle}
                className={classes.cheveronIcon}
              />
            </Tooltip>
          )}
          <div className={classes.logoContainer} onClick={onClick}>
            <Image
              src={(process.env.PUBLIC_URL || '') + "/logo.svg"}
              radius="md"
              className={classes.logo}
              alt=""
            />
            <Text fs="lg" fw="bold" ml="sm" className={classes.appName}>
              {COMMON_CONSTANTS.APP_NAME}
            </Text>
          </div>
        </Group>
        
        {/* Project Switcher */}
        {currentProject && projects.length > 1 && (
          <Select
            leftSection={<IconFolder size={18} />}
            placeholder="Select project"
            data={projects.map(p => ({
              value: p.projectId,
              label: p.name,
            }))}
            value={currentProject.projectId}
            onChange={handleProjectSwitch}
            className={classes.projectDropdown}
            comboboxProps={{ withinPortal: true }}
          />
        )}
        
        <Box className={classes.userInformation}>
          <Popover width={200} position="bottom" withArrow shadow="md">
            <Popover.Target>
              <Avatar
                size={"md"}
                radius={"md"}
                src={userProfilePicture.current}
              />
            </Popover.Target>
            <Popover.Dropdown>
              <Stack justify="space-between" gap="sm">
                <Group className={classes.userName}>
                  <IconUserScan size={22} color="#0ba09a" />
                  <Text>{getCookies(COOKIES_KEY.USER_NAME)}</Text>
                </Group>
                {gcpMultiTenantEnabled &&
                  currentTenantId &&
                  currentTenantId !== "undefined" && (
                    <Text size="xs" c="dimmed">
                      {MULTI_TENANT_CONSTANTS.CURRENT_TENANT_LABEL}:{" "}
                      {getCookies(COOKIES_KEY.TENANT_NAME) || currentTenantId}
                    </Text>
                )}
                <Button
                  leftSection={<IconLogout size={18} />}
                  onClick={onLogoutClick}
                  variant="light"
                  color="red"
                  size="sm"
                  fullWidth
                >
                  {HEADER_CONSTANTS.LOGOUT_TEXT}
                </Button>
              </Stack>
            </Popover.Dropdown>
          </Popover>
        </Box>
      </Box>
    </AppShell.Header>
  );
}
