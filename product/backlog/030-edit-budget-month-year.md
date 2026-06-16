# Edit an unlocked budget's month and year after creation

- **ID:** 030-edit-budget-month-year
- **Scope:** full-stack
- **Size:** M

## Why

If the couple picks the wrong month (or year) when starting a budget, today the
only fix is to delete the whole draft and rebuild every income, expense, and
savings row from scratch. That is exactly the kind of avoidable, error-prone
rework Balance exists to remove. STATE.md flags this gap directly: "There is no
`PUT /api/budgets/{id}` — month/year is not editable after creation (backlog
item 030)."

## What

Allow changing the **month and/or year of an UNLOCKED budget** in place,
keeping all of its line items. A LOCKED budget stays immutable (month/year
included). The new month/year must still be unique among non-deleted budgets,
using the same rule that budget creation enforces today. Nothing about the
lock/unlock flow, transfer calculation, or line items changes — only the
budget's period.

## Acceptance criteria

- [ ] `PUT /api/budgets/{id}` updates month and/or year of an UNLOCKED budget
      and returns the updated `BudgetResponse` (200)
- [ ] Attempting it on a LOCKED budget is rejected (409, existing locked-budget
      exception path) with no change
- [ ] A month/year that collides with another non-deleted budget is rejected
      with the same duplicate error that `POST /api/budgets` uses (no new error
      semantics)
- [ ] Setting month/year to the budget's current values is a no-op success (not
      treated as a duplicate against itself)
- [ ] Month is validated 1–12 and year is required, matching `CreateBudgetRequest`
- [ ] Line items (income/expenses/savings) and totals are untouched by the edit
- [ ] Frontend: on `/budgets/:id` for an UNLOCKED budget, an "Edit period"
      action opens a modal (month + year) that saves through a new
      `useUpdateBudget` mutation and invalidates the budget queries
- [ ] The edit action is absent on LOCKED budgets

## API changes (if backend)

New endpoint, additive and non-breaking (the deployed frontend never calls it):

```
PUT /api/budgets/{id}
Body: UpdateBudgetRequest { Integer month (1–12, required), Integer year (required) }
200 -> BudgetResponse
409 -> budget is locked
409/400 -> duplicate month/year (reuse existing DuplicateBudget* exception)
404 -> not found
```

Add `UpdateBudgetRequest` to `BudgetDtos`, a `updateBudget` method through
`IDomainService`/`DomainService` (business rules: locked check, duplicate check
that excludes the budget's own id) and `IDataService`/`DataService` (existence
query that excludes a given id). Reuse the existing duplicate/locked exceptions
rather than inventing new ones.

## UI notes (if frontend)

- Reuse the modal + React Hook Form + Zod pattern already used by the budget
  wizard's month step; a simple month dropdown (sv-SE month names) + year input.
- Add `useUpdateBudget(id)` next to the existing budget hooks; invalidate the
  budget detail and budget list query keys on success; surface errors via the
  existing toast pattern.
- Place the trigger near the existing lock/unlock/delete actions on
  `BudgetDetailPage`, visible only when `status === "UNLOCKED"`.

## Out of scope

- Editing month/year of LOCKED budgets (must unlock first — keeps the
  lock invariant intact)
- Any change to line items, transfers, or the wizard
- Reordering/“move budget” semantics beyond the uniqueness check

## Notes

- Backend touch points: `BudgetController` (`@PutMapping("/{id}")`),
  `DomainService` (budget creation duplicate/locked logic to mirror),
  `DataService` (uniqueness query — add an "exists by month/year excluding id"
  variant), `BudgetDtos`.
- The uniqueness rule is "unique month+year among non-deleted budgets"; the
  self-exclusion is the only subtlety. Cover it with an explicit test.
- No migration needed — schema is unchanged.
