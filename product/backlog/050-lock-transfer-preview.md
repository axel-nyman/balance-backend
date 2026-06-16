# Preview the computed transfers before locking a budget

- **ID:** 050-lock-transfer-preview
- **Scope:** full-stack
- **Size:** M

## Why

Locking is the pivotal, semi-committing step: it computes the minimal set of
inter-account transfers, generates the todo list, and moves real money between
account balances. Today the couple can't see what transfers the lock will
produce until after it has already happened (then they'd have to unlock to
change anything). A read-only preview of the planned transfers before
confirming builds trust in the greedy algorithm and catches "wait, that's the
wrong account" mistakes while they're still cheap to fix.

## What

Before locking an UNLOCKED budget, the couple can see the exact transfers the
lock would create — "move 2 500 kr from Checking to Savings", etc. — computed
without any side effects, and then confirm or cancel. The preview uses the same
calculation the lock uses, so what they see is what they get. It changes no
balances, creates no todo list, and stamps no recurring templates.

## Acceptance criteria

- [ ] A new read-only endpoint returns the planned transfers for an UNLOCKED
      budget using the same logic as lock (`TransferCalculationUtils`), with
      **zero** persistence side effects (no balance change, no todo list, no
      recurring stamps)
- [ ] Each transfer entry identifies the from-account, to-account, and amount
- [ ] Calling it on a LOCKED budget returns the already-applied transfers, or a
      clear 409 — pick one and document it (simplest consistent interpretation)
- [ ] A budget whose plan needs no transfers returns an empty list (not an error)
- [ ] Frontend: the lock action first opens a confirmation modal listing the
      previewed transfers (sv-SE / SEK formatted), with Confirm (proceeds to the
      existing lock call) and Cancel
- [ ] If the preview is empty, the modal says so plainly and still allows
      confirming the lock
- [ ] Tests: backend asserts the preview matches what lock would do and that no
      state changed after calling it; frontend asserts the modal lists transfers
      and that Confirm triggers the lock mutation

## API changes (if backend)

Additive, non-breaking read endpoint:

```
GET /api/budgets/{id}/transfer-preview
200 -> { List<TransferPreview> transfers }   TransferPreview { fromAccount{id,name}, toAccount{id,name}, BigDecimal amount }
404 -> not found
(409 if LOCKED, if that interpretation is chosen)
```

Reuse `TransferCalculationUtils.calculateTransfers(...)` exactly as the lock
path does; the domain method must be a pure read (no `@Transactional` write, no
saves). Map `TransferPlan` to a response DTO via an extension. Do not refactor
the lock flow itself beyond extracting the shared calculation call if needed
(note any such extraction in the PR rather than expanding scope).

## UI notes (if frontend)

- Reuse the existing confirmation-modal pattern and shadcn/ui dialog; this
  replaces the current immediate lock action with a preview-then-confirm step.
- Fetch the preview with a React Query query (not a mutation) keyed by budget
  id; format amounts with the existing sv-SE / SEK helper.
- On Confirm, call the existing lock mutation; keep its current success/error
  toasts and navigation to the todo page.

## Out of scope

- Editing/overriding the computed transfers (the algorithm is authoritative)
- Any change to the lock algorithm or to unlock
- Showing balance-after-lock projections per account (possible future item)

## Notes

- Backend touch points: `BudgetController` (new `@GetMapping`),
  `DomainService` (a side-effect-free preview method calling the same util),
  `TransferCalculationUtils` (unchanged), a `TransferPlan → DTO` extension.
- Strongest test is a "no state changed" assertion: snapshot balances / todo
  existence before and after calling the preview and assert equality.
- No migration needed.
