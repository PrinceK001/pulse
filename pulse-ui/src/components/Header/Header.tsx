import {
  AppShell,
  Tooltip,
  Group,
  Text,
  Box,
  Select,
  Badge,
} from "@mantine/core";

import classes from "./Header.module.css";
import { HeaderProps } from "./Header.interface";
import { useNavigate } from "react-router-dom";
import {
  TOOLTIP_LABLES,
} from "../../constants";
import {
  IconCircleChevronLeft,
  IconCircleChevronRight,
  IconFolder,
} from "@tabler/icons-react";
import { useTenantContext, useProjectContext } from "../../contexts";

export function Header({ toggle: toogle, opened }: HeaderProps) {
  const navigate = useNavigate();
  const { projects, tier } = useTenantContext();
  const { projectId, projectName, switchProject } = useProjectContext();

  const handleProjectSwitch = async (newProjectId: string | null) => {
    if (!newProjectId || newProjectId === projectId) return;
    await switchProject(newProjectId);
  };

  return (
    <AppShell.Header>
      <Box className={classes.headerContainer}>
        {/* Navbar Toggle */}
        <Box className={classes.leftSection}>
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
        </Box>
        
        {/* Project Display Section */}
        <Box className={classes.projectSection}>
          {projectId && projects.length <= 1 ? (
            // Single project display
            tier === 'free' ? (
              // FREE tier: Show project name with upgrade badge
              <Group gap="xs" className={classes.projectInfo}>
                <IconFolder size={18} style={{ color: '#0ba09a' }} />
                <Text className={classes.projectName}>{projectName}</Text>
                <Badge 
                  variant="light" 
                  color="teal"
                  size="sm"
                  className={classes.upgradeBadge}
                  onClick={() => navigate('/pricing')}
                >
                  Free · Upgrade
                </Badge>
              </Group>
            ) : (
              // ENTERPRISE tier: Show project name only (no upgrade badge)
              <Group gap="xs" className={classes.projectInfo}>
                <IconFolder size={18} style={{ color: '#0ba09a' }} />
                <Text className={classes.projectName}>{projectName}</Text>
              </Group>
            )
          ) : projectId && projects.length > 1 ? (
            // Multiple projects - show selector (same for both tiers)
            <Select
              leftSection={<IconFolder size={18} />}
              placeholder="Select project"
              data={projects.map(p => ({
                value: p.projectId,
                label: p.name,
              }))}
              value={projectId}
              onChange={handleProjectSwitch}
              className={classes.projectDropdown}
              comboboxProps={{ withinPortal: true }}
            />
          ) : null}
        </Box>
      </Box>
    </AppShell.Header>
  );
}
