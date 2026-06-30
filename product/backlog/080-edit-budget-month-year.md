# Edit a budget's month/year after creation

- **ID:** 080-edit-budget-month-year
- **Scope:** full-stack
- **Size:** M (about a day)

## Why

If the couple picks the wrong month in the wizard (easy to do near a
month boundary), there is no way to fix it: month/year is fixed at creation.
The only recourse today is to delete the whole budget — losing every income,
expense, and savings line — and rebuild it from scratch. Letting an UNLOCKED
budget's month/year be corrected in place removes a sharp, data-losing edge
from the monthly routine.

## What

Add the ability to change an UNLOCKED budget's month and year after creation,
keeping all its line items. On `/budgets/:id` (UNLOCKED only) expose an
"edit month" action that opens a modal to pick a new month/year, validated the
same way the wizard's month step is. LOCKED budgets stay immutable. The change
must respect the existing invariants: month/year is unique among non-deleted
budgets, and only the most recent budget may be the unlocked working draft.

## Acceptance criteria

- [ ] `PUT /api/budgets/{id}` updates month and/or year of an **UNLOCKED**
      budget and returns the updated `BudgetResponse`; all income/expense/
      savings lines are preserved unchanged
- [ ] Attempting to edit a **LOCKED** budget returns a domain error (no change)
- [ ] Changing to a (month, year) that already belongs to another non-deleted
      budget returns the same duplicate error used by budget creation (409),
      with no change persisted
- [ ] The "one unlocked budget at a time / only the latest budget is the
      working draft" rule is not weakened — if editing the month/year would
      violate the temporal invariant the app enforces at creation, it is
      rejected with a clear error (interpretation recorded in the PR)
- [ ] No-op edits (same month/year) succeed idempotently
- [ ] Frontend: an "edit month" action on `/budgets/:id` for UNLOCKED budgets
      opens a modal (RHF + Zod) mirroring the wizard's month step; LOCKED
      budgets show no such action
- [ ] Backend integration tests cover success, locked-rejection, duplicate
      (409), and no-op; frontend component tests cover shown/hidden-by-status
      and the happy-path save

## API changes (backend)

New `PUT /api/budgets/{id}` accepting `{ "month": int, "year": int }`
(both 1–12 / sane year range, validated in the DTO). Returns the standard
`BudgetResponse`. Purely additive — the currently deployed frontend never
calls it, so existing behavior is unchanged. The endpoint must **not** touch
status, `lockedAt`, line items, balances, todos, or allocations — it only
edits the month/year fields. STATE.md's API-surface note ("There is no
`PUT /api/budgets/{id}` … not yet specced") is resolved by this item.

## UI notes (frontend)

Reuse the wizard month-step component/validation (`src/components/wizard/`),
the existing modal + React Hook Form + Zod pattern, and the budget mutation
hooks in `src/hooks/` (add a `useUpdateBudget`/`useEditBudgetMonth` mutation
that invalidates the budget detail + list query keys). sv-SE month names.

## Out of scope

- Editing month/year of a LOCKED budget (must unlock first)
- Bulk re-dating or merging budgets
- Any change to line items, balances, or the lock/unlock flow

## Notes

- Backend: `BudgetController` (`api/endpoints/BudgetController.java`) has
  GET/DELETE/lock/unlock on `/{id}` but no PUT. The `Budget` entity already
  has `setMonth`/`setYear` and a unique constraint on
  `(month, year, deleted_at)` (`domain/model/Budget.java:12`), so the duplicate
  path is enforced at the DB level too — surface it as the existing
  duplicate-budget domain exception, not a raw constraint error.
- Verify how creation enforces "latest budget only / one unlocked at a time"
  (DomainService) and apply the *same* rule here; record the exact
  interpretation in the PR. This is the main design risk — keep it minimal.
