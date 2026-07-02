# Settle account balances from the todo page

- **ID:** 120-todo-balance-settling
- **Scope:** frontend
- **Size:** S (≤ half a day)

## Why

During the Execute phase the couple works through the locked budget's todo
list, but checking off a PAYMENT item is purely a status flip — the account
balance the payment actually changed must be re-entered later from the
Accounts page, from memory. Transfers already get an "update balance" button
after completion, but it opens the modal with a synthesized account whose
`currentBalance` is set to the *transfer amount*, so the number shown is
wrong. Settling balances right where the work happens removes a whole
round-trip from the monthly routine.

## What

Extend and fix the post-completion "update balance" (wallet) affordance on the
todo page (`/budgets/:id/todo`):

- **PAYMENT items** get the same wallet button TRANSFER items already have,
  shown once the item is completed. It opens the existing
  `UpdateBalanceModal` for the item's **from-account**.
- The modal receives the **real account** (live `currentBalance` from the
  accounts query), not a synthesized object.
- The new-balance input is **pre-filled with a computed suggestion**, still
  fully editable before save:
  - payment → `fromAccount.currentBalance − item.amount`
  - transfer → `toAccount.currentBalance + item.amount`
- The comment field is pre-filled with the todo item's name (editable).
- Semantics unchanged: the checkbox toggle itself **never** mutates a balance;
  recording the balance stays an explicit, optional save.

## Acceptance criteria

- [ ] A completed PAYMENT item shows the update-balance affordance; a pending
      one does not (mirroring current TRANSFER behavior)
- [ ] The modal opens on the real account with its live balance; the suggested
      new balance is pre-filled (− amount for payments on the from-account,
      + amount for transfers on the to-account) and remains editable
- [ ] The comment is pre-filled with the item name and remains editable
- [ ] Toggling a todo checkbox alone never changes any account balance
- [ ] Existing TRANSFER flow keeps working, now with the correct live balance
      instead of the synthesized `currentBalance: item.amount` object
- [ ] Component tests cover affordance visibility per type/status and the
      prefill math for both item types

## API changes (if backend)

None. Uses the existing `POST /api/bank-accounts/{id}/balance` via the
existing modal.

## UI notes (if frontend)

- `src/components/todo/TodoItemList.tsx` — the synthesized `modalAccount`
  (lines 26–35) is replaced by a lookup from the accounts query (`useAccounts`);
  wire `onUpdateBalance` for payment rows too (currently transfer-only).
- `src/components/todo/TodoItemRow.tsx` — wallet button currently rendered
  only for completed transfers (~lines 73–83).
- `src/components/accounts/UpdateBalanceModal.tsx` — may need optional
  `initialBalance` / `initialComment` props; default behavior elsewhere
  (Accounts page) must not change.

## Out of scope

- Automatic balance deduction on checkbox toggle (explicit save stays).
- Any backend change; no link from `TodoItem` back to `BudgetExpense`
  (`deductedAt` stays unused — see Notes).
- Goal-allocation reconciliation beyond what `UpdateBalanceModal` already does
  (item 070d behavior comes for free by reusing the modal).

## Notes

- Backend confirmation that PAYMENT completion is a pure status flip:
  `DomainService.updateTodoItemStatus` (balance and expense untouched).
- `BudgetExpense.deductedAt` is captured in DTOs/wizard but read by nothing —
  a candidate for a future planned-vs-actual feature or removal; deliberately
  not touched here.
