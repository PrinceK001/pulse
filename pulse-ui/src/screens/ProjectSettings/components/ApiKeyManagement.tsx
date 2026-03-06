import { useState, useEffect } from 'react';
import { Stack, Text, Box, Group, Button, Code, CopyButton, ActionIcon, Tooltip, Loader, Alert } from '@mantine/core';
import { IconCopy, IconCheck, IconRefresh, IconPlus, IconInfoCircle, IconKey } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useProjectContext } from '../../../contexts';
import { getProjectApiKey } from '../../../helpers/getProjectApiKey';
import { makeRequest } from '../../../helpers/makeRequest';
import { API_BASE_URL } from '../../../constants';
import { ConfirmationModal } from '../../../components/ConfirmationModal';
import classes from './ApiKeyManagement.module.css';

// Type definitions for API responses
interface ApiKeyRestResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  isActive: boolean;
  expiresAt: string | null;
  gracePeriodEndsAt: string | null;
  createdBy: string;
  createdAt: string;
  deactivatedAt: string | null;
  deactivatedBy: string | null;
  deactivationReason: string | null;
}

interface ApiKeyListResponse {
  apiKeys: ApiKeyRestResponse[];
  count: number;
}

interface CreateApiKeyResponse {
  apiKeyId: number;
  projectId: string;
  displayName: string;
  apiKey: string;
  expiresAt: string | null;
  createdAt: string;
}

export function ApiKeyManagement() {
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [isDummyKey, setIsDummyKey] = useState(false);
  const [confirmRegenerateOpen, setConfirmRegenerateOpen] = useState(false);
  const { projectId, projectName } = useProjectContext();

  useEffect(() => {
    fetchApiKey();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchApiKey = async () => {
    // Handle missing project context
    if (!projectId) {
      console.warn('[ApiKeyManagement] No project ID available');
      setLoading(false);
      return;
    }
    
    setLoading(true);
    try {
      const result = await getProjectApiKey(projectId);
      setApiKey(result.key);
      setIsDummyKey(result.isDummy);
      
      if (result.isDummy) {
        console.log('[ApiKeyManagement] Using dummy development key');
      }
    } catch (error) {
      console.error('[ApiKeyManagement] Failed to fetch API key:', error);
      notifications.show({
        title: 'Error',
        message: 'Failed to fetch API key. Please try again.',
        color: 'red',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleGenerateOrRegenerateKey = async (isRegenerate: boolean) => {
    if (!projectId) {
      notifications.show({
        title: 'Error',
        message: 'Project ID not available',
        color: 'red',
      });
      return;
    }
    
    setGenerating(true);
    
    try {
      let currentDisplayName = 'Default Key';
      
      // Step 1: If regenerating, revoke old key first
      if (isRegenerate && apiKey) {
        console.log('[ApiKeyManagement] Regenerating: fetching current keys');
        
        // Get current active key to find its ID and display name
        const listResponse = await makeRequest<ApiKeyListResponse>({
          url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys`,
          init: { method: 'GET' },
        });
        
        if (listResponse.data?.apiKeys) {
          const activeKey = listResponse.data.apiKeys.find((k) => k.isActive);
          
          if (activeKey) {
            console.log('[ApiKeyManagement] Revoking old key:', activeKey.apiKeyId);
            currentDisplayName = activeKey.displayName || 'Default Key';
            
            // Revoke the old key with 0-day grace period (immediate revocation)
            await makeRequest({
              url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys/${activeKey.apiKeyId}`,
              init: {
                method: 'DELETE',
                body: JSON.stringify({ gracePeriodDays: 0 })
              },
            });
            
            console.log('[ApiKeyManagement] Old key revoked successfully');
          }
        }
      }
      
      // Step 2: Create new key
      console.log('[ApiKeyManagement] Creating new API key');
      const createResponse = await makeRequest<CreateApiKeyResponse>({
        url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys`,
        init: {
          method: 'POST',
          body: JSON.stringify({
            displayName: currentDisplayName,
            expiresAt: null  // null means never expires
          })
        },
      });
      
      // Step 3: Handle response
      if (createResponse.data?.apiKey) {
        setApiKey(createResponse.data.apiKey);
        setIsDummyKey(false);
        
        notifications.show({
          title: 'Success',
          message: isRegenerate 
            ? 'API key regenerated successfully' 
            : 'API key generated successfully',
          color: 'green',
          icon: <IconCheck size={18} />,
        });
      } else {
        throw new Error(createResponse.error?.message || 'Failed to create API key');
      }
      
    } catch (error) {
      console.error('[ApiKeyManagement] Error during generate/regenerate:', error);
      notifications.show({
        title: 'Error',
        message: error instanceof Error ? error.message : 'An unexpected error occurred',
        color: 'red',
      });
    } finally {
      setGenerating(false);
    }
  };

  // Show error if no project ID
  if (!projectId) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
        <Alert color="red" title="No Project Selected" icon={<IconInfoCircle />}>
          Please select a project to manage API keys.
        </Alert>
      </Box>
    );
  }

  // Show loading state
  if (loading) {
    return (
      <Box className={classes.pageContainer}>
        <Box className={classes.pageHeader}>
          <Box className={classes.titleSection}>
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
        <Box className={classes.contentTable}>
          <Box className={classes.tableHeader}>
            <Box className={classes.tableHeaderContent}>
              <IconKey size={18} color="#0ba09a" />
              <Text className={classes.tableHeaderTitle}>Project API Key</Text>
            </Box>
          </Box>
          <Box className={classes.tableWrapper} style={{ textAlign: 'center' }}>
            <Loader size="lg" />
            <Text c="dimmed" mt="md">Loading API key...</Text>
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
            <Text className={classes.pageTitle}>API Key Management</Text>
          </Box>
        </Box>
      </Box>

      {/* Show info banner when using dummy key */}
      {isDummyKey && (
        <Alert color="blue" title="Development Mode" icon={<IconInfoCircle />} mb="lg">
          Using a development API key. The API key management endpoint is being developed by your team.
          This key can be used for local testing.
        </Alert>
      )}

      {/* API Key Content */}
      <Box className={classes.contentTable}>
        <Box className={classes.tableHeader}>
          <Box className={classes.tableHeaderContent}>
            <IconKey size={18} color="#0ba09a" />
            <Text className={classes.tableHeaderTitle}>Project API Key</Text>
          </Box>
        </Box>
        <Box className={classes.tableWrapper}>
          <Stack gap="md">
            <Group justify="space-between">
              <Text fw={500}>API Key</Text>
              {apiKey && (
                <CopyButton value={apiKey}>
                  {({ copied, copy }) => (
                    <Tooltip label={copied ? 'Copied' : 'Copy'}>
                      <ActionIcon color={copied ? 'teal' : 'gray'} onClick={copy}>
                        {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                      </ActionIcon>
                    </Tooltip>
                  )}
                </CopyButton>
              )}
            </Group>
            
            {apiKey && (
              <Code block>{apiKey}</Code>
            )}
            
            {!apiKey && (
              <Text c="dimmed" size="sm">No API key found for this project. Generate one to get started.</Text>
            )}
            
            <Group gap="sm">
              {!apiKey ? (
                // Show "Generate Key" button when no key exists
                <Button
                  leftSection={<IconPlus size={16} />}
                  variant="filled"
                  color="teal"
                  onClick={() => handleGenerateOrRegenerateKey(false)}
                  loading={generating}
                >
                  {generating ? 'Generating...' : 'Generate Key'}
                </Button>
              ) : (
                // Show "Regenerate Key" button when key exists
                <Button
                  leftSection={<IconRefresh size={16} />}
                  variant="light"
                  color="orange"
                  onClick={() => setConfirmRegenerateOpen(true)}
                  loading={generating}
                >
                  {generating ? 'Regenerating...' : 'Regenerate Key'}
                </Button>
              )}
            </Group>
            
            {apiKey && (
              <Text size="xs" c="dimmed">
                Warning: Regenerating the key will invalidate the old key immediately.
              </Text>
            )}
          </Stack>
        </Box>
      </Box>

      <ConfirmationModal
        opened={confirmRegenerateOpen}
        onClose={() => setConfirmRegenerateOpen(false)}
        onConfirm={async () => {
          await handleGenerateOrRegenerateKey(true);
          setConfirmRegenerateOpen(false);
        }}
        title="Regenerate API Key?"
        message="This will immediately invalidate your current API key. All applications using the old key will stop working. Are you sure you want to continue?"
        confirmLabel="Yes, Regenerate"
        cancelLabel="Cancel"
        confirmColor="orange"
        loading={generating}
        severity="danger"
      />
    </Box>
  );
}
