# Savings goals — link budget savings items and allocate on lock (backend)

- **ID:** 070c1-savings-goals-budget-linking-backend
- **Scope:** backend
- **Size:** M (about a day)

> **Part 3 of 5** of the savings-goals feature, **backend half**. Depends on
> **070a** (entity + allocation ledger) and the existing lock/unlock flow.
> Originally specced as full-stack `070c`; split on implementation because the
> backend is independently mergeable/deployable and the frontend half hard-
> depends on **070b**'s `use-goals.ts` hooks (still in an unmerged PR). The
> frontend selector is now **`070c2`** (in the backlog). This file covers the
> backend (data model + lock/unlock allocation), which only hard-depends on
> 070a.

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

**Completed:** 2026-06-24 · **PR:** balance-backend (branch `claude/youthful-hamilton-mbyj65`)

Backend half of the savings-goals budget linking. All backend acceptance
criteria met; full suite green (**357 tests, +12** in a new
`BudgetGoalLinkingIntegrationTest`).

### Split decision
Original `070c` was full-stack. The frontend "goal" selector hard-depends on
**070b**'s `use-goals.ts` hooks, which are still in an unmerged PR
(balance-frontend#23), so it could not be built this run without coupling to an
in-review branch. Per the routine's full-stack guidance (backend first,
independently mergeable) the item was split: this backend part (`070c1`) ships
now; the frontend selector is **`070c2`** in the backlog, to be done once 070b
merges. The deployed frontend keeps working unchanged because every new field
is optional.

### What shipped
- Flyway `V6__add_savings_goal_to_budget_savings.sql` — additive nullable
  `savings_goal_id` column on `budget_savings` (FK → `savings_goals(id)`, index).
  Backward compatible: existing rows are NULL; the deployed image starts against
  the new schema.
- `BudgetSavings` entity gains `savingsGoalId`; create/update request DTOs and
  the `BudgetSavingsResponse` / budget-detail `savings[]` carry it (additive).
- Add/update savings endpoints accept an optional `savingsGoalId`. A non-null
  value is validated: missing goal → 404 (`SavingsGoalNotFoundException`),
  archived goal → 400 (`SavingsGoalArchivedException`). Omitting it preserves
  today's behaviour, so the live frontend is unaffected.
- **Lock** (`lockBudget`): after the existing savings→balance credit, a new
  `allocateLinkedGoalsForBudget` step increases each linked goal's
  `GoalAllocation` on the savings item's account by the saved amount and writes
  a `BUDGET_LOCK` `GoalAllocationChange` row. Runs inside the existing lock
  `@Transactional`.
- **Unlock** (`unlockBudget`): `reverseLinkedGoalsForBudget` runs *before* the
  balance reversal, reducing each linked goal's allocation by the saved amount
  (writing the reversing `BUDGET_LOCK` ledger row), restoring pre-lock state.

### Interpretation decisions
- **Ordering / invariant (the spec's "decide and document" point):** on lock the
  savings amount is first credited to the account balance (existing Story-26
  behaviour) and *then* allocated to the goal on that same account, so the
  per-account unallocated invariant (`sum(active allocations) ≤ currentBalance`)
  holds — the credit exactly backs the new allocation. Because credit and
  allocation are the same amount on the same account, a normal lock can never
  over-allocate; the `applyAllocation` invariant guard remains as a safety net
  and, being inside the lock transaction, rolls the whole lock back atomically
  if it ever triggers. On unlock the reversal runs before the balance is
  debited, so the invariant stays valid at every step.
- **Aggregation:** multiple savings items pointing at the same (goal, account)
  are summed into a single allocation change and a single `BUDGET_LOCK` ledger
  row, mirroring how lock already aggregates per-account balance history. The
  spec's "per change" is read as "per net (goal, account) change".
- **Reversal is delta-based, from current state** (mirrors the existing
  `reverseBalanceChanges`, which subtracts the recorded delta rather than
  snapshotting). It reverses precisely when nothing else touched the allocation
  in between; if a manual allocation existed first, unlock restores exactly to
  that pre-lock amount (covered by a test), and reductions clamp at zero.
- **Archived/deleted goals are skipped on lock** (an archived goal holds no
  allocations) so an interim archive never blocks the monthly lock; the savings
  still credits the balance as before. On unlock there is then nothing to
  reverse (the allocation is already gone), which is handled gracefully.
- The link is validated at **set time** (add/update savings) so a budget can
  only reference an existing, active goal; later archiving is the only way to
  reach a stale link, handled by the skip above.

### Limitations / follow-ups (not in 070c1 scope)
- Frontend selector is `070c2` (blocked on 070b merging).
- `GoalAllocationChange` rows are not tagged with `budgetId`; reversal is
  recomputed from the (immutable-while-locked) budget savings instead. Adequate
  here and consistent with the balance-history reversal; revisit only if
  multi-budget allocation auditing is ever needed.

### Verification
- Docker daemon started manually; `postgres:15-alpine` pulled via `mirror.gcr.io`
  and retagged (Docker Hub blob egress is blocked in this environment), tests
  run with `TESTCONTAINERS_RYUK_DISABLED=true` (ryuk image likewise blocked) —
  same environment workaround documented in 070a.
- `TESTCONTAINERS_RYUK_DISABLED=true ./mvnw test` →
  `Tests run: 357, Failures: 0, Errors: 0` · BUILD SUCCESS.
