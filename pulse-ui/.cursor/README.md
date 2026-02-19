# Pulse UI – Cursor setup

This `.cursor` folder configures Cursor IDE for the **pulse-ui** project. Everything here is scoped to pulse-ui (this directory is inside the pulse repo).

**Mindset:** Rules and skills are there to improve quality and consistency **gradually**. Don’t over-engineer. Match the repo; P0/P1 (no `any`, error handling, types, prefer TQ for new code) matter most. The rest is “aim for when you touch code.”

## Contents

| Path | Purpose |
|------|--------|
| **rules/** | **Always-on:** commands-and-workflow (yarn lint/format, run lint after edits, see AGENTS.md for canonical examples), priorities-and-stack, pulse-ui-context, no-testing. **By glob:** feature-first-abstraction, error-handling, screens-and-data, typescript-*, component-*, api-and-services, data-routing-hooks, constants-organization, design-system, env-and-imports, session-replay-context. Root `.cursorrules` is a stub. |
| **plans/** | Save Plan Mode plans here (Shift+Tab → plan → "Save to workspace"). See **plans/README.md**. |
| **skills/** | Project skills – add screen, fix lint, API service, **verify-after-edit**, **session-replay-context** (load Session Replay product/UI context from docs). All follow feature-first. |
| **commands/** | Suggested Cursor Custom Commands – add screen, fix lint, implement feature, review, API service, verify after edit, feature vs global, match reference, **Session Replay context**, **Domain and tech context**. |
| **DOMAIN_CONTEXT.md** | What Pulse is, what Session Replay is, where to read more in pulse-docs. Use for product/domain questions. |
| **TECH_STACK_PRACTICES.md** | TypeScript (strict, no any, unknown, inference), TanStack Query (useQuery/useMutation, invalidateQueries), React, Mantine. Use to align code with language and library best practices. |
| **HOW_TO_USE.md** | When to divide rules/skills/commands, and what improves accuracy and confidence. |
| **RULES_AND_AGENTS_LAYOUT.md** | Where .cursorrules and AGENTS.md must live (project root), whether they can move to .cursor/, and whether to break them down (e.g. .cursorrules → .cursor/rules/*.mdc). |

## Also in pulse-ui root

- **`.cursorrules`** – Stub; points to `.cursor/rules/` and plans.
- **`AGENTS.md`** – **Single source for canonical examples.** Stack, paths, "Reference implementations" table. Reference this instead of copying content into rules.

## Quick start

1. **Open pulse-ui as workspace.**
2. **Use AGENTS.md** for stack and for where to look (reference implementations table).
3. **After edits:** Run `yarn lint` and `yarn format`; see verify-after-edit skill or commands-and-workflow rule.
4. **Non-trivial work:** Plan Mode (Shift+Tab); save plan to `.cursor/plans/`.
5. **Custom commands:** Copy prompts from `.cursor/commands/COMMANDS.md` into Cursor Settings.
6. **Mention skills by name** (optional): in chat, e.g. "use the add-new-screen skill", when you want the agent to follow a specific workflow.

No config files here are required for Cursor to run; they improve relevance and consistency when you work in pulse-ui.
