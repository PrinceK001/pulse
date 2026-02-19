---
name: add-new-screen
description: Adds a new screen/route to Pulse UI following existing patterns. Use when the user wants to add a new page, screen, or route to the app, or when creating a new feature that needs a dedicated route.
---

# Add new screen (Pulse UI)

Use this skill when adding a new top-level screen or page to Pulse UI. Follow existing screens (e.g. `src/screens/SessionReplay/`, `src/screens/UserEngagement/`) for structure.

## Checklist

1. **Create screen folder and files (feature-first)**
   - `src/screens/<ScreenName>/<ScreenName>.tsx` – main component (clean JSX; data/actions in hooks).
   - `src/screens/<ScreenName>/index.ts` – export the screen component.
   - **Constants, types, utils live inside the feature**: `ScreenName.interface.ts`, `ScreenName.constants.ts`, and if needed `utils/` (e.g. `utils/formatFilters.ts`) **inside** `src/screens/<ScreenName>/`. Do not add feature-specific constants or utils to top-level `src/constants/` or `src/utils/`—that pollutes global space. Add `hooks/`, `components/` as needed.

2. **Register route**
   - Open `src/constants/Constants.ts`.
   - Add a new key to the `ROUTES` object (see existing keys like `SESSION_REPLAY`, `USER_ENGAGEMENT`).
   - Each entry needs: `key`, `basePath`, `path`, `element` (the component).
   - Example shape: `NEW_SCREEN: { key: 'NEW_SCREEN', basePath: '/new-screen', path: '/new-screen', element: NewScreen }`.
   - Add the import for the new component at the top of `Constants.ts`.

3. **Add to navbar (if needed)**
   - In `Constants.ts`, find `NAVBAR_ITEMS`.
   - Add an entry with `label`, `icon` (from `@tabler/icons-react`), `routeTo: ROUTES.NEW_SCREEN.basePath`, `path: ROUTES.NEW_SCREEN.path`.
   - Match the structure of existing items (e.g. `routeTo`, `path`, `label`, `icon`).

4. **Data and state**
   - **Prefer** TanStack Query for new screens: hooks under `src/screens/<ScreenName>/hooks/` with useQuery/useMutation calling `src/services/`. If the feature is very small or you’re matching an existing manual-fetch screen, follow that pattern instead.
   - Do not add Remix loaders/actions or useFetcher/useLoaderData.

## File order in Constants.ts

- Imports at top (include the new screen component).
- Add new key to `ROUTES` (alphabetical or by feature area).
- If the screen is in the nav, add one object to the `NAVBAR_ITEMS` array.

## Naming

- **Screen folder**: PascalCase (e.g. `SessionReplay`, `NewFeature`).
- **Route key**: UPPER_SNAKE_CASE (e.g. `SESSION_REPLAY`, `NEW_FEATURE`).
- **Path**: kebab-case (e.g. `/session-replay`, `/new-feature`).

## Feature-first

- **Constants**: `ScreenName.constants.ts` inside the screen folder (page size, filter defaults, labels). Only use `src/constants/` for app-wide things (routes, app config).
- **Utils**: `ScreenName/utils/` inside the screen folder for feature-specific helpers. Only use `src/utils/` for shared utilities used in multiple features.
- **Types**: `ScreenName.interface.ts` inside the screen folder. Only use `src/types/` for types shared across features.

## Session Replay specifically

- If the new screen is **Session Replay** or a sub-area of it: read **pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md** for layout, stat cards, replay viewer, teal theme, typography, and file locations. Use `.cursor/DOMAIN_CONTEXT.md` for product context (what Session Replay is in Pulse).

## Reference

- Route type: `src/constants/Constants.interface.ts` – `Routes` type.
- Example screen: `src/screens/SessionReplay/` or `src/screens/UserEngagement/` (each has its own constants, interface, optionally utils).
- App entry: `src/App.tsx` – already maps `ROUTES` to `<Route>`; no change needed there when only adding to `ROUTES`.
- **Tech practices**: `.cursor/TECH_STACK_PRACTICES.md` – TypeScript (no any, inference), TanStack Query (useQuery/useMutation, invalidateQueries), Mantine (theme, components).
