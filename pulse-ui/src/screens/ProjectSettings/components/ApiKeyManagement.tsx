import { useState, useEffect } from 'react';
import { Stack, Title, Text, Card, Group, Button, Code, CopyButton, ActionIcon, Tooltip, Loader } from '@mantine/core';
import { IconCopy, IconCheck, IconRefresh } from '@tabler/icons-react';
import { getProjectContext } from '../../../helpers/projectContext';
import { getProjectApiKey } from '../../../helpers/getProjectApiKey';
import { makeRequest } from '../../../helpers/makeRequest';
import { API_BASE_URL } from '../../../constants';

export function ApiKeyManagement() {
  const [apiKey, setApiKey] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [regenerating, setRegenerating] = useState(false);
  const projectContext = getProjectContext();

  useEffect(() => {
    fetchApiKey();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const fetchApiKey = async () => {
    if (!projectContext) return;
    
    setLoading(true);
    const key = await getProjectApiKey(projectContext.projectId);
    setApiKey(key);
    setLoading(false);
  };

  const handleRegenerateKey = async () => {
    if (!projectContext) return;
    
    setRegenerating(true);
    
    // Call regenerate endpoint (X-Project-ID header automatically included)
    const response = await makeRequest({
      url: `${API_BASE_URL}/v1/project/api-keys/regenerate`,
      init: { method: 'POST' },
    });
    
    if (response.data) {
      // Refresh the key list
      await fetchApiKey();
    } else {
      console.error('[ApiKeyManagement] Failed to regenerate key:', response.error);
    }
    
    setRegenerating(false);
  };

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
          Use this API key to authenticate SDK and API requests for {projectContext?.projectName}
        </Text>
      </div>

      <Card shadow="sm" padding="lg" radius="md" withBorder>
        <Stack gap="md">
          <Group justify="space-between">
            <Text fw={500}>Project API Key</Text>
            <CopyButton value={apiKey || ''}>
              {({ copied, copy }) => (
                <Tooltip label={copied ? 'Copied' : 'Copy'}>
                  <ActionIcon color={copied ? 'teal' : 'gray'} onClick={copy}>
                    {copied ? <IconCheck size={16} /> : <IconCopy size={16} />}
                  </ActionIcon>
                </Tooltip>
              )}
            </CopyButton>
          </Group>
          
          {apiKey && (
            <Code block>{apiKey}</Code>
          )}
          
          {!apiKey && (
            <Text c="dimmed" size="sm">No API key found for this project.</Text>
          )}
          
          <Button
            leftSection={<IconRefresh size={16} />}
            variant="light"
            color="orange"
            onClick={handleRegenerateKey}
            disabled={!apiKey}
            loading={regenerating}
          >
            {regenerating ? 'Regenerating...' : 'Regenerate Key'}
          </Button>
          
          <Text size="xs" c="dimmed">
            Warning: Regenerating the key will invalidate the old key immediately.
          </Text>
        </Stack>
      </Card>
    </Stack>
  );
}
