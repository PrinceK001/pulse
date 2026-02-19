# Tech stack practices (TypeScript, React Query, Mantine)

**Use this to align Pulse UI code with language and library best practices.** Sourced from official and community best practices (TypeScript, TanStack Query, Mantine).

---

## TypeScript paradigm

- **Strict mode:** Keep `strict: true` (and related options) in tsconfig. Catches more bugs and prevents accidental `any` inference.
- **No `any`:** Use proper types everywhere. For truly unknown values use `unknown` and narrow with type guards (e.g. `if (x instanceof Error)`, or custom type predicates).
  ```ts
  catch (error: unknown) {
    const message = error instanceof Error ? error.message : 'Something went wrong';
    // ...
  }
  ```
- **Prefer type inference:** Let TypeScript infer return types and variable types where obvious; add explicit types for public APIs, function parameters, and when inference is wrong or unclear.
- **Interfaces vs types:** Both are valid. Use `type` for object shapes and unions; use `interface` when you need declaration merging or extend contracts. Be consistent within a file.
- **Nullish coalescing:** Use `??` for default values (only `null`/`undefined` trigger the default). Avoid `||` for defaults when `0`, `''`, or `false` are valid values.
- **Narrowing:** Use type guards, `in`, `typeof`, `instanceof`, and discriminated unions instead of type assertions (`as`) when possible.

---

## TanStack Query (React Query)

- **useQuery for read:** Use for fetching and caching. Put `queryKey` and `queryFn` in hooks; call services from `src/services/` in the `queryFn`. Use `isLoading`/`isError`/`error`/`data` for UI.
- **useMutation for write:** Use for create/update/delete. In `onSuccess`, call `queryClient.invalidateQueries({ queryKey: [...] })` to refetch affected data. Prefer invalidation over manually updating the cache unless you have a good reason.
  ```ts
  useMutation({
    mutationFn: (payload) => SessionReplayService.create(payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['sessionReplay'] }),
  });
  ```
- **Query keys:** Use arrays, e.g. `['sessionReplay', filters]`. Keep keys consistent so invalidation (e.g. `invalidateQueries({ queryKey: ['sessionReplay'] })`) works across the feature.
- **Separation:** Keep UI logic (toast, redirect) and query logic (invalidate, refetch) clear. Handle success/error in mutation callbacks; show toasts from there or from the component using the mutation.

---

## React (components and hooks)

- **Presentational JSX:** Keep components focused on rendering; put data fetching, mutations, and business logic in custom hooks. Components receive data and callbacks as props.
- **One logical component per file:** Extract subcomponents when they’re reused or when the file gets long. Tiny helpers used only in one file can stay in the same file.
- **Hooks in separate files:** Custom hooks (useQuery, useMutation, derived state) live in `hooks/` under the feature; don’t define hooks inside component files.

---

## Zustand (Pulse UI)

- **When to use:** Cross-feature or app-wide UI state (filters, sidebar, modals). Server data → TanStack Query; local UI state in one screen → useState/useReducer; shared UI state → Zustand (`src/stores/`). Don’t put server-fetched data in Zustand; use TQ cache.

---

## Mantine (Pulse UI)

- **Theme:** Pulse UI uses a teal theme (see Session Replay QUICK_REFERENCE for primary teal, borders, gradients). Use theme tokens and Mantine’s color/radius props for consistency.
- **Components:** Use Mantine primitives (Button, Paper, Group, Stack, Table, Pagination, etc.) and `@tabler/icons-react` for icons. Use CSS modules (`*.module.css`) for screen-specific layout and overrides.
- **Accessibility:** Use semantic HTML and Mantine’s built-in a11y (e.g. labels, aria where needed). Error states should be actionable (e.g. Retry button), not only dismissible.

---

## References (external)

- TypeScript: [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/), strict mode and type narrowing.
- TanStack Query: [TanStack Query docs](https://tanstack.com/query/latest), [TkDodo blog – mutations](https://tkdodo.eu/blog/mastering-mutations-in-react-query) (invalidateQueries, mutation patterns).
- Mantine: [Mantine docs](https://mantine.dev/), component API and theming.
