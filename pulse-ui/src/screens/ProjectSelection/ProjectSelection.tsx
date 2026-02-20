import { useEffect, useState, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Title, Text, Card, Group, Loader, Stack, Button } from '@mantine/core';
import { IconFolder, IconPlus } from '@tabler/icons-react';
import { useTenantContext, useProjectContext } from '../../contexts';
import classes from './ProjectSelection.module.css';

export function ProjectSelection() {
  const navigate = useNavigate();
  const { projects, isLoading, refreshProjects } = useTenantContext();
  const { projectId, switchProject } = useProjectContext();
  const [error, setError] = useState<string | null>(null);
  const [hasFetched, setHasFetched] = useState(false);

  const handleProjectSelect = useCallback(async (projectId: string) => {
    try {
      await switchProject(projectId);
    } catch (err) {
      setError('Failed to switch to project');
      console.error(err);
    }
  }, [switchProject]);

  // FIRST: Always fetch projects when landing on this page
  useEffect(() => {
    console.log('[ProjectSelection] Component mounted - fetching fresh projects');
    const fetchProjects = async () => {
      await refreshProjects();
      setHasFetched(true);
    };
    fetchProjects();
  }, []); // Include refreshProjects in dependencies

  // SECOND: Handle auto-selection after projects are loaded
  useEffect(() => {
    // Wait until we've fetched projects at least once
    if (!hasFetched || isLoading) {
      return;
    }

    // If user already has a project context set, redirect to that project
    // This prevents unnecessary showing of project selection when user is already in a project
    if (projectId) {
      console.log('[ProjectSelection] Project context exists, redirecting to:', projectId);
      navigate(`/projects/${projectId}`, { replace: true });
      return;
    }

    // On first load after login, if user has no project selected but has projects available,
    // auto-select the first project (or last used project from sessionStorage)
    if (!projectId && projects.length > 0) {
      console.log('[ProjectSelection] No project selected, auto-selecting first project');
      const lastUsedProjectId = sessionStorage.getItem('pulse_last_project_id');
      const projectToSelect = projects.find(p => p.projectId === lastUsedProjectId) || projects[0];
      handleProjectSelect(projectToSelect.projectId);
    }
  }, [projectId, isLoading, projects, navigate, handleProjectSelect, hasFetched]);

  const handleCreateProject = () => {
    navigate('/organization/projects/new');
  };

  if (isLoading) {
    return (
      <Container className={classes.container}>
        <Loader size="lg" />
        <Text mt="md">Loading your projects...</Text>
      </Container>
    );
  }

  if (error) {
    return (
      <Container className={classes.container}>
        <Text c="red">{error}</Text>
      </Container>
    );
  }

  return (
    <Container className={classes.container}>
      <Stack gap="xl">
        <div className={classes.header}>
          <Title order={1}>Select a Project</Title>
          <Text c="dimmed">Choose a project to continue</Text>
        </div>

        <div className={classes.projectGrid}>
          {projects.map((project) => (
            <Card
              key={project.projectId}
              className={classes.projectCard}
              shadow="sm"
              padding="lg"
              radius="md"
              withBorder
              onClick={() => handleProjectSelect(project.projectId)}
            >
              <Group justify="space-between">
                <Group>
                  <IconFolder size={32} />
                  <div>
                    <Text fw={500}>{project.name}</Text>
                    {project.description && (
                      <Text size="sm" c="dimmed">{project.description}</Text>
                    )}
                    <Text size="xs" c="dimmed" mt={4}>
                      Role: {project.role}
                    </Text>
                  </div>
                </Group>
                {!project.isActive && (
                  <Text size="xs" c="red">Inactive</Text>
                )}
              </Group>
            </Card>
          ))}
        </div>

        {projects.length === 0 ? (
          <Stack align="center" gap="md">
            <Text ta="center" c="dimmed">
              No projects found. Create your first project to get started.
            </Text>
            <Button
              leftSection={<IconPlus size={18} />}
              onClick={handleCreateProject}
              variant="gradient"
              gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
            >
              Create Project
            </Button>
          </Stack>
        ) : (
          <Button
            leftSection={<IconPlus size={18} />}
            onClick={handleCreateProject}
            variant="light"
            color="teal"
            style={{ alignSelf: 'center' }}
          >
            Create New Project
          </Button>
        )}
      </Stack>
    </Container>
  );
}
