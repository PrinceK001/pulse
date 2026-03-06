import { useState, useEffect } from 'react';
import { Stack, Text, Table, Button, Group, Modal, TextInput, Select, Badge, Loader, ActionIcon, Box } from '@mantine/core';
import { IconUserPlus, IconTrash, IconCheck, IconX, IconEdit, IconUsers } from '@tabler/icons-react';
import { makeRequest } from '../../../helpers/makeRequest';
import { API_BASE_URL, COOKIES_KEY } from '../../../constants';
import { usePermissions } from '../../../hooks';
import { useProjectContext } from '../../../contexts';
import { showNotification } from '../../../helpers/showNotification';
import { getCookies } from '../../../helpers/cookies';
import { ConfirmationModal } from '../../../components/ConfirmationModal';
import classes from './CollaboratorManagement.module.css';
import { PROJECT_ROLES, ProjectRole } from '../../../constants/Roles';

interface Collaborator {
  userId: string;
  email: string;
  name: string;
  role: ProjectRole;
  status: string;
  lastLoginAt?: string;
}

interface MemberListResponse {
  members: Collaborator[];
  totalCount: number;
}

export function CollaboratorManagement() {
  const [collaborators, setCollaborators] = useState<Collaborator[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<ProjectRole>(PROJECT_ROLES.VIEWER);
  const [inviting, setInviting] = useState(false);
  const [removingUserId, setRemovingUserId] = useState<string | null>(null);
  const [removeConfirmUser, setRemoveConfirmUser] = useState<{ userId: string; userName: string } | null>(null);
  const [editingRoleUserId, setEditingRoleUserId] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<string>('');
  
  const { projectId } = useProjectContext();
  const { canInviteProjectMembers, canRemoveProjectMembers, projectRole } = usePermissions();
  
  // Get current user ID to prevent self-role changes
  const currentUserId = getCookies(COOKIES_KEY.USER_ID);

  useEffect(() => {
    fetchCollaborators();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchCollaborators = async () => {
    if (!projectId) return;
    
    setLoading(true);
    try {
      const response = await makeRequest<MemberListResponse>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/members`,
        init: { method: 'GET' },
      });
      
      if (response.data) {
        setCollaborators(response.data.members);
      } else {
        showNotification('Error', response.error?.message || 'Failed to load members', <IconUserPlus />, '#fa5252');
      }
    } catch (error) {
      console.error('[CollaboratorManagement] Error fetching members:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleInvite = async () => {
    if (!inviteEmail.trim() || !projectId) return;
    
    setInviting(true);
    try {
      const response = await makeRequest<Collaborator>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/members`,
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            email: inviteEmail.trim(),
            role: inviteRole,
          }),
        },
      });
      
      if (response.data) {
        showNotification('Success', `Invitation sent to ${inviteEmail}`, <IconUserPlus />, '#0ec9c2');
        setInviteModalOpen(false);
        setInviteEmail('');
        setInviteRole(PROJECT_ROLES.VIEWER);
        fetchCollaborators();
      } else {
        showNotification('Error', response.error?.message || 'Failed to invite member', <IconUserPlus />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to invite member', <IconUserPlus />, '#fa5252');
    } finally {
      setInviting(false);
    }
  };

  const handleRemove = async (userId: string, userName: string) => {
    setRemoveConfirmUser({ userId, userName });
  };

  const confirmRemove = async () => {
    if (!removeConfirmUser || !projectId) return;
    
    const { userId, userName } = removeConfirmUser;
    setRemovingUserId(userId);
    setRemoveConfirmUser(null);
    
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/members/${userId}`,
        init: { method: 'DELETE' },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification('Success', `${userName} removed from project`, <IconTrash />, '#0ec9c2');
        fetchCollaborators();
      } else {
        showNotification('Error', response.error?.message || 'Failed to remove member', <IconTrash />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to remove member', <IconTrash />, '#fa5252');
    } finally {
      setRemovingUserId(null);
    }
  };

  const handleRoleUpdate = async (userId: string, currentRole: string) => {
    if (!projectId || !newRole || newRole === currentRole) {
      setEditingRoleUserId(null);
      return;
    }
    
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/members/${userId}`,
        init: {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ newRole }),
        },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification('Success', 'Role updated successfully', <IconUserPlus />, '#0ec9c2');
        setEditingRoleUserId(null);
        fetchCollaborators();
      } else {
        showNotification('Error', response.error?.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
    }
  };

  if (loading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Team Members</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconUsers size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Members</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ textAlign: 'center' }}>
            <Loader size="lg" />
            <Text c="dimmed" mt="md">Loading team members...</Text>
          </Box>
        </Box>
      </Box>
    );
  }

  return (
    <Box className={classes.pageContainer}>
      {/* Page Header */}
      <Box className={classes.pageHeader}>
        <Box className={classes.headerGroup}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Manage Team </Text>
          </Box>
          {canInviteProjectMembers && (
            <Button
              leftSection={<IconUserPlus size={16} />}
              onClick={() => setInviteModalOpen(true)}
              variant="filled"
              color="teal"
            >
              Invite Member
            </Button>
          )}
        </Box>
      </Box>

      {/* Members Table */}
      <Box className={classes.contentTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconUsers size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Team Members</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {collaborators.length} member{collaborators.length !== 1 ? 's' : ''}
            </Badge>
          </Box>
        </Box>
        
        {collaborators.length > 0 ? (
          <Box className={classes.tableWrapper}>
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
            {collaborators.map((collab) => {
              const isCurrentUser = collab.userId === currentUserId;
              return (
                <Table.Tr 
                  key={collab.userId}
                  style={{ backgroundColor: isCurrentUser ? '#f8f9fa' : undefined }}
                >
                  <Table.Td>
                    {collab.name}
                    {isCurrentUser && <Text span c="dimmed" size="xs" ml="xs">(You)</Text>}
                  </Table.Td>
                  <Table.Td>{collab.email}</Table.Td>
                  <Table.Td>
                    {editingRoleUserId === collab.userId && projectRole === PROJECT_ROLES.ADMIN ? (
                      <Group gap="xs">
                        <Select
                          size="xs"
                          value={newRole}
                          onChange={(val) => setNewRole(val || collab.role)}
                          data={[
                            { value: PROJECT_ROLES.ADMIN, label: 'Admin' },
                            { value: PROJECT_ROLES.EDITOR, label: 'Editor' },
                            { value: PROJECT_ROLES.VIEWER, label: 'Viewer' },
                          ]}
                          style={{ width: 120 }}
                          disabled={isCurrentUser}
                        />
                        <ActionIcon 
                          size="sm" 
                          color="teal" 
                          onClick={() => handleRoleUpdate(collab.userId, collab.role)}
                          disabled={isCurrentUser}
                        >
                          <IconCheck size={14} />
                        </ActionIcon>
                        <ActionIcon size="sm" color="gray" onClick={() => setEditingRoleUserId(null)}>
                          <IconX size={14} />
                        </ActionIcon>
                      </Group>
                    ) : (
                      <Group gap="xs">
                        <Badge title={isCurrentUser ? "You cannot change your own role" : undefined}>
                          {collab.role}
                        </Badge>
                        {projectRole === PROJECT_ROLES.ADMIN && !isCurrentUser && (
                          <ActionIcon
                            size="xs"
                            variant="subtle"
                            onClick={() => {
                              setEditingRoleUserId(collab.userId);
                              setNewRole(collab.role);
                            }}
                          >
                            <IconEdit size={12} />
                          </ActionIcon>
                        )}
                      </Group>
                    )}
                  </Table.Td>
                  <Table.Td>
                    {canRemoveProjectMembers && !isCurrentUser && (
                      <Button
                        size="xs"
                        variant="subtle"
                        color="red"
                        leftSection={<IconTrash size={14} />}
                        onClick={() => handleRemove(collab.userId, collab.name)}
                        loading={removingUserId === collab.userId}
                        disabled={removingUserId === collab.userId}
                      >
                        Remove
                      </Button>
                    )}
                  </Table.Td>
                </Table.Tr>
              );
            })}
          </Table.Tbody>
        </Table>
          </Box>
        ) : (
          <Box className={classes.emptyState}>
            <Text c="dimmed" ta="center" py="xl">
              No collaborators yet. Invite team members to get started.
            </Text>
          </Box>
        )}
      </Box>

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
              { value: PROJECT_ROLES.ADMIN, label: 'Admin' },
              { value: PROJECT_ROLES.EDITOR, label: 'Editor' },
              { value: PROJECT_ROLES.VIEWER, label: 'Viewer' },
            ]}
            value={inviteRole}
            onChange={(val) => setInviteRole((val as ProjectRole) || PROJECT_ROLES.VIEWER)}
          />
          <Button 
            onClick={handleInvite} 
            disabled={!inviteEmail.trim() || inviting}
            loading={inviting}
          >
            Send Invite
          </Button>
        </Stack>
      </Modal>

      <ConfirmationModal
        opened={removeConfirmUser !== null}
        onClose={() => setRemoveConfirmUser(null)}
        onConfirm={confirmRemove}
        title="Remove Member?"
        message={`Are you sure you want to remove ${removeConfirmUser?.userName} from this project? They will lose access immediately.`}
        confirmLabel="Yes, Remove"
        cancelLabel="Cancel"
        confirmColor="red"
        loading={removingUserId !== null}
        severity="warning"
      />
    </Box>
  );
}
