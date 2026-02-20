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
import { useEffect, useState } from "react";
import { getProjectContext, setProjectContext } from "../../helpers/projectContext";
import { getUserProjects, ProjectSummary } from "../../helpers/getUserProjects";

export function Header({ toggle: toogle, opened }: HeaderProps) {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const currentProject = getProjectContext();
  console.log(currentProject);

  useEffect(() => {
    // Fetch projects for dropdown
    getUserProjects().then(response => {
      if (response.data) {
        setProjects(response.data.projects);
      }
    });
  }, []);
  console.log(projects);

  const handleProjectSwitch = (projectId: string | null) => {
    if (!projectId) return;
    
    const selectedProject = projects.find(p => p.projectId === projectId);
    if (selectedProject) {
      setProjectContext({
        projectId: selectedProject.projectId,
        projectName: selectedProject.name,
      });
      // Navigate to new project's dashboard
      navigate(`/projects/${selectedProject.projectId}`);
    }
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
          {currentProject && projects.length <= 1 ? (
            // Single project - show name with upgrade badge (Free plan)
            <Group gap="xs" className={classes.projectInfo}>
              <IconFolder size={18} style={{ color: '#0ba09a' }} />
              <Text className={classes.projectName}>{currentProject.projectName}</Text>
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
          ) : currentProject && projects.length > 1 ? (
            // Multiple projects - show selector
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
          ) : null}
        </Box>
      </Box>
    </AppShell.Header>
  );
}
