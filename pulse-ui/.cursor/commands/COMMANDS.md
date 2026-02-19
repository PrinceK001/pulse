# Suggested Cursor custom commands (Pulse UI)

Copy the **Prompt** for each command into Cursor's Custom Commands. The **Name** is for your reference.

---

## Add new screen

**Name:** `Pulse UI: Add new screen`  
**Prompt:**
```
Follow the add-new-screen skill in .cursor/skills/add-new-screen/SKILL.md. Add a new screen to Pulse UI: create the screen folder under src/screens/, add the route to ROUTES in src/constants/Constants.ts (and to NAVBAR_ITEMS if it should appear in the nav). Use TanStack Query and existing service patterns; no Remix. Use the current conversation or my next message for the screen name and path.
```

---

## Fix lint and format

**Name:** `Pulse UI: Fix lint and format`  
**Prompt:**
```
Follow the fix-lint-format skill in .cursor/skills/fix-lint-format/SKILL.md. From the pulse-ui directory run yarn lint and yarn format (or yarn lint:fix then yarn format), then fix any remaining lint errors in the files I have open or that were recently changed. Do not use any except where the project allows.
```

---

## Implement feature with data

**Name:** `Pulse UI: Implement feature with data`  
**Prompt:**
```
We are in Pulse UI (React, TanStack Query, Zustand, Mantine). Follow .cursorrules and AGENTS.md. Use useQuery for fetching and useMutation for mutations (invalidateQueries after success); call services in src/services/. No Remix. Keep JSX clean and put logic in custom hooks. For TypeScript: no any, use unknown + type guards for errors. If the feature is Session Replay, read .cursor/DOMAIN_CONTEXT.md and pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md for product and UI context. Implement the feature or change I describe in my next message.
```

---

## Review code for Pulse UI conventions

**Name:** `Pulse UI: Review for conventions`  
**Prompt:**
```
Review the selected or open code for Pulse UI conventions. Check: (1) No Remix (use useQuery/useMutation and services), (2) Clean JSX and logic in hooks, (3) Types in .interface.ts and no any, (4) Feature-first constants/utils, (5) Matches patterns in .cursorrules and AGENTS.md. List concrete fixes if any.
```

---

## Add or extend API service

**Name:** `Pulse UI: Add/extend API service`  
**Prompt:**
```
Follow the api-service-pattern skill in .cursor/skills/api-service-pattern/SKILL.md. Add or extend an API service in src/services/ for Pulse UI. Use the existing client and REACT_APP_* env; expose functions used by useQuery/useMutation. Type request/response properly. Use my next message or the current file for the endpoint/behavior.
```

---

## Verify after edit

**Name:** `Pulse UI: Verify after edit`  
**Prompt:**
```
Follow P0 post-development checks from .cursor/rules/post-development-checks.mdc: (1) Run 'npx tsc --noEmit' from pulse-ui and verify exit code 0, (2) Use ReadLints tool on all modified files, (3) Fix any new TypeScript or lint errors immediately, (4) List the changed files. Only fix new issues introduced by our edits.
```

---

## Should this be global or feature?

**Name:** `Pulse UI: Feature vs global?`  
**Prompt:**
```
We're in Pulse UI. Feature-first: constants, utils, types belong in the feature unless truly shared across 2+ features or app-wide. For the code or file I have selected, decide: feature or src/constants|utils|types? One-line decision and reason. When in doubt, recommend feature.
```

---

## Use reference implementation

**Name:** `Pulse UI: Match reference`  
**Prompt:**
```
We're in Pulse UI. AGENTS.md lists reference implementations. Open the relevant reference (e.g. src/screens/SessionReplay/, src/services/sessionReplay/, src/utils/errorHandling.ts) and apply the same patterns to my current code. Use .cursor/TECH_STACK_PRACTICES.md for TypeScript and TanStack Query patterns. If working on Session Replay, also read .cursor/DOMAIN_CONTEXT.md and pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md. List which reference you used.
```

---

## Load Session Replay context

**Name:** `Pulse UI: Session Replay context`  
**Prompt:**
```
Follow the session-replay-context skill in .cursor/skills/session-replay-context/SKILL.md. Read .cursor/DOMAIN_CONTEXT.md and pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md (and 01_CONTEXT.md if available). Summarize what Session Replay is in Pulse, and the UI layout/design (stat cards, replay viewer, teal theme, file locations). Use this context for my next messages about Session Replay.
```

---

## Load domain and tech context

**Name:** `Pulse UI: Domain and tech context`  
**Prompt:**
```
Read .cursor/DOMAIN_CONTEXT.md (what Pulse is, what Session Replay is, where pulse-docs live) and .cursor/TECH_STACK_PRACTICES.md (TypeScript, TanStack Query, React, Mantine). Summarize briefly and use this context for my next messages in pulse-ui.
```

---

## Research architecture and implement

**Name:** `Pulse UI: Research and implement`  
**Prompt:**  
Use the full prompt in `.cursor/commands/research-and-implement.md`. In short: (1) Research the engineering problem I give in my next message and recommend the best architecture or approach. (2) Implement it in the repo—create or edit screens, services, hooks, types, constants following Pulse UI conventions (feature-first, useQuery/useMutation, AGENTS.md reference implementations). Run yarn lint and yarn format after changes. If scope is large, implement a first slice and list next steps.

---

You can add more commands (e.g. "Run tests", "Generate types from API") by adding a **Name** and **Prompt** that reference the right skill or `.cursorrules` section.
