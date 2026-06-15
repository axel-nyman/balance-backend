# Show "due but not included" recurring expenses on the unlocked budget detail page

- **ID:** 010-due-recurring-hint-on-budget-detail
- **Scope:** frontend
- **Size:** M

## Why

The wizard already surfaces due recurring templates (grouped on top, with an
"add all due" button). But once a budget exists, the detail page gives no hint
that a due template is missing — for example a template created or edited
after the budget was drafted, or an expense row that was deleted. Forgetting a
quarterly bill is exactly the kind of mistake Balance exists to prevent, and
the lock step is the last chance to catch it.

## What

On `/budgets/:id` for an UNLOCKED budget, show a hint (card or banner near the
expenses section) listing recurring templates that are due for this budget's
month but not linked from any expense row in it. Each entry offers one-click
"add" that creates an expense row the same way the wizard does. The hint never
appears on LOCKED budgets or when nothing is missing.

## Acceptance criteria

- [ ] Hint appears only for UNLOCKED budgets with ≥ 1 due-and-missing template
- [ ] "Due" means the template's `dueMonth`/`dueYear` equals the budget's
      month/year **or is earlier** (overdue) — compared in budget-month terms,
      never against the wall clock
- [ ] One-click add creates an expense row shaped like the wizard's recurring
      add: name, amount, default account if set, `isManual`, and the
      `recurringExpenseId` link (via the existing add-expense mutation)
- [ ] Entries disappear from the hint as they get added; the hint disappears
      when empty
- [ ] Component tests cover: shown/hidden logic, the add flow, and the locked
      state
- [ ] Reuses existing UI patterns (shadcn/ui, sv-SE currency formatting)

## UI notes

Existing building blocks: the due-grouping logic in
`src/components/wizard/steps/StepExpenses.tsx` (note: it currently uses
month/year *equality* — this spec includes overdue too),
`useRecurringExpenses()`, and `useAddExpense(budgetId)`. Place it above or
inside the expenses `BudgetSection` on `BudgetDetailPage`.

## Out of scope

- Changing the wizard's due semantics
- A warning on the wizard review step (possible future item)
- Backend changes (all needed data is already in `GET /api/recurring-expenses`)

## Completion notes

- **Date:** 2026-06-15
- **Repos / PRs:** balance-frontend (feature) — branch
  `claude/peaceful-hamilton-a3iie1`; balance-backend (this bookkeeping) —
  branch `claude/youthful-hamilton-a3iie1`. Frontend-only feature; no backend
  code changed.
- **What shipped:** A `DueRecurringHint` banner on `/budgets/:id` (above the
  Expenses `BudgetSection`) that lists recurring templates due for the budget's
  month — **or earlier (overdue)** — that aren't linked from any expense row.
  Each entry has a one-click **Add**.

### Interpretation decisions

- **"Due" comparison** is done purely in budget-month terms using
  `monthYearToNumber(dueMonth, dueYear) <= monthYearToNumber(budget.month,
  budget.year)` — never against the wall clock, as the spec requires. Templates
  with `dueMonth`/`dueYear` null (never used) are excluded.
- **"Missing"** means no budget expense row carries that template's id in
  `recurringExpenseId`. Because `useAddExpense` invalidates the budget detail
  query, an added entry disappears from the hint automatically on refetch.
- **One-click add shape** matches the wizard's recurring quick-add: `name`,
  `amount`, default `bankAccountId`, `isManual`, and the `recurringExpenseId`
  link, via the existing `useAddExpense(budgetId)` mutation.
- **Templates with no default account:** the backend requires a non-null
  `bankAccountId`, so a blind one-click POST would 400. For these the Add button
  instead opens the existing `ExpenseItemModal` prefilled (name/amount/isManual
  + the `recurringExpenseId` link preserved) so the user picks an account and
  saves through the same mutation. This keeps due-but-account-less templates
  visible (not silently hidden) while staying correct. `ExpenseItemModal` gained
  an optional `prefill` prop to support this; the existing add/edit flows are
  unchanged.
- **Visibility:** the hint renders nothing on LOCKED budgets and nothing when
  the due-and-missing set is empty.

### Tests / verification (frontend)

- New `DueRecurringHint.test.tsx` (7 tests): shown for due-in-month, shown for
  overdue, hidden for future-due, hidden when already linked, hidden when
  locked, one-click add posts the wizard-shaped body, and no-default-account
  opens the modal.
- Full suite green: `npm run lint` (0 errors), `npx tsc --noEmit`,
  `npm test -- --run` (54 files, 497 tests passing), `npm run build` all pass.

### Deviations / cut

- Nothing cut from the acceptance criteria. The only judgement call is the
  no-default-account handling above (modal instead of direct add).
