# Edit a budget's month/year

- **ID:** 030-edit-budget-month-year
- **Scope:** full-stack
- **Size:** M

## Why

Pick the wrong month in wizard step 1 and today the only fix is deleting the
whole draft and rebuilding it. Moving a draft budget to another month should
be one small edit.

## What

Allow changing the month/year of an UNLOCKED budget.

- **Backend:** add `PUT /api/budgets/{id}` accepting `{month, year}` with the
  same validation as create (month 1–12, sane year, no duplicate month/year
  among non-deleted budgets) plus: reject if the budget is LOCKED. Specific
  domain exceptions handled by `GlobalExceptionHandler`, consistent with the
  existing error style.
- **Frontend:** an edit affordance on the unlocked budget detail page (e.g.
  pencil next to the title) opening a small modal with a month/year picker
  mirroring wizard step 1 validation; explicit save; budgets list and detail
  queries invalidated.

## Acceptance criteria

Backend:
- [ ] `PUT /api/budgets/{id}` updates month/year of an UNLOCKED budget
- [ ] Rejections with the existing error shape: duplicate month/year, locked
      budget, unknown budget, invalid month/year
- [ ] Integration tests cover the happy path and every rejection
- [ ] Purely additive — the currently deployed frontend keeps working

Frontend:
- [ ] Edit modal on the unlocked detail page; affordance absent when LOCKED
- [ ] Duplicate-month error surfaced via the existing error-message mapping
- [ ] Component tests for validation and the happy path

Process:
- [ ] Backend PR independently mergeable; frontend PR cross-references it and
      states "merge backend first"

## Notes

Check how lock validation defines "most recent" — moving the draft to a past
month may make it unlockable until moved forward again; that is acceptable,
but the implementer should confirm the behavior is coherent and note it in
the PR.

## Out of scope

- Editing month/year of LOCKED budgets (unlock first)
