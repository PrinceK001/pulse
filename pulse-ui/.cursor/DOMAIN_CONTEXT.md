# Pulse & Session Replay – Domain context for Pulse UI

**Use this when working on Pulse UI so the AI understands what Pulse is and how Session Replay fits in.**

**Resolving pulse-docs:** Paths below are relative to the pulse repo. From **pulse-ui** root, pulse-docs is often `../pulse-docs/` or `../../pulse-docs/`—check the repo layout (e.g. `ls ..` or open parent) and use the path that exists.

---

## What is Pulse?

**Pulse is an experience intelligence platform.** It competes with Contentsquare, FullStory, Amplitude, and similar tools.

- **Problem it solves:** Teams use 4–5 disconnected tools (RUM, product analytics, UX analytics, feedback, session replay). Each captures a fragment; nobody has the full picture. Critical questions (“why did conversion drop?”) get answered too late.
- **Pulse unifies these signals** into one platform. Its differentiator is **interaction-first**: every user interaction is measured for **quality** (latency, jank, success, interruption), segmented by device/network/release, and connected to business outcomes.
- **Target customers:** Mid-to-large digital-first companies (fintech, commerce, gaming, media, marketplaces). 1M+ MAU. Experience directly impacts revenue.
- **Other modules (besides Session Replay):** RUM/performance monitoring, interaction analytics, funnels, user segments/cohorts, journey mapping, retention analytics, business metrics, feedback, error tracking, PulseAI.

**Pulse UI** is the React frontend for this platform. It provides dashboards, **Session Replay**, App Vitals, Query Builder, Alerts, User Engagement, Screens, Network APIs, etc. Session Replay has the most doc coverage in pulse-docs; for other screens, follow existing code in `src/screens/<ScreenName>/` and AGENTS.md reference implementations.

---

## What is Session Replay?

**Session Replay is one module within Pulse.** It is the **evidence layer** that makes other modules actionable.

- **Definition:** Technology that captures and reconstructs user interactions so teams can “watch” what users experienced (like a DVR for user sessions). It is **not** screen recording—it’s reconstruction from DOM/view hierarchy + events (smaller, searchable, maskable).
- **Role in Pulse:**
  - Interaction analytics says “Add to Cart is slow on Samsung.” Session Replay lets you **watch** what that slowness looks like.
  - Funnels say “23% drop-off at checkout.” Session Replay lets you **watch** sessions of users who dropped off.
  - Crash reports: Session Replay shows **what the user was doing and saw** when it crashed.
  - PulseAI: Session Replay is the **proof**—watch sessions, see degradation.

**Session Replay UI (in Pulse UI)** includes: session list with filters, stat cards (total sessions, avg duration, interactions, events/session), replay viewer (playback controls, timeline), and integration with Pulse’s existing session/telemetry data. Design follows Pulse UI’s teal theme; see Session Replay quick reference for layout, colors, and components.

---

## Where to read more (pulse-docs)

| Topic | Doc path (relative to pulse repo) |
|-------|------------------------------------|
| **Pulse & Session Replay context (start here)** | `pulse-docs/session-replay/01_CONTEXT.md` |
| What is Session Replay (concept) | `pulse-docs/session-replay/02_WHAT_IS_SESSION_REPLAY.md` |
| How Session Replay works (technical) | `pulse-docs/session-replay/04_HOW_SESSION_REPLAY_WORKS.md` |
| **Session Replay UI (layout, design, components)** | `pulse-docs/session-replay/02-ui-implementation/QUICK_REFERENCE.md` |
| Session Replay implementation (backend, SDK, UI) | `pulse-docs/session-replay/01-implementation/`, `17_TECH_SPEC_REFERENCE.md` |
| Pulse vision & current state | `pulse-docs/current/01_START_HERE.md`, `04_PULSE_TRUE_VISION.md` |

When building or changing Session Replay UI, read **QUICK_REFERENCE.md** for layout, stat cards, replay viewer, teal theme, typography, and file locations. When explaining product context to users or making product-minded decisions, use **01_CONTEXT.md**.
