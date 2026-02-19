import { useState, useEffect } from 'react';
import { Stack, Title, Text, Table, Button, Group, Modal, TextInput, Select, Badge, Loader } from '@mantine/core';
import { IconUserPlus, IconTrash } from '@tabler/icons-react';
import { getProjectContext } from '../../../helpers/projectContext';

interface Collaborator {
  userId: string;
  email: string;
  name: string;
  role: string;
}

export function CollaboratorManagement() {
  const [collaborators, setCollaborators] = useState<Collaborator[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('viewer');
  const projectContext = getProjectContext();

  useEffect(() => {
    fetchCollaborators();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchCollaborators = async () => {
    // TODO: Fetch collaborators from API
    console.log('Fetch collaborators');
    setLoading(false);
  };

  const handleInvite = async () => {
    // TODO: Implement invite API
    console.log('Invite:', inviteEmail, inviteRole);
    setInviteModalOpen(false);
    setInviteEmail('');
  };

  const handleRemove = async (userId: string) => {
    // TODO: Implement remove collaborator API
    console.log('Remove:', userId);
  };

  if (loading) {
    return (
      <Stack align="center" gap="md" py="xl">
        <Loader size="lg" />
        <Text c="dimmed">Loading team members...</Text>
      </Stack>
    );
  }

  return (
    <Stack gap="lg">
      <Group justify="space-between">
        <div>
          <Title order={2}>Team Members</Title>
          <Text c="dimmed" size="sm">
            Manage who has access to {projectContext?.projectName}
          </Text>
        </div>
        <Button
          leftSection={<IconUserPlus size={16} />}
          onClick={() => setInviteModalOpen(true)}
        >
          Invite Member
        </Button>
      </Group>

      {collaborators.length > 0 ? (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>Role</Table.Th>
              <Table.Th>Actions</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {collaborators.map((collab) => (
              <Table.Tr key={collab.userId}>
                <Table.Td>{collab.name}</Table.Td>
                <Table.Td>{collab.email}</Table.Td>
                <Table.Td>
                  <Badge>{collab.role}</Badge>
                </Table.Td>
                <Table.Td>
                  <Button
                    size="xs"
                    variant="subtle"
                    color="red"
                    leftSection={<IconTrash size={14} />}
                    onClick={() => handleRemove(collab.userId)}
                  >
                    Remove
                  </Button>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      ) : (
        <Text c="dimmed" ta="center" py="xl">
          No collaborators yet. Invite team members to get started.
        </Text>
      )}

      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
      >
        <Stack gap="md">
          <TextInput
            label="Email"
            placeholder="user@example.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <Select
            label="Role"
            data={[
              { value: 'admin', label: 'Admin' },
              { value: 'editor', label: 'Editor' },
              { value: 'viewer', label: 'Viewer' },
            ]}
            value={inviteRole}
            onChange={(val) => setInviteRole(val || 'viewer')}
          />
          <Button onClick={handleInvite} disabled={!inviteEmail.trim()}>
            Send Invite
          </Button>
        </Stack>
      </Modal>
    </Stack>
  );
}
