---
name: session-replay-context
description: Load product and UI context for Session Replay in Pulse UI. Use when building or changing the Session Replay screen, filters, stat cards, replay viewer, or related copy.
---

# Session Replay context (Pulse UI)

Use this skill when working on **Session Replay** in pulse-ui so UI and copy match product intent and design.

## What to read first

1. **Product and domain**
   - **`.cursor/DOMAIN_CONTEXT.md`** – What Pulse is, what Session Replay is (evidence layer), how it fits with analytics/funnels/crashes/PulseAI.
   - **pulse-docs/session-replay/01_CONTEXT.md** – Deeper product context (why Session Replay, target users, competitors).

2. **UI implementation (layout, design, components)**
   - **pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md** – Layout (header, sidebar, main content), stat cards (Total Sessions, Avg Duration, Total Interactions, Avg Events/Session), replay viewer (playback controls, timeline), teal theme (primary teal, borders, gradients), typography, spacing, icons (@tabler/icons-react), mock data shape (`SessionReplayData`), file locations under `src/screens/SessionReplay/`.

## Scope for Session Replay UI

- **List + filters + playback + shareable links.** Stats come from backend (do not derive from paginated list).
- **Evidence layer:** Copy and flows should reflect that Session Replay is for “watching” what users experienced—linking metrics, funnels, and crashes to actual sessions.
- **Rule:** When editing under `src/screens/SessionReplay/`, the rule **session-replay-context.mdc** also applies; it points to DOMAIN_CONTEXT and QUICK_REFERENCE.

## Reference in repo

- Screen: `src/screens/SessionReplay/` – structure, hooks, components, constants, types.
- Service: `src/services/sessionReplay/` – API and types.
