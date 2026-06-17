# Savings goals — handle manual balance changes that affect allocations (full-stack)

- **ID:** 070d-savings-goals-balance-reallocation
- **Scope:** full-stack
- **Size:** M (about a day)

> **Part 4 of 5** of the savings-goals feature. Depends on **070a** (allocation
> ledger + invariant) and benefits from **070b** (UI to surface the prompt).

## Why

When the couple corrects an account's balance manually (the existing
update-balance flow) and that account backs one or more goals, the earmarked
amounts can become inconsistent with reality — e.g. the balance drops below
what's allocated. The app must keep allocations truthful and let the user decide
how to absorb a shortfall, rather than silently showing money as "saved" that
no longer exists.

## What

Extend the manual balance update so that, when the new balance would leave an
account **over-allocated** (`totalAllocated > newBalance`), the deficit is
resolved by these rules (from the idea):

- **Within slack** — if the account still covers all allocations after the
  change (`newBalance ≥ totalAllocated`, e.g. 50% allocated and balance dropped
  10%): **nothing happens** to any goal.
- **Single goal** — exactly one goal allocated on the account: reduce that
  goal's allocation by the deficit **automatically**, and inform the user it
  happened.
- **Multiple goals** — two or more goals allocated and a deficit exists: the
  user must **choose how to split** the reduction across those goals; the change
  isn't finalised until they do. (Increasing a balance never needs any of this —
  it only grows unallocated money.)

## Acceptance criteria

- [ ] Balance increase, or a decrease that stays within slack: existing
      behaviour, no goal changes, no extra prompt (deployed frontend unaffected)
- [ ] Single-goal deficit: the goal's allocation is reduced by exactly the
      deficit in the same transaction as the balance update; response tells the
      frontend what was auto-adjusted
- [ ] Multiple-goal deficit with no split provided: the update is rejected with
      a specific, machine-readable conflict (the goals, current allocations, and
      required total reduction) so the UI can prompt — no partial writes
- [ ] Re-submitting with a valid split (amounts summing to the deficit, none
      driving an allocation below zero) applies the balance change and the
      per-goal reductions atomically
- [ ] The 070a invariant (allocations ≤ balance) holds after every successful
      update
- [ ] Backend integration tests cover all four cases (slack / single / multi
      without split / multi with split) plus invalid splits
- [ ] Frontend: after a manual balance update, the user is informed of any
      automatic single-goal adjustment, and for the multi-goal case is shown a
      reallocation dialog to enter the split before the change completes; tests
      cover both flows

## API changes

Keep `POST /api/bank-accounts/{id}/balance` backward compatible:

- No-deficit and single-goal cases succeed with the existing request shape; the
  response gains additive fields describing any auto-adjustment.
- Multi-goal deficit returns a specific domain error (e.g.
  `AllocationReallocationRequiredException` → 409) carrying the conflict detail.
  Resolution uses an **optional** `reallocation` field on the request (a list of
  `{savingsGoalId, reduceBy}`) — absent on the legacy path, present only when
  resolving a multi-goal conflict. Document the exact contract in the PR.

## UI notes

- Manual balance update lives in the accounts area (update-balance modal +
  `use-accounts.ts` `useUpdateBalance`). On a 409 conflict, open a reallocation
  dialog listing the affected goals with inputs that must sum to the required
  reduction; on success show a toast summarising single-goal auto-adjustments.
- Reuse RHF + Zod, shadcn `Dialog`, sv-SE formatting. Invalidate goals + the
  affected account queries after success.

## Backend notes

- Flow: `BankAccountController.updateBankAccountBalance` →
  `DomainService.updateBankAccountBalance` (~lines 146–202). The deficit
  computation and goal reductions belong in the domain layer, inside the
  existing `@Transactional`. MANUAL `BalanceHistory` writing is unchanged.
- Allocations are a ledger over the balance (070a) — reducing an allocation does
  **not** move money; it only changes what's earmarked. No balance double-count.

## Out of scope

- Predictions/visualizations (070e).
- Reallocation triggered by anything other than a manual balance update (lock
  changes are 070c's concern).

## Notes

- This is subtle, money-adjacent logic — derive the test list from the four
  cases above before implementing, and keep all writes inside one transaction so
  a rejected multi-goal case leaves nothing changed.
