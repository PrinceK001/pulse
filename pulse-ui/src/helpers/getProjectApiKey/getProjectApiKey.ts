import { makeRequest } from '../makeRequest';
import { API_BASE_URL } from '../../constants';

interface ApiKeyItem {
  id: string;
  key: string;
  createdAt: string;
  isActive: boolean;
}

interface ApiKeysResponse {
  keys: ApiKeyItem[];
}

/**
 * Fetches the active API key for the current project.
 * Falls back to a dummy development key if the API is not implemented yet.
 * 
 * @param projectId - The project ID to generate dummy key if needed
 * @returns The active API key or a dummy key
 */
export const getProjectApiKey = async (projectId: string): Promise<string> => {
  try {
    // X-Project-ID header is automatically added by makeRequest
    const response = await makeRequest<ApiKeysResponse>({
      url: `${API_BASE_URL}/v1/project/api-keys`,
      init: { method: 'GET' },
    });
    
    if (response.data?.keys && response.data.keys.length > 0) {
      // Get the active key
      const activeKey = response.data.keys.find(k => k.isActive);
      if (activeKey?.key) {
        return activeKey.key;
      }
    }
  } catch (error) {
    console.log('[getProjectApiKey] API call failed, using dummy key:', error);
  }
  
  // Fallback: Return dummy key when API doesn't exist yet or returns no keys
  console.log('[getProjectApiKey] No API keys found, using dummy key');
  return `pulse_${projectId}_sk_dummy_development_key`;
};
