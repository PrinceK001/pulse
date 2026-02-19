/**
 * Session Replay Mock Responses
 * 
 * Mock data and response generators for Session Replay API endpoints
 */

import {
  GetSessionsResponse,
  GetSessionDetailResponse,
  GetFilterSchemaResponse,
  GetDateRangeConfigResponse,
  GetQuickFiltersResponse,
} from '../../services/sessionReplay/types';

// Re-export mock data generators from sessionReplay service
// (Keep the existing mock data classes, just reference them here)
export { MockSessionReplayData, MockConfigurationData, MOCK_SESSIONS_DATA } from '../../services/sessionReplay/mockData';

/**
 * Generate mock response for GET /api/v1/session-replay/sessions
 */
export function generateSessionsResponse(
  queryParams: Record<string, any> = {}
): GetSessionsResponse {
  const { MockSessionReplayData, MOCK_SESSIONS_DATA } = require('../../services/sessionReplay/mockData');
  
  let filteredSessions = [...MOCK_SESSIONS_DATA];

  // Apply filters
  filteredSessions = MockSessionReplayData.filterSessions(filteredSessions, {
    environment: queryParams.environment,
    project: queryParams.project,
    hasErrors: queryParams.filters?.hasErrors,
    rageClicks: queryParams.filters?.rageClicks,
    slowSessions: queryParams.filters?.slowSessions,
    mobile: queryParams.filters?.mobile,
    newUsers: queryParams.filters?.newUsers,
    searchQuery: queryParams.searchQuery,
  });

  // Sort sessions (default: most recent first)
  filteredSessions.sort((a, b) => {
    const dateA = new Date(a.startTime).getTime();
    const dateB = new Date(b.startTime).getTime();
    return dateB - dateA;
  });

  // Paginate
  const page = queryParams.page || 1;
  const pageSize = queryParams.pageSize || 10;
  const paginated = MockSessionReplayData.paginateSessions(
    filteredSessions,
    page,
    pageSize
  );

  // Calculate metrics on filtered data
  const metrics = MockSessionReplayData.calculateMetrics(filteredSessions);

  return {
    sessions: paginated.sessions,
    pagination: {
      page,
      pageSize,
      total: paginated.total,
      totalPages: paginated.totalPages,
    },
    metrics,
  };
}

/**
 * Generate mock response for GET /api/v1/session-replay/sessions/:id
 */
export function generateSessionDetailResponse(sessionId: string): GetSessionDetailResponse {
  const { MockSessionReplayData } = require('../../services/sessionReplay/mockData');
  return MockSessionReplayData.generateSessionDetail(sessionId);
}

/**
 * Generate mock response for GET /api/v1/session-replay/filters/schema
 */
export function generateFilterSchemaResponse(
  queryParams: Record<string, any> = {}
): GetFilterSchemaResponse {
  const { MockConfigurationData } = require('../../services/sessionReplay/mockData');
  // For now, default to iOS. In real app, would use projectId to determine platform
  const platform = 'ios'; // Could be derived from queryParams.projectId
  return MockConfigurationData.getFilterSchema(platform as 'web' | 'ios' | 'android');
}

/**
 * Generate mock response for GET /api/v1/session-replay/config/date-ranges
 */
export function generateDateRangeConfigResponse(): GetDateRangeConfigResponse {
  const { MockConfigurationData } = require('../../services/sessionReplay/mockData');
  return MockConfigurationData.getDateRangeConfig();
}

/**
 * Generate mock response for GET /api/v1/session-replay/config/quick-filters
 */
export function generateQuickFiltersResponse(): GetQuickFiltersResponse {
  const { MockConfigurationData } = require('../../services/sessionReplay/mockData');
  return MockConfigurationData.getQuickFilters();
}

/**
 * Generate mock response for POST /api/v1/session-replay/sessions/bulk-tag
 */
export function generateBulkTagResponse(): { success: boolean } {
  return { success: true };
}

/**
 * Generate mock response for DELETE /api/v1/session-replay/sessions/bulk-delete
 */
export function generateBulkDeleteResponse(): { success: boolean } {
  return { success: true };
}

/**
 * Generate mock response for POST /api/v1/session-replay/sessions/export
 */
export function generateExportResponse(): { downloadUrl: string; expiresAt: string } {
  return {
    downloadUrl: 'https://example.com/downloads/sessions.csv',
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  };
}
