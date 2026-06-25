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

## Completion notes

**Completed:** 2026-06-25 · **PRs:** balance-backend (branch
`claude/youthful-hamilton-wbbqb0`) + balance-frontend (branch
`claude/peaceful-hamilton-wbbqb0`). Merge order: **backend first**.

Backend full suite green (360 tests, +15 new in
`BudgetSavingsGoalLinkIntegrationTest`). Frontend green: lint, `tsc`, 535
Vitest tests (+5 new on `SavingsItemModal`), and build.

### What shipped
- **Backend:** `BudgetSavings` gains a nullable `savingsGoalId` (Flyway
  `V6__add_savings_goal_id_to_budget_savings.sql` — additive nullable column +
  FK to `savings_goals` + index). The add/update savings endpoints accept an
  optional `savingsGoalId` (validated to reference an **active** goal — 404 if
  missing, 400 if archived); `BudgetSavingsResponse` and the budget-detail
  savings response carry it back. On **lock** each goal-linked savings line
  earmarks its amount toward the goal on its account (summed per goal+account,
  reusing the 070a `applyAllocation` so the invariant + `BUDGET_LOCK`
  `GoalAllocationChange` ledger row come for free); on **unlock** that exact
  contribution is reversed.
- **Frontend:** the budget-detail savings add/edit modal
  (`SavingsItemModal`) gets an optional **Goal** selector (None / pick an
  active goal); the savings section sublabel shows the linked goal name
  (`account · goal`). Omitting a goal sends no `savingsGoalId`, so existing
  behaviour is unchanged.

### Interpretation decisions
- **Ordering (the documented invariant rule).** On lock the goal allocation
  step runs **after** `updateBalancesForBudget`, so the savings money has
  already credited the account; the new earmark is therefore always backed by
  the just-added balance and the per-account invariant
  (`Σ allocations ≤ currentBalance`) holds by construction. The
  `InsufficientUnallocatedFundsException` check inside `applyAllocation` is kept
  as a safety net — if it ever fired it would roll back the whole lock (the lock
  is one `@Transactional`). Unlock reverses in the inverse order
  (de-allocate, then restore balances).
- **Reversal is delta-based and tolerant.** Unlock subtracts exactly each
  linked line's amount from the current allocation, clamped at zero, and skips
  allocations already gone. So a manual allocation made while the budget was
  locked survives unlock, and a goal archived while locked is not resurrected.
- **Archived/deleted goals are skipped on lock** (they no longer accept
  allocations); the lock still succeeds. Linking is validated against active
  goals at add/update time.
- **Multiple savings lines to the same goal+account are aggregated** into one
  allocation change per lock/unlock (one ledger row each), not one per line.

### Deferred (not blocking the acceptance criteria)
- The **budget wizard** savings step does not yet expose the goal selector —
  the create-budget flow would need the wizard state + per-line plumbing
  extended. Goals can be linked by editing a savings line on the budget-detail
  page after the budget is created. The hard acceptance criterion (the
  budget-detail savings modal) is fully met. Worth a small follow-up item.
