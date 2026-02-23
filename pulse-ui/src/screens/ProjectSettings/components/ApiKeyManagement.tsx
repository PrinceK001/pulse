import { useState, useEffect } from 'react';
import { Stack, Title, Text, Card, Group, Button, Code, CopyButton, ActionIcon, Tooltip, Loader, Alert } from '@mantine/core';
import { IconCopy, IconCheck, IconRefresh, IconPlus, IconInfoCircle } from '@tabler/icons-react';
import { notifications } from '@mantine/notifications';
import { useProjectContext } from '../../../contexts';
import { getProjectApiKey } from '../../../helpers/getProjectApiKey';
import { makeRequest } from '../../../helpers/makeRequest';
import { API_BASE_URL } from '../../../constants';

export function ApiKeyManagement() {
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [generating, setGenerating] = useState(false);
  const [isDummyKey, setIsDummyKey] = useState(false);
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
      // Call regenerate endpoint (works for both generate and regenerate)
      // X-Project-ID header is automatically included by makeRequest
      const response = await makeRequest({
        url: `${API_BASE_URL}/v1/project/api-keys/regenerate`,
        init: { method: 'POST' },
      });
      
      // Handle 404 - API not implemented yet
      if (response.status === 404) {
        console.log('[ApiKeyManagement] API endpoint not implemented (404)');
        notifications.show({
          title: 'API Not Ready',
          message: 'The API key management endpoint is being developed. Using development key for now.',
          color: 'blue',
          icon: <IconInfoCircle size={18} />,
        });
        
        // Generate a new dummy key with timestamp to simulate regeneration
        const timestamp = Date.now();
        const newDummyKey = `pulse_${projectId}_sk_dummy_${timestamp}`;
        setApiKey(newDummyKey);
        setIsDummyKey(true);
        setGenerating(false);
        return;
      }
      
      // Handle successful response
      if (response.data) {
        notifications.show({
          title: 'Success',
          message: isRegenerate ? 'API key regenerated successfully' : 'API key generated successfully',
          color: 'green',
          icon: <IconCheck size={18} />,
        });
        
        // Refresh the key
        await fetchApiKey();
      } else {
        console.error('[ApiKeyManagement] Failed to generate/regenerate key:', response.error);
        notifications.show({
          title: 'Error',
          message: response.error?.message || 'Failed to generate API key. Please try again.',
          color: 'red',
        });
      }
    } catch (error) {
      console.error('[ApiKeyManagement] Error during generate/regenerate:', error);
      notifications.show({
        title: 'Error',
        message: 'An unexpected error occurred. Please try again.',
        color: 'red',
      });
    } finally {
      setGenerating(false);
    }
  };

  // Show error if no project ID
  if (!projectId) {
    return (
      <Stack gap="lg">
        <Alert color="red" title="No Project Selected" icon={<IconInfoCircle />}>
          Please select a project to manage API keys.
        </Alert>
      </Stack>
    );
  }

  // Show loading state
  if (loading) {
    return (
      <Stack align="center" gap="md" py="xl">
        <Loader size="lg" />
        <Text c="dimmed">Loading API key...</Text>
      </Stack>
    );
  }

  return (
    <Stack gap="lg">
      <div>
        <Title order={2}>API Key Management</Title>
        <Text c="dimmed" size="sm">
          Use this API key to authenticate SDK and API requests for {projectName}
        </Text>
      </div>

      {/* Show info banner when using dummy key */}
      {isDummyKey && (
        <Alert color="blue" title="Development Mode" icon={<IconInfoCircle />}>
          Using a development API key. The API key management endpoint is being developed by your team.
          This key can be used for local testing.
        </Alert>
      )}

      <Card shadow="sm" padding="lg" radius="md" withBorder>
        <Stack gap="md">
          <Group justify="space-between">
            <Text fw={500}>Project API Key</Text>
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
                color="blue"
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
                onClick={() => handleGenerateOrRegenerateKey(true)}
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
      </Card>
    </Stack>
  );
}
