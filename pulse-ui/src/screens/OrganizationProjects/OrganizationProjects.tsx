import { Container, Title, Grid, Card, Button, Group, Text } from '@mantine/core';
import { IconPlus } from '@tabler/icons-react';
import { getUserProjects, ProjectSummary } from '../../helpers/getUserProjects';
import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

export function OrganizationProjects() {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const navigate = useNavigate();

  useEffect(() => {
    getUserProjects().then(response => {
      if (response.data) {
        setProjects(response.data.projects);
      }
    });
  }, []);

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <Title order={1}>All Projects</Title>
        <Button leftSection={<IconPlus size={16} />}>
          Create Project
        </Button>
      </Group>
      
      <Grid>
        {projects.map(project => (
          <Grid.Col key={project.projectId} span={4}>
            <Card 
              shadow="sm" 
              padding="lg"
              onClick={() => navigate(`/projects/${project.projectId}`)}
              style={{ cursor: 'pointer' }}
            >
              <Text fw={500}>{project.name}</Text>
              <Text size="sm" c="dimmed">{project.description || 'No description'}</Text>
              <Text size="xs" c="dimmed" mt={4}>Role: {project.role}</Text>
            </Card>
          </Grid.Col>
        ))}
      </Grid>
    </Container>
  );
}
