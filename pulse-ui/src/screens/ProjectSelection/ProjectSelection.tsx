import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Title, Text, Card, Group, Loader, Stack } from '@mantine/core';
import { IconFolder } from '@tabler/icons-react';
import { getUserProjects, ProjectSummary } from '../../helpers/getUserProjects';
import { setProjectContext } from '../../helpers/projectContext';
import { ROUTES } from '../../constants';
import classes from './ProjectSelection.module.css';

export function ProjectSelection() {
  const navigate = useNavigate();
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchProjects();
  }, []);

  const fetchProjects = async () => {
    setLoading(true);
    const response = await getUserProjects();
    
    if (response.data) {
      setProjects(response.data.projects);
      // Auto-select first project if only one exists
      if (response.data.projects.length === 1) {
        handleProjectSelect(response.data.projects[0]);
      }
    } else {
      setError(response.error?.message || 'Failed to load projects');
    }
    setLoading(false);
  };

  const handleProjectSelect = (project: ProjectSummary) => {
    setProjectContext({
      projectId: project.projectId,
      projectName: project.name,
    });
    navigate(ROUTES.HOME.basePath);
  };

  if (loading) {
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
              onClick={() => handleProjectSelect(project)}
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

        {projects.length === 0 && (
          <Text ta="center" c="dimmed">
            No projects found. Please contact your administrator.
          </Text>
        )}
      </Stack>
    </Container>
  );
}
