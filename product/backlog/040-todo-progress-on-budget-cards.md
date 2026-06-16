# Show todo-list progress on locked budget cards

- **ID:** 040-todo-progress-on-budget-cards
- **Scope:** full-stack
- **Size:** S

## Why

During the "execute" phase of the month the couple works through a locked
budget's todo list (transfers + payments). The `/budgets` grid shows each
budget's status and totals, but gives no sense of how far along the month's
tasks are — you must open each budget's todo page to find out. A small "X of Y
done" on the card turns the budget grid into an at-a-glance monthly progress
view, reinforcing the routine Balance is built around.

## What

On the `/budgets` grid, each **LOCKED** budget card shows its todo progress
(completed vs total items), e.g. a compact "5 / 8 done" label and/or a thin
progress bar. UNLOCKED budgets show nothing new (they have no todo list). The
backend already computes these counts for the todo page; expose them on the
budget list response so the grid needs no per-card extra fetch.

## Acceptance criteria

- [ ] `GET /api/budgets` includes, for each LOCKED budget, a todo summary with
      total and completed counts; UNLOCKED budgets carry null/absent summary
- [ ] The added field is optional/additive — the currently deployed frontend
      keeps working unchanged (no field removed or renamed)
- [ ] No N+1: counts come from an aggregate query (or batch), not one query per
      budget
- [ ] Frontend: LOCKED budget cards render "completed / total done" using the
      new field; the count matches the budget's todo page
- [ ] A locked budget with 0 completed shows "0 / N"; all-complete shows
      "N / N" (and visually reads as done)
- [ ] UNLOCKED cards are visually unchanged
- [ ] Tests: backend asserts the summary is present for locked and
      null/absent for unlocked; frontend asserts the label renders from the
      field and is hidden for unlocked

## API changes (if backend)

Additive field on the existing `BudgetResponse` (used by both `GET /api/budgets`
and `GET /api/budgets/{id}`):

```
BudgetResponse {
  ... existing fields ...,
  TodoProgress todoProgress   // null for UNLOCKED budgets
}
TodoProgress { int totalItems, int completedItems }
```

Reuse the existing todo counting logic (`TodoSummaryResponse` already exposes
`totalItems`/`completedItems`). Populate it when mapping budgets in the list
path; prefer a single grouped count query over per-budget lookups. Adding a
nullable field to a JSON response is backward compatible for the deployed
frontend (Jackson ignores unknown/extra fields; the new field is simply unread
by old clients).

## UI notes (if frontend)

- Reuse the existing budget card component on the `/budgets` grid and the
  shadcn/ui primitives already in use; a minimal text label is enough, an
  optional thin progress bar (existing Progress component if present) is a plus.
- Keep sv-SE conventions; this is counts, not currency.
- Gate rendering on `status === "LOCKED" && todoProgress != null`.

## Out of scope

- Per-item breakdown on the card (that's the todo page's job)
- Any change to the todo page itself or to optimistic checkbox behavior
- Charts/visualizations beyond a simple count/bar (reports are a non-goal)

## Notes

- Backend touch points: `BudgetDtos.BudgetResponse`, `BudgetExtensions`
  (mapping), `DomainService` budget-list path, a repository count query for
  todo items grouped by budget. Confirm whether todo items are reachable via an
  aggregate query to avoid N+1.
- Frontend touch points: the budgets grid/card component and its types in
  `src/api`.
- No migration needed.
