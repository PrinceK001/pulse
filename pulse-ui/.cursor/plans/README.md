# Plans (Plan Mode)

Use **Plan Mode** (Shift+Tab in the agent input) for non-trivial work. The agent will research the codebase, ask clarifying questions, and produce an implementation plan before writing code.

**Save plans here:** When the agent creates a plan, use "Save to workspace" to store it in `.cursor/plans/`. That gives you:

- Documentation for the team
- A way to resume interrupted work
- Context for future agents on the same feature

**If the agent goes wrong:** Revert the changes, refine the plan (or edit the saved plan), and run again. That’s often faster than fixing via long follow-up chains.

See [Cursor: Best practices for coding with agents](https://cursor.com/blog/agent-best-practices).
