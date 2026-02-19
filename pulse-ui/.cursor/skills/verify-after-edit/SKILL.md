---
name: verify-after-edit
description: After making code changes in Pulse UI, run lint and format and fix new issues; list changed files. Use when the user asks to verify edits, run lint, or ensure changes don't introduce new errors.
---

# Verify after edit (Pulse UI)

Use this skill **after** you’ve made code changes to improve correctness and confidence.

## Steps

1. **Run lint** from the pulse-ui directory:
   ```bash
   cd pulse-ui && yarn lint
   ```
   (If the workspace root is already pulse-ui, use `yarn lint`.)

2. **Fix any new errors** in the files you changed. Prefer `yarn lint:fix` for auto-fixable issues, then fix the rest by hand.

3. **Run format** so style is consistent:
   ```bash
   yarn format
   ```

4. **Re-run lint** to confirm no new violations:
   ```bash
   yarn lint
   ```

5. **List the files you changed** so the user can review (e.g. in a short bullet list). If you introduced new errors that you fixed, say so briefly.

## When to use

- After adding or editing components, screens, services, or hooks.
- When the user says “verify,” “run lint,” or “make sure nothing is broken.”
- Optionally after any non-trivial edit to catch regressions early.

## Note

Only fix **new** issues introduced by your changes. Pre-existing lint errors in other files can be left unless the user asked to fix them. If the project has no `yarn format`, skip step 3.
