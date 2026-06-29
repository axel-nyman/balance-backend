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

## Completion notes

- **Date:** 2026-06-29
- **PRs:** balance-backend (this branch, `feat: edit an unlocked budget's month/year`) + balance-frontend (`feat: edit budget month/year on the detail page`). Merge order: backend first.
- **Scope delivered:** `PUT /api/budgets/{id}` (additive) + an "Edit Month" modal on `/budgets/:id` for UNLOCKED budgets.

### Interpretation decisions

- **Temporal invariant.** I verified how creation enforces its rules
  (`DomainService.createBudget`): it checks only (a) year range 2000–2100,
  (b) no duplicate `(month, year)` among non-deleted budgets, and (c) no other
  UNLOCKED budget exists. Creation does **not** enforce any "must be later than
  every existing budget" ordering. The "only the most recent budget can be
  locked/unlocked" rule is evaluated *dynamically* at lock/unlock time against
  the current month/year, not stored as an invariant. Therefore `updateBudget`
  applies the same checks as creation **minus** the one-unlocked check: editing
  an already-UNLOCKED budget never changes its status, so there is still exactly
  one unlocked budget afterwards — the rule cannot be weakened. No extra
  ordering check is added; whichever budget is most-recent after an edit remains
  the one lock/unlock will accept. This is the minimal correct interpretation.
- **Duplicate status code.** The spec text says "409", but the existing
  duplicate-budget path (`DuplicateBudgetException`, used by creation) maps to
  **400** in `GlobalExceptionHandler`. The spec's own Notes section requires
  reusing *the existing duplicate-budget domain exception* for consistency with
  creation, so the endpoint returns **400** (not 409) with
  `{"error":"Budget already exists for this month"}` — matching create exactly.
  Reconciling create/update onto 409 would be a separate, breaking change and is
  out of scope here.
- **Locked rejection.** Editing a LOCKED budget throws `BudgetLockedException`
  ("Cannot modify locked budget") → 400, the same exception every other
  locked-budget mutation guard already uses.
- **Month names (frontend).** The spec asked for "sv-SE month names", but the
  existing wizard month step (`StepMonthYear`) — which the spec also says to
  mirror — uses English month labels. To stay consistent with the existing UX
  the modal mirrors the wizard's English labels. (App-wide month display via
  `formatMonthYear` already uses sv-SE; only the in-control picker labels are
  English, matching the wizard.)

### Tests

- Backend integration tests added to `BudgetIntegrationTest`: success (with
  line items preserved), locked-rejection, duplicate (400), no-op idempotent,
  not-found, and invalid-month bean-validation.
- Frontend: `EditBudgetMonthModal.test.tsx` (render, prefill, PUT save + close,
  duplicate guard disables Save) and `BudgetDetailPage.test.tsx` (Edit-Month
  action shown for UNLOCKED, hidden for LOCKED).

### Verification status / known limitation

- Frontend fully verified green: `npm run lint` (0 errors), `tsc --noEmit`,
  `npm test -- --run` (577 passed), `npm run build`.
- **Backend integration tests could not be executed in this run:** Docker Hub's
  blob CDN (`production.cloudfront.docker.com`) returned `403 Forbidden` for
  every image pull in this session (postgres:15-alpine, testcontainers/ryuk,
  even alpine:latest), so Testcontainers could not start a database. The
  backend compiles cleanly (`./mvnw test-compile` succeeds). The backend PR is
  opened as a **draft** for this reason; the suite should be run by CI / the
  maintainer where the registry is reachable.
