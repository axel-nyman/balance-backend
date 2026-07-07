# Harden budget state-machine business rules with E2E tests

- **ID:** 160-state-machine-e2e-tests
- **Scope:** backend (tests only)
- **Size:** M (about a day)

## Why

The money-safety rules of the budget lifecycle — one unlocked budget at a
time, lock only at exactly zero balance, unlock only the most recent budget,
locked budgets immutable, account deletion blocked only by unlocked-budget
references — are enforced in scattered service checks and covered piecemeal
by feature tests. Promoting sprint-5 Story 33 pins them down as a single
regression suite over the couple's real data, the same move items 100
(transfer algorithm) and 130 (balance/history consistency) made. Unlike
Story 37 (atomicity), this needs no failure injection — every scenario is
drivable through the public API.

## What

Add a `StateMachineBusinessRulesE2ETest` (`@SpringBootTest` + Testcontainers,
reusing the `TransferAlgorithmE2ETest`/`DataConsistencyE2ETest` harness)
driving the real REST endpoints, covering the five Story 33 scenarios.
**No production code changes are expected**; if a test surfaces a genuine
defect (a real race window is the likeliest), stop and report it in the PR
rather than silently fixing it — items 100/130's contract.

## Acceptance criteria

- [ ] **One unlocked budget (race):** two concurrent `POST /api/budgets` for
      different months (real threads) end with exactly one 201 and exactly
      one unlocked budget in the database; after locking it, the second
      create succeeds. If both can succeed, that is a reportable finding
- [ ] **Exact-zero lock:** a budget off by 0.01 (e.g. 10.00 income vs 3.34 +
      6.67) is rejected with 400 and stays UNLOCKED with no todo list, no
      balance change, no history entries; balancing it makes lock succeed
- [ ] **Most-recent unlock across year boundary:** with locked budgets
      Oct–Dec 2024 and Jan–Feb 2025, unlocking Dec 2024 and Jan 2025 fails
      (400) while Feb 2025 succeeds — ordering is (year, month), not month
      alone
- [ ] **Locked budgets immutable everywhere:** on a locked budget, every
      mutating endpoint is rejected with 400 and changes nothing: the 9
      income/expenses/savings POST/PUT/DELETE routes, plus
      `PUT /api/budgets/{id}` (month/year edit, item 080) and
      `DELETE /api/budgets/{id}`
- [ ] **Account deletion by budget status:** an account referenced by an
      unlocked budget cannot be deleted (400); once that budget is locked and
      the account is referenced only by locked budgets, deletion succeeds
      (soft delete) and the locked budgets' data stays intact
- [ ] Suite green in `./mvnw test`; `BigDecimal` assertions via
      `isEqualByComparingTo`; threads/executors cleaned up; no new
      dependencies

## API changes (if backend)

None — tests only.

## Out of scope

- Changing production code (report defects, don't fix them here).
- Story 37 (transaction atomicity/rollback) — still a candidate, but it
  requires failure injection inside the Spring context and separate scoping.
- Load/perf-style concurrency (one clean two-thread race per rule is enough).

## Notes

- Original spec: `todo/backlog/sprint-5/33-state-machine-business-rules-e2e-tests.md`
  — move it to `todo/done/` in this item's bookkeeping, as items 100/130 did
  for Stories 32/30.
- Story 33 prescribes exact error strings and implementation details ("ORDER
  BY year DESC…"); assert observable API behavior (status codes, state via
  GET endpoints/repositories) and document interpretation in the PR, per the
  item 100/130 precedent.
- The lock-immutability sweep should enumerate the endpoints from the current
  controllers, not the story text — item 080 added a mutating route the 2025
  story predates.
