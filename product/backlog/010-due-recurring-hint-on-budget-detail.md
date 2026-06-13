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
