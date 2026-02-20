import { useState, useEffect } from 'react';
import { Container, Title, Table, Button, Group, Text, Modal, TextInput, Select, Badge, ActionIcon, Loader } from '@mantine/core';
import { IconUserPlus, IconTrash } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions } from '../../hooks';
import { showNotification } from '../../helpers/showNotification';
import { API_BASE_URL } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';

interface Member {
  userId: string;
  email: string;
  name: string;
  role: string;
  status: string;
  lastLoginAt?: string;
}

interface MemberListResponse {
  members: Member[];
  totalCount: number;
}

export function OrganizationMembers() {
  const { tenantId } = useTenantContext();
  const { canInviteTenantMembers, canRemoveTenantMembers } = usePermissions();
  
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<string>('member');
  const [inviting, setInviting] = useState(false);

  useEffect(() => {
    if (tenantId) {
      fetchMembers();
    }
  }, [tenantId]);

  const fetchMembers = async () => {
    if (!tenantId) return;
    
    setLoading(true);
    try {
      const response = await makeRequest<MemberListResponse>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members`,
        init: {
          method: 'GET',
        },
      });
      
      if (response.data) {
        setMembers(response.data.members);
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to load members',
          <IconUserPlus />,
          '#fa5252'
        );
      }
    } catch (error) {
      console.error('[OrganizationMembers] Error fetching members:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleInviteMember = async () => {
    if (!inviteEmail.trim() || !tenantId) return;
    
    setInviting(true);
    try {
      const response = await makeRequest<Member>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members`,
        init: {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            email: inviteEmail.trim(),
            role: inviteRole,
          }),
        },
      });
      
      if (response.data) {
        showNotification(
          'Success',
          `Invitation sent to ${inviteEmail}`,
          <IconUserPlus />,
          '#0ec9c2'
        );
        setInviteModalOpen(false);
        setInviteEmail('');
        setInviteRole('member');
        // Refresh members list
        fetchMembers();
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to invite member',
          <IconUserPlus />,
          '#fa5252'
        );
      }
    } catch (error: any) {
      showNotification(
        'Error',
        error.message || 'Failed to invite member',
        <IconUserPlus />,
        '#fa5252'
      );
    } finally {
      setInviting(false);
    }
  };

  const handleRemoveMember = async (userId: string, userName: string) => {
    if (!window.confirm(`Are you sure you want to remove ${userName} from the organization?`)) {
      return;
    }
    
    if (!tenantId) return;
    
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members/${userId}`,
        init: {
          method: 'DELETE',
        },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification(
          'Success',
          `${userName} removed from organization`,
          <IconTrash />,
          '#0ec9c2'
        );
        // Refresh members list
        fetchMembers();
      } else {
        showNotification(
          'Error',
          response.error?.message || 'Failed to remove member',
          <IconTrash />,
          '#fa5252'
        );
      }
    } catch (error: any) {
      showNotification(
        'Error',
        error.message || 'Failed to remove member',
        <IconTrash />,
        '#fa5252'
      );
    }
  };

  const getRoleBadgeColor = (role: string) => {
    switch (role.toLowerCase()) {
      case 'owner':
        return 'grape';
      case 'admin':
        return 'blue';
      case 'member':
        return 'gray';
      default:
        return 'gray';
    }
  };

  if (loading) {
    return (
      <Container size="xl">
        <Loader size="lg" />
        <Text mt="md">Loading members...</Text>
      </Container>
    );
  }

  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <div>
          <Title order={1}>Team Members</Title>
          <Text c="dimmed" size="sm">
            Manage your organization's team members
          </Text>
        </div>
        {canInviteTenantMembers && (
          <Button
            leftSection={<IconUserPlus size={16} />}
            onClick={() => setInviteModalOpen(true)}
            variant="gradient"
            gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
          >
            Invite Member
          </Button>
        )}
      </Group>
      
      {members.length === 0 ? (
        <Text ta="center" c="dimmed" py="xl">
          No members yet. {canInviteTenantMembers && 'Invite your first team member to get started.'}
        </Text>
      ) : (
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th>Email</Table.Th>
              <Table.Th>Role</Table.Th>
              <Table.Th>Status</Table.Th>
              <Table.Th>Last Login</Table.Th>
              {canRemoveTenantMembers && <Table.Th>Actions</Table.Th>}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {members.map((member) => (
              <Table.Tr key={member.userId}>
                <Table.Td>{member.name}</Table.Td>
                <Table.Td>{member.email}</Table.Td>
                <Table.Td>
                  <Badge color={getRoleBadgeColor(member.role)} variant="light">
                    {member.role}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  <Badge color={member.status === 'active' ? 'teal' : 'gray'} variant="light">
                    {member.status}
                  </Badge>
                </Table.Td>
                <Table.Td>
                  {member.lastLoginAt 
                    ? new Date(member.lastLoginAt).toLocaleDateString()
                    : 'Never'}
                </Table.Td>
                {canRemoveTenantMembers && (
                  <Table.Td>
                    {member.role !== 'owner' && (
                      <ActionIcon
                        color="red"
                        variant="subtle"
                        onClick={() => handleRemoveMember(member.userId, member.name)}
                      >
                        <IconTrash size={16} />
                      </ActionIcon>
                    )}
                  </Table.Td>
                )}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}

      {/* Invite Member Modal */}
      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
        size="md"
      >
        <TextInput
          label="Email Address"
          placeholder="member@example.com"
          value={inviteEmail}
          onChange={(e) => setInviteEmail(e.target.value)}
          required
          mb="md"
        />
        <Select
          label="Role"
          value={inviteRole}
          onChange={(value) => setInviteRole(value || 'member')}
          data={[
            { value: 'member', label: 'Member - Can view and access projects' },
            { value: 'admin', label: 'Admin - Can manage members and projects' },
          ]}
          required
          mb="lg"
        />
        <Group justify="flex-end">
          <Button
            variant="subtle"
            color="gray"
            onClick={() => setInviteModalOpen(false)}
            disabled={inviting}
          >
            Cancel
          </Button>
          <Button
            onClick={handleInviteMember}
            loading={inviting}
            disabled={inviting || !inviteEmail.trim()}
            variant="gradient"
            gradient={{ from: '#0ec9c2', to: '#0ba09a' }}
          >
            Send Invitation
          </Button>
        </Group>
      </Modal>
    </Container>
  );
}
