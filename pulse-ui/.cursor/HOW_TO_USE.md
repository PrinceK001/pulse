# How to get the best use of Cursor in Pulse UI

## Do we need to divide into more rules, skills, commands, agents?

**Short answer:** We already have a good split. A few targeted additions improve accuracy; more division than that has diminishing returns.

### Current split (keep)

- **One AGENTS.md** – Single entry point for context, priorities, and paths. Splitting into many “agent” files fragments context; one file + scoped rules is enough.
- **Root .cursorrules** – Short stub pointing to `.cursor/rules/*.mdc`. Full guidance lives in **scoped .mdc rules** so the right context loads when relevant.
- **.cursor/rules/*.mdc** – Scoped by file type or always-on. These **are** the “division”: they load only when relevant (e.g. screens-and-data when in `src/screens/` or `src/services/`), so the AI gets the right slice without losing the rest.
- **Skills** – One per workflow (add screen, fix lint, API service). Adding more is useful only for **frequent, repeatable** workflows (e.g. “verify after edit”); avoid many one-off skills.
- **Commands** – User-triggered. Adding more command prompts in COMMANDS.md is free: each gives a one-shot way to get consistent behavior (review, verify, decide feature vs global).

### What we added to improve accuracy and confidence

1. **More scoped rules** – e.g. `error-handling.mdc` when editing services/utils/hooks, so error-handling guidance is in context when it matters.
2. **Reference implementations** – In AGENTS.md: “For screen structure see X; for service see Y.” So the AI can **read the repo** for ground truth instead of guessing.
3. **Confidence & verification** – In AGENTS.md: when unsure, state assumption and prefer conservative choice; after edits, run lint and fix new issues. Reduces overconfident wrong answers.
4. **Anti-patterns** – Short “don’t” list so the model avoids the most common mistakes (e.g. feature constants in global, Remix APIs, `any`).
5. **Verify skill + commands** – A “verify after edit” skill and commands for “run lint and list changes” and “should this be global or feature?” so correctness is built into the workflow.
6. **Domain and tech context** – **DOMAIN_CONTEXT.md** (what Pulse is, what Session Replay is, where pulse-docs live) and **TECH_STACK_PRACTICES.md** (TypeScript, TanStack Query, React, Mantine). The AI can read these for product/domain questions and for language and library best practices instead of guessing. Session Replay work uses QUICK_REFERENCE and 01_CONTEXT from pulse-docs.

---

## What else improves accuracy and confidence

| What | Why it helps |
|------|------------------|
| **Reference implementations** | Point to real files (e.g. `src/screens/SessionReplay`, `src/utils/errorHandling.ts`). The AI can read them and match existing patterns instead of inventing. |
| **When unsure, state assumption** | Ask the AI to say “Assuming X; if not, do Y.” Reduces silent wrong guesses. |
| **Prefer conservative choice** | “When in doubt, keep in feature” / “when in doubt, don’t refactor existing code.” Fewer risky changes. |
| **Run lint after edits** | Build verification into the flow so the AI (or you) fixes new lint errors before concluding. |
| **Scoped rules by path** | Rules that apply only to `**/screens/**`, `**/services/**`, etc. keep context relevant and reduce noise. |
| **Anti-patterns** | Explicit “don’t do this” (no Remix, no `any`, no feature constants in global) cuts recurring mistakes. |
| **Short checklists** | e.g. “Constant used in 2+ features? → global : feature.” Makes placement decisions consistent. |
| **One source of truth** | AGENTS.md + .cursorrules; rules/skills reference them. Avoid duplicating long content in many files. |

---

## Summary

- **Division:** We already divide via scoped rules and a few skills/commands. One AGENTS.md and a short .cursorrules stub; full rules in **.mdc** and **reference implementations** so the right context is available when needed.
- **Accuracy/confidence:** Add **reference implementations**, **verification steps** (lint after edit), **“when unsure” guidance**, **anti-patterns**, and a couple of **extra commands** so behavior is consistent and the AI corrects itself (lint) before finishing.

---

## Cursor agent best practices (from Cursor)

[Cursor’s best practices for coding with agents](https://cursor.com/blog/agent-best-practices) align well with this setup:

| Practice | How we support it in pulse-ui |
|----------|-------------------------------|
| **Plan before coding** | Use **Plan Mode** (Shift+Tab in agent input) for non-trivial work; save plans to `.cursor/plans/` for docs and future agents. If the agent goes wrong, revert and refine the plan instead of long follow-up chains. |
| **Let the agent find context** | We don’t copy whole files into rules. Rules **point to** canonical examples (e.g. `src/screens/SessionReplay/`, `src/utils/errorHandling.ts`). Tag a file in the prompt only when you know the exact one. |
| **Rules = essentials only** | Rules focus on commands (`yarn lint`, `yarn format`), patterns (feature-first, no Remix, clean JSX), and **pointers** to reference implementations. We use the linter for style instead of duplicating style guides in rules. |
| **Skills for workflows** | Add-new-screen, api-service-pattern, verify-after-edit, session-replay-context are skills the agent can use when relevant. Add more only for repeated workflows. |
| **Commands for repeatable steps** | `.cursor/commands/COMMANDS.md` has prompts for “Add screen”, “Verify after edit”, “Match reference”, “Session Replay context”, etc. Add custom commands in Cursor and invoke with the prompt or `/` when available. |
| **Verifiable goals** | TypeScript (strict), lint, and “verify after edit” (run lint/format, list changed files) give the agent clear success criteria. |
| **Start new conversation when switching tasks** | When moving to a different feature or the agent keeps making the same mistake, start fresh. Use **@Past Chats** to pull in only needed context from previous work. |

**In short:** Be specific in prompts; reference files instead of pasting; run lint after edits; use plans for bigger tasks; review the agent’s changes.
