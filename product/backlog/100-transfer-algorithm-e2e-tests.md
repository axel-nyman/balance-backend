# Harden the lock-time transfer algorithm with correctness E2E tests

- **ID:** 100-transfer-algorithm-e2e-tests
- **Scope:** backend (tests only)
- **Size:** M (about a day)

## Why

Locking a budget computes the minimal set of inter-account transfers to cover
each account's planned net position (the greedy `TransferCalculationUtils`) and
then *applies those transfers to real account balances*. This is the most
safety-critical write in the app against the couple's real money. It has unit
coverage, but no end-to-end test proves that the transfers a real lock produces
are self-consistent, balance-preserving, and free of nonsense edges
(self-transfers, cycles). Promoting sprint-5 Story 32 into a focused E2E suite
turns the data-safety mandate ("correctness beats feature count") into
regression protection before any future change touches the lock flow.

## What

Add a `@SpringBootTest` + Testcontainers E2E test class that drives the real
`POST budget → add lines → PUT /lock` path and asserts the transfer plan and
the resulting balances are correct, covering the Story 32 cases. No production
code changes are expected; if a test surfaces a genuine bug, stop and report it
in the PR rather than silently "fixing" the algorithm in the same change.

## Acceptance criteria

- [ ] **No self-transfers:** no generated TRANSFER todo item has the same
      from-account and to-account
- [ ] **No trivial cycles:** the transfer set does not contain a pair that
      cancels (A→B and B→A for the same amount); the greedy result is acyclic
- [ ] **Conservation:** total money moved out equals total moved in; every
      account's post-lock balance equals its pre-lock balance plus its planned
      net position (income to it − expenses/savings from it)
- [ ] **Transfer-count minimality:** for a known fixture the number of transfers
      equals the expected minimum (n accounts with non-zero net → ≤ n−1
      transfers); a documented hub/all-deficit fixture is asserted exactly
- [ ] **Edge fixtures:** all-deficit accounts and a dominant-hub pattern (from
      Story 32) each produce a valid, conservation-respecting plan
- [ ] Tests run green in the existing `./mvnw test` suite with no new runtime
      dependencies; assertions use `BigDecimal` comparisons (`compareTo`), never
      `equals`

## Out of scope

- Changing `TransferCalculationUtils` or the lock/unlock flow (tests only) —
  unless a test proves a real defect, which is reported, not fixed here
- The other nine sprint-5 stories (promote separately if wanted)
- Any frontend change

## Notes

- Source under test: `domain/utils/TransferCalculationUtils.java` (existing unit
  test: `src/test/java/.../domain/utils/TransferCalculationUtilsTest.java`) and
  the lock path in the budget `DomainService`.
- Original spec: `todo/backlog/sprint-5/32-transfer-algorithm-e2e-tests.md`
  (5 tests). Reuse the integration-test harness/fixtures other budget
  integration tests already use (`BudgetIntegrationTest`).
- Move `todo/backlog/sprint-5/32-*.md` to `todo/done/` as part of this item's
  bookkeeping (in addition to the usual `product/` → `done/` move and STATE.md
  update), since it promotes that sprint-5 story.
