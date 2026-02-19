# Pulse UI – Cursor commands

This folder documents **suggested Cursor custom commands** for working in pulse-ui. Use them to get consistent, project-aware behavior from the AI.

## How Cursor uses this folder

- **Rules**: `.cursor/rules/*.mdc` are loaded automatically when you work in pulse-ui (when pulse-ui is the workspace root or when Cursor picks up the project).
- **Skills**: `.cursor/skills/*/SKILL.md` are project skills the agent can use when the task matches (e.g. “add a new screen”, “fix lint”).
- **Commands**: Cursor does not read command definitions from this repo by default. The **COMMANDS.md** file lists suggested prompts you can add as **Custom Commands** in Cursor (see below).

## Adding custom commands in Cursor

1. Open **Cursor Settings** (e.g. `Cmd + ,`).
2. Search for **“Custom Commands”** or open **Rules / Commands**.
3. Add a new command: give it a **name** and a **prompt**. You can copy prompts from **COMMANDS.md** in this folder.
4. Optionally assign a keyboard shortcut.

When you run the command (e.g. from the command palette or via shortcut), Cursor will use that prompt with the current context (file, selection, etc.).

## Recommended setup for pulse-ui

- **Open pulse-ui as the workspace root** when doing frontend work so that:
  - `.cursorrules` and `AGENTS.md` apply.
  - `.cursor/rules/*.mdc` and `.cursor/skills/` are in scope.
- If you open the **parent repo** (pulse), Cursor may still use rules from the root; for pulse-ui–specific behavior, open the `pulse-ui` folder.

See **COMMANDS.md** for copy-paste command prompts. Useful ones: **Add new screen**, **Fix lint and format**, **Implement feature with data**, **Verify after edit**, **Match reference**, **Session Replay context** (load Session Replay product/UI docs), **Domain and tech context** (load Pulse + tech practices).
