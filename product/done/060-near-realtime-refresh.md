# Keep two open sessions in sync with near-real-time auto-refresh

- **ID:** 060-near-realtime-refresh
- **Scope:** frontend
- **Size:** S (≤ half a day)

## Why

The couple often has Balance open on two devices at once (e.g. both phones
while sorting the month). Today a change made on one device isn't reflected on
the other until that device's tab is refocused or the page reloaded, so they
can see stale balances, budget items, or todo state. Keeping the views in sync
without a manual refresh removes a real source of "wait, did that save?"
confusion during the monthly routine.

## What

Make the app refresh server data on a background interval so a change made on
one device appears on the other within a short, bounded delay (target: a few
tens of seconds), using React Query's built-in polling — **no backend changes
and no new infrastructure**. Enable `refetchInterval` (and keep the existing
`refetchOnWindowFocus`) on the queries whose staleness is user-visible across
devices: the budget list/detail, accounts, recurring expenses, and the todo
list. Pause polling when the tab is hidden to avoid needless load.

This is deliberately the **simplest interpretation** of "real-time": polling
gives near-real-time sync with zero server work and no risk to data safety
(reads only). True push (SSE/WebSocket) is a possible later upgrade — see
out of scope.

## Acceptance criteria

- [ ] List/detail queries for budgets, accounts, recurring expenses, and the
      todo list refetch automatically on a bounded interval while their view is
      mounted and the tab is visible
- [ ] Polling pauses when the document is hidden and resumes on visibility
      (use React Query's `refetchIntervalInBackground: false`, the default, or
      explicit visibility handling)
- [ ] Optimistic todo checkbox updates are **not** disrupted by the new polling
      (no flicker/rollback of an in-flight optimistic toggle)
- [ ] The chosen interval is defined in one place (e.g. a constant next to the
      QueryClient config or in `src/lib/`), not scattered as magic numbers
- [ ] No change to mutation behaviour or to backend calls beyond the added GETs
- [ ] Tests cover that the configured queries carry a `refetchInterval` and
      that optimistic todo updates still behave (extend existing hook tests)

## UI notes

- QueryClient config: `src/App.tsx` (~lines 19–27) currently sets
  `staleTime: 60s`, `refetchOnWindowFocus: true`, `retry: 1`.
- Hooks to touch: `src/hooks/use-budgets.ts`, `use-accounts.ts`,
  `use-recurring-expenses.ts`, `use-todo.ts`. The todo hook already takes an
  options object with a custom `staleTime` (`TODO_STALE_TIME` in
  `src/lib/budget-lifecycle.ts`) — add the interval consistently there.
- Prefer per-query `refetchInterval` over a global one so non-data queries
  aren't polled; or a global default with opt-outs — record the choice in the PR.

## API changes (if backend)

None. Reads use existing endpoints.

## Out of scope

- **True push via SSE or WebSocket.** That would be a separate full-stack item
  (backend has no messaging/SSE infrastructure today — pure REST on Spring Boot
  3.4.x; adding `spring-boot-starter-websocket` or an `SseEmitter` change-feed
  endpoint plus a frontend `EventSource`/socket client). Propose it as a
  follow-up (e.g. `065-sse-change-feed`) if polling proves insufficient; do not
  build it here.
- Cross-device conflict resolution / presence indicators ("the other device is
  editing this").

## Notes

- Watch the cost on the Raspberry Pi: keep the interval generous (tens of
  seconds, not seconds) and visibility-gated so two idle tabs don't hammer the
  API.
- Data-safe by construction: this item only adds background GETs.

## Completion notes

- **Completed:** 2026-06-23
- **PRs:** balance-frontend `feat: poll user-visible queries for near-real-time
  cross-device sync` (branch `claude/peaceful-hamilton-dmkqk9`); balance-backend
  this `docs:` bookkeeping PR (branch `claude/youthful-hamilton-dmkqk9`).
- **Scope:** frontend-only feature + backend bookkeeping. No backend code or API
  changes — reads use existing endpoints.

### Interpretation decisions

- **Per-query `refetchInterval`, not a global default.** Added
  `refetchInterval: POLL_INTERVAL` to exactly the user-visible read queries —
  `useBudgets` (list), `useBudget` (detail), `useAccounts`,
  `useRecurringExpenses`, and `useTodoList` — rather than setting a global on the
  QueryClient. This keeps non-data / one-off queries (e.g. the balance-history
  infinite query behind the drawer) unpolled, matching the spec's preference.
- **Interval = 30s**, defined once as `POLL_INTERVAL` in
  `src/lib/query-config.ts`. Generous tens-of-seconds value per the Raspberry Pi
  cost note; two idle tabs poll five endpoints every 30s at most.
- **Tab-hidden pausing** relies on React Query's default
  `refetchIntervalInBackground: false` — polling stops when the document is
  hidden and resumes on visibility. Not set explicitly (it is the default);
  noted here so the behavior is intentional, not accidental.
- **Optimistic todo updates untouched.** The existing `onMutate`/`onError`/
  `onSettled` optimistic flow in `useUpdateTodoItem` is unchanged; `onMutate`
  still cancels in-flight queries, so a background poll cannot clobber an
  in-flight toggle. Existing optimistic tests still pass.

### Tests

- New `src/hooks/polling.test.tsx` asserts the budgets-list, budget-detail,
  accounts, and recurring-expenses queries each carry `refetchInterval ===
  POLL_INTERVAL`.
- Extended `src/hooks/use-todo.test.tsx` with the same assertion for the todo
  query, alongside the pre-existing optimistic-update tests.
- Full suite: `npm run lint` (warnings only, pre-existing), `npx tsc --noEmit`,
  `npm test -- --run` (514 passing), and `npm run build` all green. Note: the
  default parallel `vitest` run intermittently emits teardown "window is not
  defined" errors from an unrelated wizard animation timer
  (`useCopyAnimation`); this flake predates this change (the base branch fails
  the same way) and does not reproduce under
  `vitest run --no-file-parallelism`, which is fully green.

### Deviations / cut

- None. True push (SSE/WebSocket) remains out of scope as specified.
