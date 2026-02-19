# Pulse UI – Agent context

Use this file when working in the **pulse-ui** frontend. It orients the AI to the project and points to the main rules.

## Reality & priorities

- **Don't over-engineer.** The codebase is mixed: many flows use TanStack Query (see `src/hooks/`); some screens (e.g. SessionReplay) still use manual fetch + useState. Both exist. Improve gradually.
- **P0**: No `any`; no explicit `undefined` for initialization (use `null` or appropriate falsy); no hardcoded API-driven values (fetch from backend/config); error handling in new code; types for public/API contracts; **TypeScript must compile without errors**; **no new lint errors on modified files**.
- **P1**: For **new** code, prefer useQuery/useMutation, presentational JSX, named constants for domain values, and actionable error UX. Don't mandate big rewrites of existing code.
- **Match the file you're in**: Prefer existing patterns in that screen or module. When adding a new screen or service, follow the patterns in `.cursorrules` and here.
- **Post-development checks (P0)**: After ANY code changes, run `npx tsc --noEmit` and `ReadLints` on modified files. Fix issues immediately before proceeding. See `.cursor/rules/post-development-checks.mdc`.

## Domain context (what is Pulse, what is Session Replay)

- **Pulse** is an **experience intelligence platform** that unifies RUM, analytics, UX, feedback, and session replay. It is **interaction-first**: every interaction is measured for quality (latency, jank, success) and connected to business outcomes. Competes with Contentsquare, FullStory, Amplitude.
- **Session Replay** is one module in Pulse: the **evidence layer**. It lets users watch reconstructed sessions (not video—DOM/view hierarchy + events) so they can see why metrics moved, why users dropped off, or what the user saw when something crashed. In Pulse UI, the Session Replay screen has: session list with filters, stat cards, replay viewer with playback controls, and teal-themed layout.
- **Full domain and product context:** Read **`.cursor/DOMAIN_CONTEXT.md`** (and when working on Session Replay UI, **`pulse-docs/session-replay/01_CONTEXT.md`** and **`pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md`**).

## Project

- **Name**: Pulse UI (Pulse observability platform frontend).
- **Location**: This repo is `pulse-ui` (subfolder inside the pulse repo). All paths below are relative to `pulse-ui/`. Docs for Pulse and Session Replay live in **pulse-docs/** (sibling or parent of pulse-ui).

## Stack

| Layer        | Technology                                                                                      |
| ------------ | ----------------------------------------------------------------------------------------------- |
| Framework    | React 18, TypeScript                                                                            |
| Routing      | React Router 6 – routes in `src/constants/Constants.ts` (`ROUTES`), rendered in `src/App.tsx`   |
| Server state | TanStack Query (useQuery, useMutation); API calls go through `src/services/` and `src/clients/` |
| Client state | Zustand (`src/stores/`), React Context when needed                                              |
| UI           | Mantine + CSS modules (`*.module.css`)                                                          |
| Build        | Webpack (CRA-style)                                                                             |

**No Remix.** Do not suggest loaders, actions, or useFetcher. Use TanStack Query and the existing services.

## Key paths & feature-first abstraction

- **Screens**: `src/screens/<ScreenName>/` – one folder per screen. Each feature is **self-contained**: put its **constants** (`FeatureName.constants.ts`), **utils** (`utils/`), **types** (`.interface.ts`), **hooks/**, **components/** inside this folder. Do not default to top-level `src/constants/` or `src/utils/` for feature-specific code—that pollutes global space.
- **Global (only for truly shared code)**: Use `src/constants/`, `src/utils/`, `src/types/` only when code is used **across multiple features or app-wide** (e.g. routes, app config, theme, `getErrorMessage`, shared formatters). When in doubt, keep it in the feature.
- **Shared UI**: `src/components/` – components used in multiple places.
- **Routes**: `src/constants/Constants.ts` – `ROUTES` (app-wide by nature). Add new screens here and in `App.tsx`.
- **API**: `src/services/`, `src/clients/` – services can be per-feature under `src/services/<feature>/` or shared. Use from useQuery/useMutation.
- **Global state**: `src/stores/` (e.g. `useFilterStore.ts`) for app-wide or cross-feature state.
- **Theme**: `src/theme/`, Mantine provider in `App.tsx`.

## Conventions (summary)

- **Data**: Prefer useQuery/useMutation + services for **new** code; existing manual fetch is acceptable.
- **Clean JSX**: Keep JSX presentational; put logic in hooks where it improves clarity.
- **Feature-first**: Constants, utils, types live **inside the feature** (e.g. `SessionReplay.constants.ts`, `SessionReplay/utils/`, `SessionReplay.interface.ts`). Global `src/constants/`, `src/utils/`, `src/types/` only for **truly shared** code (routes, theme, shared formatters). Don't pollute global space.
- **Types**: Prefer `.interface.ts` in the feature; inline OK for local-only. Shared types in `src/types/` only when used across features.
- **Errors**: Surface errors; use `src/utils/errorHandling.ts` and actionable UI (retry, message). Don't leave the user with no path forward.
- **Env**: `REACT_APP_*` (e.g. `REACT_APP_PULSE_SERVER_URL`). See `.env.example`.

## Rules and skills

- **Full rules**: See **`.cursorrules`** (architecture, TypeScript, design system). Top section defines P0/P1/P2 and "don't over-engineer."
- **Scoped rules**: **`.cursor/rules/*.mdc`** – **post-development-checks** (P0: always run `npx tsc --noEmit` and `ReadLints` after changes), **commands-and-workflow** (always: yarn lint/format, run lint after edits, see AGENTS.md for canonical examples), **priorities-and-stack** (always), **pulse-ui-context** (always), **no-testing** (always), plus feature-first-abstraction, error-handling, screens-and-data, typescript-*, component-*, api-and-services, data-routing-hooks, constants-organization, design-system, env-and-imports, session-replay-context. Root **`.cursorrules`** is a stub. **Plans:** Save Plan Mode plans to `.cursor/plans/` (see `.cursor/plans/README.md`).
- **Skills**: **`.cursor/skills/`** – add screen, fix lint/format, API service, **verify-after-edit** (run lint/format after changes and list changed files).
- **Commands**: **`.cursor/commands/COMMANDS.md`** – suggested custom commands (add screen, fix lint, review, verify after edit, feature vs global, match reference).
- **How to use**: **`.cursor/HOW_TO_USE.md`** – when to divide rules/skills and what improves accuracy and confidence.
- **Domain**: **`.cursor/DOMAIN_CONTEXT.md`** – what Pulse is, what Session Replay is, where to read more in pulse-docs.
- **Tech stack practices**: **`.cursor/TECH_STACK_PRACTICES.md`** – TypeScript (strict, no any, unknown, inference), TanStack Query (useQuery/useMutation, invalidateQueries), React (presentational JSX, hooks), Mantine (theme, components). Use these to align code with language and library best practices.

**Question the rules** when they add ceremony without benefit (e.g. forcing an enum for a one-off union, or splitting a tiny helper into its own file). Prefer existing patterns in the file you're in; apply P0/P1 consistently.

---

## Reference implementations (read the repo)

When you're not sure how something should look, **read these files** and match their patterns. Prefer copying existing style over inventing.

| For                                         | Look at                                                                                                             |
| ------------------------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Screen structure (files, hooks, components) | `src/screens/SessionReplay/` or `src/screens/UserEngagement/`                                                       |
| Feature constants / types                   | `src/screens/SessionReplay/SessionReplay.constants.ts`, `SessionReplay.interface.ts`                                |
| API service (class or functions, typing)    | `src/services/sessionReplay/SessionReplayService.ts`, `src/services/sessionReplay/types.ts`                         |
| Error handling                              | `src/utils/errorHandling.ts`                                                                                        |
| Routes and nav                              | `src/constants/Constants.ts` (ROUTES, NAVBAR_ITEMS)                                                                 |
| Shared hooks (TQ, etc.)                     | `src/hooks/useGetDashboardFilters/`, hooks under `src/screens/AppVitals/`                                           |
| Session Replay product/UI context           | `.cursor/DOMAIN_CONTEXT.md`, **pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md**, `01_CONTEXT.md` |
| TypeScript & TanStack Query practices       | `.cursor/TECH_STACK_PRACTICES.md`                                                                                   |

---

## Confidence and verification

- **When unsure:** State your assumption (e.g. "Assuming this is only used in this screen, so keeping constants in the feature"). Prefer the **conservative** choice: e.g. keep in feature, don't refactor unrelated code, don't add global constants "just in case."
- **After making edits (P0):** Run `npx tsc --noEmit` and use `ReadLints` tool on all modified files. Fix any **new** errors immediately before proceeding. List the files you changed so the user can confirm. See `.cursor/rules/post-development-checks.mdc`.
- **Placement (feature vs global):** If not clearly shared across 2+ features, keep it in the feature. When in doubt, keep in feature.

---

## Anti-patterns (don't do)

- **Don't** put feature-specific constants, utils, or types in `src/constants/`, `src/utils/`, or `src/types/`. They belong in the feature folder.
- **Don't** suggest Remix APIs (loaders, actions, useFetcher, useLoaderData). Use TanStack Query and existing services.
- **Don't** use `any`. Use proper types or `unknown` + type guards.
- **Don't** leave error states with no path forward (e.g. only a dismiss button). Prefer a Retry or clear message so the user can act.
- **Don't** over-engineer: avoid big rewrites or "ideal" refactors when the task is a small change. Match the file you're in.
- **Don't** add test files (e.g. `*.test.tsx`, `*.spec.ts`); testing is handled separately for this repo.
- **Don't** use try-catch unneccesssarily
