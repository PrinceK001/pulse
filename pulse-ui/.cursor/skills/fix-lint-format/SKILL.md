---
name: fix-lint-format
description: Runs lint and format in Pulse UI and fixes reported issues. Use when the user asks to fix lint errors, run format, fix ESLint/Prettier, or clean up code quality issues in pulse-ui.
---

# Fix lint and format (Pulse UI)

Use this skill when fixing lint/format issues in the pulse-ui project.

## Commands (run from pulse-ui root)

```bash
# Format with Prettier
yarn format

# Lint with ESLint
yarn lint

# Lint and auto-fix where possible
yarn lint:fix
```

Run these from the **pulse-ui** directory (`pulse/pulse-ui`). If the workspace root is the parent repo, run: `cd pulse/pulse-ui && yarn lint:fix && yarn format`.

## Workflow

1. Run `yarn lint` (or `yarn lint:fix`) and read the output.
2. Fix remaining issues by hand if `lint:fix` cannot fix them (e.g. missing types, unused vars, rule violations).
3. Run `yarn format` so formatting is consistent.
4. Re-run `yarn lint` to confirm no new violations.

## Common fixes

- **Unused variables/imports**: Remove or use them; prefer removing if not needed.
- **Missing types**: Add types in `*.interface.ts` or inline where appropriate; no `any`.
- **Formatting**: Let Prettier fix (run `yarn format`); avoid manual style changes that conflict with Prettier.

## Project config

- ESLint config: project config (e.g. eslint-config-react-app or local `.eslintrc`).
- Prettier: `package.json` scripts `format` and `lint`/`lint:fix`.
