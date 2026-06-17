# Savings goals — link budget savings items and allocate on lock (full-stack)

- **ID:** 070c-savings-goals-budget-linking
- **Scope:** full-stack
- **Size:** M (about a day)

> **Part 3 of 5** of the savings-goals feature. Depends on **070a** (entity +
> allocation ledger) and the existing lock/unlock flow. Best done after 070b so
> goals exist to link to, but only hard-depends on 070a.

## Why

The monthly routine already plans savings per account in the budget. Connecting
a budget's savings line to a goal means that when the budget locks, that
month's saving is automatically earmarked toward the goal — and unlocking
undoes it — so the goals stay accurate without separate manual bookkeeping.

## What

Let each budget **savings** item optionally reference one goal (0 or 1). On
**lock**, for every savings item that names a goal, increase that goal's
allocation on the savings item's account by the item's amount (extending the
existing lock transaction) and append a `BUDGET_LOCK` `GoalAllocationChange`
history row (070a). On **unlock**, reverse exactly that (allocations and their
history reversal). This must compose with the existing lock behaviour
(transfers, todo generation, AUTOMATIC balance history, recurring stamping) and
must preserve the lock/unlock invariant: unlock fully restores pre-lock state,
including goal allocations.

## Acceptance criteria

- [ ] `BudgetSavings` gains an optional `savingsGoalId` (nullable FK); Flyway
      `V{next}` migration is additive and backward compatible (deployed backend
      still starts; existing savings rows have null)
- [ ] Add/update savings endpoints accept an optional `savingsGoalId`; omitting
      it preserves today's behaviour (the deployed frontend keeps working)
- [ ] On lock, each savings item with a `savingsGoalId` adds its `amount` to
      that goal's `GoalAllocation` on the item's `bankAccountId` (creating or
      increasing the allocation), inside the existing lock `@Transactional`,
      writing a `BUDGET_LOCK` `GoalAllocationChange` history row (070a) per
      change; unlock writes the reversing history rows
- [ ] Allocation-on-lock still respects the unallocated invariant from 070a;
      locking surfaces a clear domain error if it would over-allocate an account
      (decide and document the rule — e.g. the lock already credits the account
      via savings balance updates, so ordering matters)
- [ ] On unlock, every allocation made by that lock is reversed precisely,
      leaving goal allocations exactly as before the lock
- [ ] Integration tests: lock allocates to goals, unlock reverses, lock with no
      linked goals behaves exactly as today, and the unallocated invariant holds
- [ ] Frontend: the budget savings add/edit modal gets an optional "goal"
      selector (none / pick a goal); selecting persists the link; the savings
      section shows the linked goal; tests cover the selector and that omitting
      it is unchanged

## API changes

Additive `savingsGoalId` on the budget-savings create/update request DTOs and
on `BudgetSavings` responses. No breaking changes — field is optional and
defaults to null.

## UI notes

- Frontend savings modal: `src/components/budget-detail/SavingsItemModal.tsx`
  and the wizard savings step; reuse the existing account-select pattern for a
  goal-select. Hooks: `use-budgets.ts` savings mutations; `use-goals.ts` (070b)
  for the goal list. sv-SE formatting throughout.
- Only allow linking goals on **UNLOCKED** budgets (editing is locked-out today).

## Backend notes

- Lock: `DomainService.lockBudget` (~lines 795–837) →
  `updateBalancesForBudget` / `generateTodoList`; add a goal-allocation step in
  the same transaction. Unlock: `DomainService.unlockBudget` (~lines 910–943) →
  add the reversal alongside `reverseBalanceChanges`/`restoreRecurringExpenses`.
- Carefully define ordering vs the existing savings→balance updates so the
  invariant (allocations ≤ balance) holds at commit. Document the interpretation
  in the PR.

## Out of scope

- Manual balance-change reallocation (070d).
- Predictions (070e).

## Notes

- This touches the most safety-critical flow (lock/unlock). Preserve the
  invariant that unlock restores pre-lock state **exactly** — now including goal
  allocations. No destructive migrations.
