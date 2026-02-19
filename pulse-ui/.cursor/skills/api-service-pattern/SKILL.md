---
name: api-service-pattern
description: Adds or extends an API service in Pulse UI using TanStack Query and existing clients. Use when creating a new service, adding API calls, or integrating a new backend endpoint in pulse-ui.
---

# API service pattern (Pulse UI)

Use this skill when adding or extending API calls and services in pulse-ui. The app uses **TanStack Query** and **Axios** (or existing clients); there are no Remix loaders/actions.

## Where things live

- **Services**: `src/services/` – e.g. `src/services/sessionReplay/SessionReplayService.ts`. Prefer **per-feature** folders (`src/services/<feature>/`) when the API is only used by that feature; shared services can live at top level or in a shared folder.
- **Clients**: `src/clients/` – shared API client / axios instance; use for base URL and interceptors.
- **Env**: Base URL from `process.env.REACT_APP_PULSE_SERVER_URL` (app config lives in `src/constants/Constants.ts` – `API_BASE_URL` is app-wide). Feature-specific endpoint paths can live in the feature’s constants or next to the service; only put truly shared API config in global constants.

## Adding a new service

1. **Create or extend a service file**
   - Example: `src/services/<feature>/<Feature>Service.ts` or `src/services/<featureName>.ts`.
   - Export functions that return promises (e.g. `getList(filters)`, `getById(id)`, `create(payload)`).
   - Use the shared client (e.g. axios instance) from `src/clients/` or the pattern used in existing services.

2. **Use in TanStack Query**
   - **Fetching**: In a hook (e.g. under `src/screens/<Screen>/hooks/`), use `useQuery({ queryKey: [...], queryFn: () => Service.getList(...) })`.
   - **Mutations**: Use `useMutation({ mutationFn: (payload) => Service.create(payload), onSuccess: () => queryClient.invalidateQueries({ queryKey: [...] }) })`. Prefer **invalidateQueries** after success over manually updating the cache (see `.cursor/TECH_STACK_PRACTICES.md`).

3. **Types (feature-first)**
   - Define request/response types in the **feature’s** `*.interface.ts` (e.g. `src/screens/SessionReplay/SessionReplay.interface.ts`) or next to the service if the service is feature-scoped. Use `src/types/` only for types shared across multiple features. No `any`.

## Conventions

- Service functions are **async** and return typed data (or throw).
- Keep **queryKey** consistent (e.g. `['sessionReplay', filters]`) so invalidateQueries works.
- Error handling: use existing patterns (e.g. toast, error boundaries); type errors properly (no `any`).

## Reference

- Existing services: `src/services/sessionReplay/`, and other folders under `src/services/`.
- Constants: `API_BASE_URL`, `REQUEST_TIMEOUT` in `src/constants/Constants.ts`.
- `.cursorrules` in pulse-ui: "API Client Structure", "Error Handling", "Mock-Ready API Configuration".
- **Tech practices**: `.cursor/TECH_STACK_PRACTICES.md` – TanStack Query (queryKey, invalidateQueries), TypeScript (no any, typed request/response).
- **Domain**: For Session Replay APIs, stats and filters should align with Session Replay scope in `.cursor/DOMAIN_CONTEXT.md` and pulse-docs.
