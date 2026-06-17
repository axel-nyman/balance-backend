# Savings goals — backend foundation (entity, allocations, CRUD)

- **ID:** 070a-savings-goals-backend-foundation
- **Scope:** backend
- **Size:** M (about a day)

> **Part 1 of 5** of the "savings goals" feature (idea 5 from item 015). The
> set is sequenced: **070a (this)** backend foundation → 070b goals pages
> (frontend) → 070c budget-savings↔goal linking (full-stack) → 070d manual
> balance reallocation (full-stack) → 070e progress & predictions (frontend).
> Each part is independently mergeable and deployable; the live app must keep
> working after each.

## Why

The couple wants to track which money in their bank accounts is already
"spoken for" by a savings goal (e.g. a trip, a buffer) versus still free to
allocate. The foundation is a goals concept and an allocation ledger that
records how much of each account's balance belongs to each goal, so the rest
of the feature (pages, budget linking, predictions) can build on it.

## What

Add a `SavingsGoal` entity and a `GoalAllocation` ledger linking goals to bank
accounts with an allocated amount, following the existing strict 3-layer
architecture. Expose CRUD plus manual allocation endpoints. Crucially,
**allocations never change `BankAccount.currentBalance`** — they are a separate
ledger describing how the existing balance is earmarked. "Unallocated" money on
an account = `currentBalance − sum(active allocations on that account)`.

Relationships: a goal can span several accounts; an account can back several
goals (many-to-many via the `GoalAllocation` join carrying an `amount`).

Goal lifecycle: `ACTIVE` → `ARCHIVED`. Archiving removes the goal's allocations
(frees the money) without touching balances. "Completed" is **derived**
(allocated ≥ target), not a stored status — money is not auto-unallocated on
completion (the user archives when ready). Soft-delete via `deletedAt` as
elsewhere.

## Acceptance criteria

- [ ] `SavingsGoal`: `id`, `name` (required), optional `targetAmount`
      (`NUMERIC(19,2)`), optional `endDate` (date), `status` (`ACTIVE|ARCHIVED`),
      `createdAt`/`updatedAt`/`deletedAt`, JPA auditing + soft delete like
      `BankAccount`
- [ ] `GoalAllocation`: `id`, `savingsGoalId`, `bankAccountId`, `amount`
      (`NUMERIC(19,2)`, > 0), audit timestamps; at most one active allocation
      per `(goal, account)` pair (adjust amount rather than duplicate)
- [ ] Invariant enforced: an account's total active allocations may never
      exceed its `currentBalance` (reject the request with a specific domain
      exception, e.g. `InsufficientUnallocatedFundsException` → 400/409)
- [ ] `POST /api/savings-goals` creates a goal and optionally seeds initial
      allocations from named accounts' **unallocated** money (each respecting
      the invariant)
- [ ] `GET /api/savings-goals` returns active goals with per-goal summary:
      total allocated, `targetAmount`, derived progress, and the backing
      accounts; archived goals excluded by default
- [ ] `GET /api/savings-goals/{id}` returns the goal with its per-account
      allocation breakdown
- [ ] `PUT /api/savings-goals/{id}` edits `name`, `targetAmount`, `endDate`
- [ ] `POST /api/savings-goals/{id}/allocations` manually assigns/adjusts an
      amount from a given account (respecting the invariant); supports reducing
      to zero (removes the allocation)
- [ ] `POST /api/savings-goals/{id}/archive` sets status `ARCHIVED` and removes
      that goal's allocations (frees money) — **balances unchanged**
- [ ] A way to read per-account unallocated/allocated figures (either additive
      fields on the existing `BankAccountResponse` **or** a dedicated endpoint —
      pick one, see API changes)
- [ ] Flyway `V5__...` migration creates the two tables (backward compatible;
      currently deployed backend must still start against the new schema)
- [ ] Testcontainers integration tests cover: create (with/without seed
      allocations), the invariant rejection, manual allocate/adjust/zero,
      archive frees money without changing balances, list excludes archived,
      and unallocated computation

## API changes

New, additive endpoints under `/api/savings-goals` (no existing endpoint
changes, so the deployed frontend keeps working). DTOs in
`domain/dtos/SavingsGoalDtos.java`, mappers in
`domain/extensions/SavingsGoalExtensions.java`.

For unallocated figures, **prefer additive fields** on `BankAccountResponse`
(e.g. `allocatedAmount`, `unallocatedAmount`) since adding response fields is
backward compatible and the accounts page will want them anyway — but a
dedicated `GET /api/bank-accounts/{id}/allocations` is acceptable. Record the
choice in the PR.

## Out of scope

- Linking budget savings items to goals and the lock/unlock allocation (070c).
- Manual balance-change reallocation handling (070d).
- Predictions / velocity / visualizations (070e).
- Any frontend (070b).

## Notes

- Follow the BankAccount end-to-end template: entity (`domain/model/`),
  repository (`infrastructure/data/context/`), DTOs (`domain/dtos/`), extensions
  (`domain/extensions/`), `IDataService`/`DataService`,
  `IDomainService`/`DomainService`, controller (`api/endpoints/`), specific
  exceptions via `GlobalExceptionHandler`. `BigDecimal` for all money.
- Latest migration is `V4`; this adds `V5`.
- Keep allocations strictly a ledger over existing balances — never mutate
  `currentBalance` here. This keeps the foundation data-safe and makes 070d's
  reallocation logic the *only* place balances and allocations interact.
