import { useState, useEffect, useCallback } from 'react';
import { Box, Table, Button, Group, Text, Modal, TextInput, Select, Badge, ActionIcon, Loader, Stack } from '@mantine/core';
import { IconUserPlus, IconTrash, IconCheck, IconX, IconEdit, IconUsers } from '@tabler/icons-react';
import { useTenantContext } from '../../contexts';
import { usePermissions } from '../../hooks';
import { showNotification } from '../../helpers/showNotification';
import { API_BASE_URL, COOKIES_KEY } from '../../constants';
import { makeRequest } from '../../helpers/makeRequest';
import { getCookies } from '../../helpers/cookies';
import { ConfirmationModal } from '../../components/ConfirmationModal';
import classes from './OrganizationMembers.module.css';

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
  const { canInviteTenantMembers, canRemoveTenantMembers, canUpdateTenantRoles } = usePermissions();
  
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [inviteModalOpen, setInviteModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<string>('member');
  const [inviting, setInviting] = useState(false);
  const [editingRoleUserId, setEditingRoleUserId] = useState<string | null>(null);
  const [newRole, setNewRole] = useState<string>('');
  const [updatingRole, setUpdatingRole] = useState(false);
  const [removeConfirmUser, setRemoveConfirmUser] = useState<{ userId: string; userName: string } | null>(null);
  const [removingUserId, setRemovingUserId] = useState<string | null>(null);
  
  // Get current user ID to prevent self-role changes
  const currentUserId = getCookies(COOKIES_KEY.USER_ID);

  const fetchMembers = useCallback(async () => {
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
  }, [tenantId]);

  useEffect(() => {
    if (tenantId) {
      fetchMembers();
    }
  }, [tenantId, fetchMembers]);

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
    setRemoveConfirmUser({ userId, userName });
  };

  const confirmRemoveMember = async () => {
    if (!removeConfirmUser || !tenantId) return;
    
    const { userId, userName } = removeConfirmUser;
    setRemovingUserId(userId);
    setRemoveConfirmUser(null);
    
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
    } finally {
      setRemovingUserId(null);
    }
  };

  const handleRoleUpdate = async (userId: string, currentRole: string, userName: string) => {
    if (!tenantId || !newRole || newRole === currentRole) {
      setEditingRoleUserId(null);
      return;
    }
    
    setUpdatingRole(true);
    try {
      const response = await makeRequest<void>({
        url: `${API_BASE_URL}/v1/tenants/${tenantId}/members/${userId}`,
        init: {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ newRole }),
        },
      });
      
      if (response.data !== undefined || !response.error) {
        showNotification('Success', `Updated ${userName}'s role to ${newRole}`, <IconUserPlus />, '#0ec9c2');
        setEditingRoleUserId(null);
        fetchMembers();
      } else {
        showNotification('Error', response.error?.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
      }
    } catch (error: any) {
      showNotification('Error', error.message || 'Failed to update role', <IconUserPlus />, '#fa5252');
    } finally {
      setUpdatingRole(false);
    }
  };

  const getRoleBadgeColor = (role: string) => {
    switch (role.toLowerCase()) {
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
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>Team Members</Text>
            <Text className={classes.pageSubtitle}>Manage your organization's team members</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconUsers size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Organization Members</Text>
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
            <Text className={classes.pageTitle}>Team Members</Text>
            <Text className={classes.pageSubtitle}>Manage your organization's team members</Text>
          </Box>
          {canInviteTenantMembers && (
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
            <Text className={classes.tableHeaderTitle}>Organization Members</Text>
            <Badge size="sm" variant="light" color="teal" ml="auto">
              {members.length} member{members.length !== 1 ? 's' : ''}
            </Badge>
          </Box>
        </Box>
        
        {members.length > 0 ? (
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
                {members.map((member) => {
                  const isCurrentUser = member.userId === currentUserId;
                  return (
                    <Table.Tr 
                      key={member.userId}
                      style={{ backgroundColor: isCurrentUser ? '#f8f9fa' : undefined }}
                    >
                      <Table.Td>
                        {member.name}
                        {isCurrentUser && <Text span c="dimmed" size="xs" ml="xs">(You)</Text>}
                      </Table.Td>
                      <Table.Td>{member.email}</Table.Td>
                      <Table.Td>
                        {editingRoleUserId === member.userId && canUpdateTenantRoles ? (
                          <Group gap="xs">
                            <Select
                              size="xs"
                              value={newRole}
                              onChange={(val) => setNewRole(val || member.role)}
                              data={[
                                { value: 'admin', label: 'Admin' },
                                { value: 'member', label: 'Member' },
                              ]}
                              style={{ width: 120 }}
                              disabled={updatingRole || isCurrentUser}
                            />
                            <ActionIcon 
                              size="sm" 
                              color="teal" 
                              onClick={() => handleRoleUpdate(member.userId, member.role, member.name)}
                              loading={updatingRole}
                              disabled={updatingRole || isCurrentUser}
                            >
                              <IconCheck size={14} />
                            </ActionIcon>
                            <ActionIcon 
                              size="sm" 
                              color="gray" 
                              onClick={() => setEditingRoleUserId(null)}
                              disabled={updatingRole}
                            >
                              <IconX size={14} />
                            </ActionIcon>
                          </Group>
                        ) : (
                          <Group gap="xs">
                            <Badge 
                              color={getRoleBadgeColor(member.role)} 
                              variant="light"
                              title={isCurrentUser ? "You cannot change your own role" : undefined}
                            >
                              {member.role}
                            </Badge>
                            {canUpdateTenantRoles && !isCurrentUser && (
                              <ActionIcon
                                size="xs"
                                variant="subtle"
                                onClick={() => {
                                  setEditingRoleUserId(member.userId);
                                  setNewRole(member.role);
                                }}
                              >
                                <IconEdit size={12} />
                              </ActionIcon>
                            )}
                          </Group>
                        )}
                      </Table.Td>
                      <Table.Td>
                        {canRemoveTenantMembers && !isCurrentUser && (
                          <Button
                            size="xs"
                            variant="subtle"
                            color="red"
                            leftSection={<IconTrash size={14} />}
                            onClick={() => handleRemoveMember(member.userId, member.name)}
                            loading={removingUserId === member.userId}
                            disabled={removingUserId === member.userId}
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
              No members yet. {canInviteTenantMembers && 'Invite your first team member to get started.'}
            </Text>
          </Box>
        )}

      </Box>

      {/* Invite Member Modal */}
      <Modal
        opened={inviteModalOpen}
        onClose={() => setInviteModalOpen(false)}
        title="Invite Team Member"
      >
        <Stack gap="md">
          <TextInput
            label="Email Address"
            placeholder="member@example.com"
            value={inviteEmail}
            onChange={(e) => setInviteEmail(e.target.value)}
          />
          <Select
            label="Role"
            value={inviteRole}
            onChange={(value) => setInviteRole(value || 'member')}
            data={[
              { value: 'admin', label: 'Admin' },
              { value: 'member', label: 'Member' },
            ]}
          />
          <Button
            onClick={handleInviteMember}
            loading={inviting}
            disabled={inviting || !inviteEmail.trim()}
          >
            Send Invitation
          </Button>
        </Stack>
      </Modal>

      <ConfirmationModal
        opened={removeConfirmUser !== null}
        onClose={() => setRemoveConfirmUser(null)}
        onConfirm={confirmRemoveMember}
        title="Remove Member?"
        message={`Are you sure you want to remove ${removeConfirmUser?.userName} from the organization? They will lose access to all projects immediately.`}
        confirmLabel="Yes, Remove"
        cancelLabel="Cancel"
        confirmColor="red"
        loading={removingUserId !== null}
        severity="warning"
      />
    </Box>
  );
}
