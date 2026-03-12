# Mock Server Status

Last Updated: 2024-03-12

**Coverage**: 55/75 endpoints (73%)

---

## ✅ Fully Implemented (Phase 1 Complete)

| Endpoint | Method | Handler | Notes |
|----------|--------|---------|-------|
| `/v1/auth/login` | POST | `handleAuthEndpoints` | Returns mock JWT, tenant context |
| `/v1/auth/token/refresh` | POST | `handleAuthEndpoints` | Refreshes tokens |
| `/v1/auth/token/verify` | GET | `handleAuthEndpoints` | Validates tokens |
| `/v1/users/me/projects` | GET | `handleV1UserEndpoints` | Returns tenant + projects |
| `/v1/onboarding/complete` | POST | `handleOnboardingEndpoints` | Creates tenant + project |
| `/v1/tnc/status` | GET | `handleTncEndpoints` | Returns accepted: true |
| `/v1/tnc/accept` | POST | `handleTncEndpoints` | Accepts TNC |
| `/v1/tnc/documents` | GET | `handleTncEndpoints` | Returns documents |
| `/v1/tnc/history` | GET | `handleTncEndpoints` | Returns history |
| `/v1/interactions/performance-metric/distribution` | POST | `handleDataQueryEndpoint` | V2 data query generator |
| `/telemetry-filters` | GET | `handleDashboardFiltersEndpoint` | Dashboard filters |
| `/v1/interactions/filter-options` | GET | `handleDashboardFiltersEndpoint` | Filter options |
| `/v1/breadcrumbs` | POST | `handleBreadcrumbsEndpoint` | Breadcrumb events |
| `/query/metadata/table` | GET | `handleRealtimeQueryEndpoints` | Table metadata |
| `/query/tables` | GET | `handleRealtimeQueryEndpoints` | Available tables |
| `/query/history` | GET | `handleRealtimeQueryEndpoints` | Query history |
| `/query/job/*` | GET | `handleRealtimeQueryEndpoints` | Query job status |
| `/query/ai` | POST | `handleRealtimeQueryEndpoints` | AI query assistance |
| `/query` | POST | `handleRealtimeQueryEndpoints` | Execute query |
| `/v1/interactions` | GET/POST | `handleJobEndpoints` | Interactions CRUD |
| `/job/*` | GET/POST/PUT/DELETE | `handleJobEndpoints` | Job management |
| `/permission/check` | GET | `handlePermissionEndpoints` | Permission checks |
| `/alert` | GET/POST/PUT/DELETE | `handleAlertEndpoints` | Alerts CRUD |
| `/session-replays` | GET | `handleSessionReplaysEndpoints` | Session replays |
| `/getApdexScore` | POST | `handleAnalyticsEndpoints` | APDEX metrics |
| `/getErrorRate` | POST | `handleAnalyticsEndpoints` | Error rate |
| `/getInteractionTime` | POST | `handleAnalyticsEndpoints` | Interaction timing |
| `/getInteractionCategory` | POST | `handleAnalyticsEndpoints` | Interaction categories |
| `/api/v1/interaction/insights` | POST | `handleInteractionInsightsEndpoint` | Insights |
| `/getUserEvent` | GET | `handleUserEventsEndpoints` | User events |
| `/validateQuery` | POST | `handleUniversalQueryEndpoints` | Query validation |
| `/fetchQueryData` | POST | `handleUniversalQueryEndpoints` | Fetch results |
| `/getQuery/*` | GET | `handleUniversalQueryEndpoints` | Query details |
| `/analytics-report` | GET | `handleAnalyticsReportEndpoints` | Reports |
| `/incident/generateReport` | POST | `handleAnalyticsReportEndpoints` | Generate report |
| `/anomaly/*` | GET | `handleAnomalyEndpoints` | Anomaly detection |
| `/v1/configs/*` | GET/POST/PUT | `handleSdkConfigV1Endpoints` | SDK config V1 |
| `/sdk-config` | GET/POST/PUT | `handleSdkConfigEndpoints` | SDK config legacy |
| `/events` | GET/POST | `handleEventEndpoints` | Events |
| `/whitelist` | POST | `handleEventEndpoints` | Whitelist events |

---

## ⚠️ Partially Implemented

| Endpoint | Method | What's Missing | Priority |
|----------|--------|----------------|----------|
| `/v1/auth/social/authenticate` | POST | Old flow, needs deprecation | P3 |

---

## ✅ Phase 2 Complete - Tenant & Project Management

### Tenant Operations
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/tenants/:tenantId` | GET | `handleTenantEndpoints` | ✅ | Returns tenant details |
| `/v1/tenants` | GET | — | ❌ P3 | Tenant list (admin only) |
| `/v1/tenants/:tenantId` | PUT | — | ❌ P2 | Update org details |
| `/v1/tenants/:tenantId/deactivate` | PUT | — | ❌ P3 | Deactivate org |
| `/v1/tenants/:tenantId/activate` | PUT | — | ❌ P3 | Activate org |

### Tenant Member Management
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/tenants/:tenantId/members` | GET | `handleTenantEndpoints` | ✅ | Lists members with roles |
| `/v1/tenants/:tenantId/members` | POST | `handleTenantEndpoints` | ✅ | Single & bulk invite support |
| `/v1/tenants/:tenantId/members/:userId` | DELETE | — | ❌ P2 | Remove members |
| `/v1/tenants/:tenantId/members/:userId` | PATCH | — | ❌ P2 | Update roles |
| `/v1/tenants/:tenantId/members/leave` | DELETE | — | ❌ P3 | Leave org |

### Project Operations
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects` | POST | `handleProjectEndpoints` | ✅ | Creates project with API key |
| `/v1/projects/:projectId` | GET | `handleProjectEndpoints` | ✅ | Returns project details |
| `/v1/projects/:projectId` | PUT | — | ❌ P2 | Update project |
| `/v1/projects/:projectId` | DELETE | — | ❌ P3 | Delete project |

### Project Member Management
| Endpoint | Method | Handler | Status | Notes |
|----------|--------|---------|--------|-------|
| `/v1/projects/:projectId/members` | GET | `handleProjectEndpoints` | ✅ | Lists members with roles |
| `/v1/projects/:projectId/members` | POST | `handleProjectEndpoints` | ✅ | Single & bulk invite support |
| `/v1/projects/:projectId/members/:userId` | DELETE | — | ❌ P2 | Remove collaborators |
| `/v1/projects/:projectId/members/:userId` | PATCH | — | ❌ P2 | Update roles |
| `/v1/projects/:projectId/members/leave` | DELETE | — | ❌ P3 | Leave project |

---

## ❌ Phase 3 - Remaining Endpoints

### API Keys (P2 - Medium Priority)
| Endpoint | Method | Backend File | Blockers |
|----------|--------|--------------|----------|
| `/v1/projects/:projectId/api-keys` | GET | `ProjectApiKeysController.java` | API key management |
| `/v1/projects/:projectId/api-keys` | POST | `ProjectApiKeysController.java` | Generate keys |
| `/v1/projects/:projectId/api-keys/:keyId` | DELETE | `ProjectApiKeysController.java` | Revoke keys |

### Other Missing (P2-P3)
| Endpoint | Method | Backend File | Priority | Blockers |
|----------|--------|--------------|----------|----------|
| `/v1/auth/tenant/lookup` | GET | `Authenticate.java` | P3 | Tenant lookup helper |

---

## 📊 Statistics

- **Total Backend Endpoints**: ~75
- **Mocked Endpoints**: 55
- **Coverage**: 73%
- **Phase 1**: ✅ Complete (Auth, Onboarding, TNC)
- **Phase 2**: ✅ Complete (Project/Tenant CRUD + Member Lists/Invite)
- **Phase 3**: 🔄 Pending (Remaining CRUD operations - 12 endpoints)

---

## 🎯 Next Priority - Phase 3

**Remaining CRUD Operations** (12 endpoints):
1. Member removal: `DELETE /v1/tenants/:tenantId/members/:userId`
2. Member removal: `DELETE /v1/projects/:projectId/members/:userId`
3. Role updates: `PATCH /v1/tenants/:tenantId/members/:userId`
4. Role updates: `PATCH /v1/projects/:projectId/members/:userId`
5. Update project: `PUT /v1/projects/:projectId`
6. Update tenant: `PUT /v1/tenants/:tenantId`
7. API key management (3 endpoints)
8. Other misc endpoints (tenant lookup, etc.)

---

## 🔍 Known Issues

1. **Analytics graphs not loading**: Requires `projectId` in context (backend-dependent)
2. **Mock data reset on refresh**: In-memory only, no persistence
3. **No validation**: Mock accepts any input (backend validates)

## 📝 Recent Updates

### Phase 2 (Completed 2024-03-12)
- ✅ Implemented project creation and details endpoints
- ✅ Implemented tenant details endpoint
- ✅ Implemented member listing for both tenant and project
- ✅ Implemented member invite (single & bulk) for both tenant and project
- ✅ Added realistic mock data with Indian names and mixed roles
- ✅ Fixed TypeScript compilation errors (Array.from instead of spread)
- ✅ Coverage increased from 64% to 73%

---

## 📝 Notes

- Mock server returns realistic data with Indian context (states, cities, devices)
- Error simulation: 10% by default (configurable via `REACT_APP_MOCK_ERROR_RATE`)
- Delay simulation: 500ms by default (configurable via `REACT_APP_MOCK_DELAY`)
- All mocked endpoints log to console when `REACT_APP_MOCK_LOGGING=true`
