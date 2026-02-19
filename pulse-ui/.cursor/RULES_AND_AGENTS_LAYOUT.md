# Where .cursorrules and AGENTS.md live (and whether to break them down)

## Short answers

| File        | Single file or folder? | Can move to .cursor/? | Break down? |
|------------|------------------------|----------------------|-------------|
| **.cursorrules** | Single file at **project root** (legacy). | **No.** Cursor looks for `.cursorrules` at the **pulse-ui root**. Moving it to `.cursor/.cursorrules` may stop Cursor from auto-loading it. | **Yes.** The modern approach is to put content in **`.cursor/rules/*.mdc`** and keep a short .cursorrules or remove it. |
| **AGENTS.md**    | Single file at **project root**.           | **Unclear.** Docs say "project root (or subdirectories)." Keeping it at root is safest for auto-load. | **Optional.** One file is a strong single entry point; splitting can fragment context. |

---

## How Cursor loads rules (order of precedence)

1. **Team rules** (Cursor Team/Enterprise)
2. **Project rules** – **`.cursor/rules/*.mdc`** (version-controlled, globs, always-apply)
3. **User rules** (global Cursor settings)
4. **Legacy rules** – **`.cursorrules`** at project root (single file)
5. **AGENTS.md** at project root

So: **`.cursor/rules/`** is the *modern* system; **`.cursorrules`** and **AGENTS.md** at root are still supported and loaded if present.

---

## Should we break them down?

### .cursorrules (2,500+ lines)

**Yes, breaking down is recommended** if you want:

- Only relevant rules in context (by glob), so the model sees less noise.
- Easier maintenance (edit one .mdc instead of a giant file).
- Alignment with Cursor’s modern rule system.

**How:** Turn sections of `.cursorrules` into separate **`.cursor/rules/*.mdc`** files, for example:

- `priorities-and-stack.mdc` (alwaysApply: true) – P0/P1/P2, stack, no Remix, reference docs
- `component-architecture.mdc` (globs: `**/*.tsx`) – Clean JSX, hooks, one component per file
- `data-and-routing.mdc` (globs: `src/screens/**/*`, `src/services/**/*`, `src/hooks/**/*`) – useQuery/useMutation, routes
- `typescript.mdc` (globs: `**/*.ts`, `**/*.tsx`) – strict, no any, types
- `api-client.mdc` (globs: `src/services/**/*`, `src/clients/**/*`) – API structure, env
- `feature-first.mdc` (already exists; can absorb “feature-first” section from .cursorrules)
- `design-system.mdc` (globs: `**/*.tsx`, `**/*.css`) – Mantine, theme, CSS modules

**Done for Pulse UI:** The former 2,500-line `.cursorrules` was migrated into multiple `.cursor/rules/*.mdc` files. The root `.cursorrules` is now a **short stub** (~50 lines) that states P0/P1/P2 and points to each rule file. The full legacy content is in git history if needed.

**Trade-off:** Many small .mdc files are easier to maintain and scope; the downside is the “full picture” is no longer in one place (mitigated by a short root .cursorrules or a README in `.cursor/rules/` that lists what each file covers).

### AGENTS.md (~100 lines)

**Recommendation: keep as a single file** for now.

- It’s already the single entry point for context, paths, reference implementations, and anti-patterns.
- HOW_TO_USE argues that splitting into many “agent” files fragments context.
- If it grows a lot later, you could add a short root AGENTS.md that links to `.cursor/AGENTS_*.md` sections (e.g. domain, conventions, references), but one file is fine at current size.

---

## Summary

- **Location:** Keep **`.cursorrules`** and **AGENTS.md** at **pulse-ui project root** so Cursor continues to load them. Don’t move them into `.cursor/` if you rely on default discovery.
- **Breakdown:**  
  - **.cursorrules** → Yes, migrate content into **`.cursor/rules/*.mdc`** and keep a tiny or no root .cursorrules.  
  - **AGENTS.md** → No need to break down; one file is enough.
