import { Container, Title, Grid, Card, Button, Group, Text, Badge } from '@mantine/core';
import { IconPlus, IconFolder } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions } from '../../hooks';
import { useNavigate } from 'react-router-dom';

export function OrganizationProjects() {
  const { projects, isLoading } = useTenantContext();
  const { canCreateProjects } = usePermissions();
  const navigate = useNavigate();

  const handleCreateProject = () => {
    navigate('/organization/projects/new');
  };

  const handleProjectClick = (projectId: string) => {
    navigate(`/projects/${projectId}`);
  };

  if (isLoading) {
    return (
      <Container size="xl">
        <Text>Loading projects...</Text>
      </Container>
    );
  }

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <div>
          <Title order={1}>All Projects</Title>
          <Text c="dimmed" size="sm">
            Manage your projects and create new ones
          </Text>
        </div>
        {canCreateProjects && (
          <Button
            leftSection={<IconPlus size={16} />}
            onClick={handleCreateProject}
            variant="gradient"
            gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
          >
            Create Project
          </Button>
        )}
      </Group>
      
      {projects.length === 0 ? (
        <Card shadow="sm" padding="xl" style={{ textAlign: 'center' }}>
          <Text c="dimmed" mb="md">
            No projects yet. {canCreateProjects && 'Create your first project to get started.'}
          </Text>
          {canCreateProjects && (
            <Button
              leftSection={<IconPlus size={16} />}
              onClick={handleCreateProject}
              variant="light"
              color="teal"
            >
              Create First Project
            </Button>
          )}
        </Card>
      ) : (
        <Grid>
          {projects.map(project => (
            <Grid.Col key={project.projectId} span={4}>
              <Card 
                shadow="sm" 
                padding="lg"
                onClick={() => handleProjectClick(project.projectId)}
                style={{ cursor: 'pointer', height: '100%' }}
                withBorder
              >
                <Group justify="space-between" mb="xs">
                  <IconFolder size={24} style={{ color: '#0ba09a' }} />
                  {project.isActive ? (
                    <Badge color="teal" variant="light" size="sm">Active</Badge>
                  ) : (
                    <Badge color="gray" variant="light" size="sm">Inactive</Badge>
                  )}
                </Group>
                <Text fw={500} size="lg" mb="xs">{project.name}</Text>
                <Text size="sm" c="dimmed" lineClamp={2} mb="sm">
                  {project.description || 'No description'}
                </Text>
                <Group justify="space-between" mt="auto">
                  <Text size="xs" c="dimmed">Your Role: <strong>{project.role}</strong></Text>
                </Group>
              </Card>
            </Grid.Col>
          ))}
        </Grid>
      )}
    </Container>
  );
}
