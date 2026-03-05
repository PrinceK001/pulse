import {
  AppShell,
  Tooltip,
  Group,
  Text,
  Box,
  Select,
  Badge,
  Modal,
  Stack,
  Loader,
} from "@mantine/core";
import { flushSync } from "react-dom";
import { useState } from "react";

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
  const { projectId, projectName, setProject } = useProjectContext();
  
  // Modal state for project switching
  const [isSwitchingProject, setIsSwitchingProject] = useState(false);
  const [selectedProjectForSwitch, setSelectedProjectForSwitch] = useState<{
    projectId: string;
    name: string;
    role: string;
    isActive: boolean;
  } | null>(null);

  const handleProjectSwitch = (newProjectId: string | null) => {
    if (!newProjectId || newProjectId === projectId) return;
    
    console.log('[Header] Project switch requested:', newProjectId);
    
    // Find the selected project from the projects list
    const selectedProject = projects.find(p => p.projectId === newProjectId);
    if (!selectedProject) {
      console.error('[Header] Project not found in list:', newProjectId);
      return;
    }
    
    // Store selected project and show modal
    setSelectedProjectForSwitch(selectedProject);
    setIsSwitchingProject(true);
    
    // Update context and sessionStorage immediately while modal is shown
    console.log('[Header] Updating project context and sessionStorage with flushSync');
    flushSync(() => {
      // Update React context
      setProject({
        projectId: selectedProject.projectId,
        projectName: selectedProject.name,
        userRole: selectedProject.role as 'admin' | 'editor' | 'viewer',
        isActive: selectedProject.isActive,
      });
      
      // CRITICAL: Also update sessionStorage immediately
      sessionStorage.setItem('pulse_project_context', JSON.stringify({
        projectId: selectedProject.projectId,
        projectName: selectedProject.name,
        userRole: selectedProject.role,
        isActive: selectedProject.isActive,
        plan: 'free',
        timestamp: Date.now()
      }));
      
      // Update last used project ID
      sessionStorage.setItem('pulse_last_project_id', selectedProject.projectId);
    });
    
    console.log('[Header] Context and sessionStorage updated');
    
    // Auto-navigate after 800ms (gives time for state to settle)
    setTimeout(() => {
      console.log('[Header] Navigating to project:', newProjectId);
      navigate(`/projects/${newProjectId}`);
      setIsSwitchingProject(false);
      setSelectedProjectForSwitch(null);
    }, 800);
  };
  
  const handleCancelSwitch = () => {
    console.log('[Header] Project switch cancelled');
    setIsSwitchingProject(false);
    setSelectedProjectForSwitch(null);
  };

  return (
    <>
      {/* Project Switching Modal */}
      <Modal
        opened={isSwitchingProject}
        onClose={handleCancelSwitch}
        title="Switching Project"
        centered
        size="sm"
        withCloseButton={false}
        closeOnClickOutside={false}
        closeOnEscape={false}
      >
        <Stack align="center" gap="lg" py="md">
          <Loader size="lg" color="teal" />
          <Stack align="center" gap="xs">
            <Text size="lg" fw={500}>Switching to</Text>
            <Text size="xl" fw={600} c="teal">
              {selectedProjectForSwitch?.name}
            </Text>
          </Stack>
          <Text size="sm" c="dimmed" ta="center">
            Setting up project context and loading dashboard...
          </Text>
        </Stack>
      </Modal>

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
    </>
  );
}
