# Savings goals — handle manual balance changes that affect allocations (full-stack)

- **ID:** 070d-savings-goals-balance-reallocation
- **Scope:** full-stack
- **Size:** M (about a day)

> **Part 4 of 5** of the savings-goals feature. Depends on **070a** (allocation
> ledger + history + invariant) and benefits from **070b** (UI to surface the
> prompt).

## Why

When the couple corrects an account's balance manually (the existing
update-balance flow) and that account backs one or more goals, the earmarked
amounts can drift from reality in **both** directions:

- A balance **drop** can leave more money "saved" than actually exists.
- A balance **rise** is often money that belongs to a goal — e.g. interest
  landing in a house-deposit fund — and the couple will usually want to earmark
  it in one step rather than re-opening the goal afterwards.

The app should keep allocations truthful on a decrease, and make it effortless
to push an increase straight onto a goal, while never forcing it.

## What

Extend the manual balance update so it reacts to the size and direction of the
change relative to the account's current allocations. **Keep it as simple as
possible for the user — they can always adjust allocations by hand afterwards.**

### Balance decrease (or any change leaving the account over-allocated)

Deficit = `totalAllocated − newBalance` when positive.

- **Within slack** — `newBalance ≥ totalAllocated` (e.g. 50% allocated, balance
  drops 10%): **nothing happens** to any goal.
- **Single goal** — exactly one goal on the account and a deficit: reduce that
  goal's allocation by the deficit **automatically**, and inform the user.
- **Multiple goals** — two or more goals and a deficit: the user must **choose
  how to split** the reduction across those goals; the change isn't finalised
  until they do.

### Balance increase

An increase only grows unallocated money, so it never *requires* a change — but
the user is offered the chance to earmark it:

- **No goals** on the account: nothing to offer; just more unallocated money.
- **Single goal**: offer a single checkbox — "also add the increase to
  *‹goal›*?". The **default** is the clever part and keys off how earmarked the
  account already was: if it was **fully allocated** (≈100%) to that goal,
  default the checkbox **on** (the new money almost certainly belongs to it);
  if only **partly** allocated, default **off** (the increase is probably free
  money). Ticking it raises the allocation by the full increase.
- **Multiple goals**: more manual — show the increase and let the user
  optionally distribute some/all of it across the goals (inputs summing to ≤ the
  increase), defaulting to zero. No auto-split, since intent is ambiguous.

Increases are never blocking: leaving everything untouched is always valid.

## Acceptance criteria

- [ ] Balance increase with no reallocation requested, or a decrease that stays
      within slack: existing behaviour, no goal changes, no blocking prompt
      (deployed frontend unaffected)
- [ ] Single-goal **deficit**: the goal's allocation is reduced by exactly the
      deficit in the same transaction as the balance update; response tells the
      frontend what was auto-adjusted
- [ ] Multiple-goal **deficit** with no split provided: the update is rejected
      with a specific, machine-readable conflict (the goals, current allocations,
      and required total reduction) so the UI can prompt — no partial writes
- [ ] Re-submitting a deficit with a valid split (reductions summing to the
      deficit, none driving an allocation below zero) applies the balance change
      and the per-goal reductions atomically
- [ ] Balance **increase** with a requested allocation (single-goal checkbox or
      multi-goal split): the chosen amounts are added to the named goals'
      allocations in the same transaction; the requested total may not exceed the
      increase, and the 070a invariant still holds
- [ ] Every allocation change writes a `BALANCE_REALLOCATION` `GoalAllocationChange`
      row (070a history), so the change-over-time trail stays complete
- [ ] The 070a invariant (allocations ≤ balance) holds after every successful
      update
- [ ] Backend integration tests cover: decrease slack / single-goal deficit /
      multi-goal deficit without split / multi-goal deficit with split / invalid
      split, and increase with single-goal checkbox / multi-goal split / no
      reallocation
- [ ] Frontend: after a manual balance update, the user is informed of any
      automatic single-goal deficit adjustment; for a multi-goal deficit is shown
      a reallocation dialog to enter the split before the change completes; and
      for an increase on a goal-backed account is offered the checkbox
      (single goal) or distribution inputs (multiple) with the default-on/off
      rule above; tests cover these flows

## API changes

Keep `POST /api/bank-accounts/{id}/balance` backward compatible:

- The legacy request shape (no reallocation info) still works: increases and
  within-slack / single-goal-deficit decreases succeed; the response gains
  additive fields describing any automatic single-goal adjustment.
- A multi-goal **deficit** with no split returns a specific domain error (e.g.
  `AllocationReallocationRequiredException` → 409) carrying the conflict detail.
- An **optional** `reallocation` field on the request resolves both directions:
  a list of `{savingsGoalId, changeBy}` with **signed** amounts — negative to
  reduce (deficit split), positive to add (earmark an increase). Absent on the
  legacy path. The backend validates direction and totals against the balance
  change and the invariant, and applies everything atomically. Document the
  exact contract in the PR.

Keep the backend dumb-but-safe: it simply applies whatever valid split it's
given and enforces the invariant. The default-on/off "cleverness" for increases
lives in the **frontend** (it just pre-fills the checkbox / inputs); the backend
makes no assumptions.

## UI notes

- Manual balance update lives in the accounts area (update-balance modal +
  `use-accounts.ts` `useUpdateBalance`). After computing direction:
  - **Decrease, multi-goal deficit** → on the 409, open a reallocation dialog
    listing affected goals with inputs that must sum to the required reduction.
  - **Decrease, single-goal deficit** → succeed, then toast the auto-adjustment.
  - **Increase, single goal on account** → show a checkbox, pre-checked iff the
    account was ≈100% allocated to that goal before the change.
  - **Increase, multiple goals** → optional distribution inputs (sum ≤ increase),
    all defaulting to zero.
- Reuse RHF + Zod, shadcn `Dialog`, sv-SE formatting. Invalidate goals + the
  affected account queries after success.

## Backend notes

- Flow: `BankAccountController.updateBankAccountBalance` →
  `DomainService.updateBankAccountBalance` (~lines 146–202). Direction handling,
  deficit computation, and goal allocation changes belong in the domain layer,
  inside the existing `@Transactional`. MANUAL `BalanceHistory` writing is
  unchanged; goal changes additionally write `BALANCE_REALLOCATION` history.
- Allocations are a ledger over the balance (070a) — changing an allocation does
  **not** move money; it only changes what's earmarked. No balance double-count.

## Out of scope

- Predictions/visualizations (070e).
- Reallocation triggered by anything other than a manual balance update (lock
  changes are 070c's concern; archiving is 070a's).

## Notes

- This is subtle, money-adjacent logic — derive the test list from the cases
  above before implementing, and keep all writes inside one transaction so a
  rejected multi-goal deficit leaves nothing changed.
