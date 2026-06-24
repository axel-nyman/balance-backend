# Savings goals — backend foundation (entity, allocations, allocation history, CRUD)

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
of the feature (pages, budget linking, predictions) can build on it. It also
records **how allocations change over time** — the raw material for the later
progress/velocity features and possible future statistics.

## What

Add a `SavingsGoal` entity, a `GoalAllocation` ledger linking goals to bank
accounts with a current allocated amount, and an append-only
`GoalAllocationChange` history that records every change to those allocations
over time. Follow the existing strict 3-layer architecture. Expose CRUD plus
manual allocation endpoints.

Allocations are an **earmark over the existing balance**, not money movement:
`GoalAllocation.amount` records how much of an account's `currentBalance` is
spoken for, and "unallocated" money on an account =
`currentBalance − sum(active allocations on that account)`. With one explicit,
opt-in exception (archiving with "release to balance", below), allocation
operations in 070a **do not change `BankAccount.currentBalance`**.

Relationships: a goal can span several accounts; an account can back several
goals (many-to-many via the `GoalAllocation` join carrying an `amount`).

### Allocation history (over time)

Every time an allocation is created, increased, reduced, or removed — whether
by the manual endpoints here, by a budget lock (070c), or by balance
reallocation (070d) — append a `GoalAllocationChange` row capturing the goal,
account, the signed `changeAmount`, the resulting `amount` after the change, a
`timestamp`, and a `source` enum (`MANUAL | BUDGET_LOCK | BALANCE_REALLOCATION |
ARCHIVE`). This lets the app show, e.g., "+100 kr Mon, +100 kr Tue" rather than
just a single current figure, and gives 070e a real velocity source.

This history is **append-only and is preserved when a goal is archived** —
archiving removes the *active* `GoalAllocation` rows (and records that removal
as change events) but keeps the `GoalAllocationChange` trail, so historical
saving data survives for future statistical features.

### Goal lifecycle & archiving

`ACTIVE` → `ARCHIVED`. "Completed" is **derived** (allocated ≥ target), not a
stored status — money is not auto-unallocated on completion (the user archives
when ready). Soft-delete via `deletedAt` as elsewhere.

Archiving takes an explicit **`releaseToBalance`** choice (think of it as a
0/1 switch):

- **`false` (default)** — remove the goal's active allocations (free the
  earmark) **without touching balances**. The freed money simply becomes
  unallocated again. (This is the original idea: "archiving unallocates the
  funds.")
- **`true`** — the goal represented real money that's now been spent (a big
  planned expense paid). Remove the allocations **and** reduce each backing
  account's `currentBalance` by that goal's allocated amount on it, writing a
  `BalanceHistory` entry (source `AUTOMATIC`, comment referencing the goal) per
  affected account, all in one transaction. Net effect on each account:
  allocation and balance drop by the same amount, so unallocated money is
  unchanged and the invariant still holds.

In both cases the `GoalAllocationChange` trail records the removal.

## Acceptance criteria

- [ ] `SavingsGoal`: `id`, `name` (required), optional `targetAmount`
      (`NUMERIC(19,2)`), optional `endDate` (date), `status` (`ACTIVE|ARCHIVED`),
      `archivedAt` (nullable), `createdAt`/`updatedAt`/`deletedAt`, JPA auditing +
      soft delete like `BankAccount`
- [ ] `GoalAllocation`: `id`, `savingsGoalId`, `bankAccountId`, `amount`
      (`NUMERIC(19,2)`, > 0), audit timestamps; at most one active allocation
      per `(goal, account)` pair (adjust amount rather than duplicate)
- [ ] `GoalAllocationChange` (append-only history): `id`, `savingsGoalId`,
      `bankAccountId`, `changeAmount` (signed `NUMERIC(19,2)`), `resultingAmount`
      (`NUMERIC(19,2)`), `source` (`MANUAL|BUDGET_LOCK|BALANCE_REALLOCATION|
      ARCHIVE`), `createdAt`. Never updated or deleted; written on every
      allocation create/increase/reduce/remove
- [ ] Invariant enforced: an account's total active allocations may never
      exceed its `currentBalance` (reject with a specific domain exception, e.g.
      `InsufficientUnallocatedFundsException` → 400/409)
- [ ] `POST /api/savings-goals` creates a goal and optionally seeds initial
      allocations from named accounts' **unallocated** money (each respecting
      the invariant, each producing a `MANUAL` history row)
- [ ] `GET /api/savings-goals` returns active goals with per-goal summary:
      total allocated, `targetAmount`, derived progress, and the backing
      accounts; archived goals excluded by default
- [ ] `GET /api/savings-goals/{id}` returns the goal with its per-account
      allocation breakdown
- [ ] `GET /api/savings-goals/{id}/history` returns that goal's
      `GoalAllocationChange` rows (newest first) so the UI / 070e can show
      change-over-time; available for archived goals too
- [ ] `PUT /api/savings-goals/{id}` edits `name`, `targetAmount`, `endDate`
- [ ] `POST /api/savings-goals/{id}/allocations` manually assigns/adjusts an
      amount from a given account (respecting the invariant); supports reducing
      to zero (removes the allocation); writes a `MANUAL` history row
- [ ] `POST /api/savings-goals/{id}/archive` accepts `releaseToBalance`
      (boolean, default `false`): sets status `ARCHIVED`, removes the goal's
      allocations (records `ARCHIVE` history rows). When `true`, also reduces
      each backing account's `currentBalance` by the freed amount and writes an
      `AUTOMATIC` `BalanceHistory` entry per account — atomically; when `false`,
      **balances unchanged**
- [ ] A way to read per-account unallocated/allocated figures (either additive
      fields on the existing `BankAccountResponse` **or** a dedicated endpoint —
      pick one, see API changes)
- [ ] Flyway `V5__...` migration creates the three tables (backward compatible;
      currently deployed backend must still start against the new schema)
- [ ] Testcontainers integration tests cover: create (with/without seed
      allocations), the invariant rejection, manual allocate/adjust/zero, history
      rows written on each change, archive with `releaseToBalance=false` frees
      money without changing balances, archive with `releaseToBalance=true`
      reduces balances and writes balance history while preserving the invariant,
      history survives archiving, list excludes archived, and unallocated
      computation

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
- Predictions / velocity / visualizations (070e) — but **this item provides the
  `GoalAllocationChange` history those build on**.
- Any frontend (070b).

## Notes

- Follow the BankAccount end-to-end template: entity (`domain/model/`),
  repository (`infrastructure/data/context/`), DTOs (`domain/dtos/`), extensions
  (`domain/extensions/`), `IDataService`/`DataService`,
  `IDomainService`/`DomainService`, controller (`api/endpoints/`), specific
  exceptions via `GlobalExceptionHandler`. `BigDecimal` for all money.
- Latest migration is `V4`; this adds `V5`.
- Allocations are a ledger over existing balances. The **only** place 070a
  mutates `currentBalance` is archive-with-`releaseToBalance=true`, and it does
  so symmetrically (allocation and balance fall together). The lock flow (070c)
  and manual balance changes (070d) are the other balance↔allocation touch
  points and are deliberately isolated into their own specs so the lock/unlock
  invariant stays front-of-mind.
- The `GoalAllocationChange` design intentionally mirrors `BalanceHistory`
  (per-entity, append-only, `source` enum) so it's familiar and easy to query.

## Completion notes

**Completed:** 2026-06-24 · **PR:** balance-backend (branch `claude/youthful-hamilton-mvvsdd`)

Backend-only foundation for the savings-goals epic. All acceptance criteria met; full suite green (345 tests, +19 new in `SavingsGoalIntegrationTest`).

### What shipped
- Entities `SavingsGoal`, `GoalAllocation`, `GoalAllocationChange` (+ enums `GoalStatus`, `GoalAllocationChangeSource`), Flyway `V5__add_savings_goals.sql` (additive — three new tables, FKs, indexes, unique `(savings_goal_id, bank_account_id)`).
- 3-layer wiring: repositories, `SavingsGoalDtos`, `SavingsGoalExtensions`, `IDataService`/`DataService`, `IDomainService`/`DomainService`, `SavingsGoalController`.
- Endpoints: `POST /api/savings-goals` (optional seed allocations), `GET /api/savings-goals` (active only, with summary), `GET /{id}`, `GET /{id}/history` (newest first, archived included), `PUT /{id}`, `POST /{id}/allocations`, `POST /{id}/archive`.
- New exceptions via `GlobalExceptionHandler`: `SavingsGoalNotFoundException` (404), `SavingsGoalArchivedException` (400), `InsufficientUnallocatedFundsException` (409).

### Interpretation decisions
- **Unallocated figures:** chose the spec's preferred option — additive fields `allocatedAmount` / `unallocatedAmount` on `BankAccountResponse` (backward compatible; the accounts page wants them). The list endpoint computes them with one grouped sum query; create/update compute per-account.
- **Allocation amount is absolute (a set, not a delta).** `POST /{id}/allocations` sets the account's earmark to the given amount; `0` removes the allocation. Mirrors the "adjust rather than duplicate" rule. A no-op (unchanged amount) writes no history row.
- **`GoalAllocation` has no soft delete** — it is current-state; removal is a hard delete. The append-only `GoalAllocationChange` ledger is the history of record and survives archiving and removal.
- **Invariant** (`sum(active allocations on account) ≤ currentBalance`) enforced in `DomainService.applyAllocation` and returns **409 Conflict**.
- **Archive** rejects already-archived goals (400); `releaseToBalance=true` reduces each backing account's balance and writes an `AUTOMATIC` `BalanceHistory` row atomically; `false` leaves balances untouched. Both record `ARCHIVE` ledger rows. Empty/absent archive body defaults to `releaseToBalance=false`.
- Mutations (update/allocate/archive) are rejected on archived goals; `GET /{id}` and `/{id}/history` work for archived goals.

### Limitations / follow-ups (not in 070a scope)
- `getAllSavingsGoals` fetches allocations per goal (N+1). Dataset is a household's handful of goals; left simple. Batch later if it ever matters.
- Bank-account deletion is not yet allocation-aware (an account with active allocations can still be soft-deleted, orphaning allocation rows). Out of scope here; worth a guard in a later part.
- No allocation-count cap or end-date validation beyond `targetAmount > 0`.

### Verification
- Test image note: Docker Hub / ECR layer blobs (CloudFront) are blocked by the egress policy in this environment; pulled `postgres:15-alpine` via `mirror.gcr.io` and tagged it locally, and ran with `TESTCONTAINERS_RYUK_DISABLED=true` (ryuk image is likewise blocked). `./mvnw test` → `Tests run: 345, Failures: 0, Errors: 0` BUILD SUCCESS.
