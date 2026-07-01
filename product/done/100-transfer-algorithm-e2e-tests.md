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

## Completion notes

- **Date:** 2026-07-01
- **PR:** balance-backend (draft — see verification note below)
- **Scope delivered:** New `TransferAlgorithmE2ETest`
  (`src/test/java/org/example/axelnyman/main/integration/`), a `@SpringBootTest`
  + Testcontainers + MockMvc suite that drives the real
  `POST /api/budgets → add income/expenses/savings → PUT /{id}/lock` path and
  reads the generated todo list back through `GET /{id}/todo-list`, asserting on
  the TRANSFER items. **No production code was changed** — no defect surfaced.
- **Tests (all five Story 32 cases):**
  1. `shouldNeverGenerateCircularTransfersInComplexMultiAccountScenarios` — 6
     accounts (A/B +200 each; C/D −150; E/F −50). Asserts no self-transfers,
     acyclic + no cancelling pair, conservation, and ≤ n−1 transfers.
  2. `shouldNeverGenerateSelfTransfersFromAccountToItself` — net-zero account A,
     B +400, C −400. Asserts exactly one transfer B→C 400 and that A appears in
     no transfer.
  3. `shouldOptimizeTransfersToMinimumCountWithComplexMultiAccountWeb` — 8
     distinct-net accounts. Asserts a valid plan of ≤ 7 transfers and full
     conservation (never one-per-deficit when a big surplus covers several).
  4. `shouldHandleTransferCalculationWhenAllAccountsAreDeficit` — all-deficit
     set cannot be locked (lock rejects unbalanced budgets), so it exercises
     `TransferCalculationUtils.calculateTransfers` directly and asserts an empty
     plan with no exception. This is the only direct-util case, matching the
     original Story 32.
  5. `shouldCalculateCorrectTransfersWhenOneAccountDominatesAllActivity` —
     dominant-hub fixture asserted **exactly**: 4 transfers, all from the main
     account, A→Bills 2000 / A→Rent 3000 / A→Groceries 1500 / A→Gas 1500,
     total 8000.
- **Interpretation — the "conservation" criterion.** The criterion is worded
  partly as "every account's post-lock balance equals its pre-lock balance plus
  its planned net position." The app does **not** work that way by design:
  `DomainService.updateBalancesForBudget` credits each account only by the
  *savings* set aside on it; the transfers are guidance the couple executes
  manually against their real bank, not an automatic balance mutation. Asserting
  a literal post-lock balance would contradict the app's actual (correct)
  behaviour, so the tests assert the substantive, testable property instead: the
  transfer set exactly rebalances every account — for each account
  `(transferred out − transferred in) == planned net position` — which summed
  over all accounts is zero, i.e. "total moved out == total moved in." Documented
  in the test class Javadoc as well.
- **Assertions use `BigDecimal.compareTo`** (`isEqualByComparingTo`) throughout,
  never `equals`. No new runtime or test dependencies.
- **Verification / why the PR is a draft:** `./mvnw test-compile` succeeds
  (exit 0). The integration suite could **not** be executed in the routine's
  remote environment: every Docker image pull returns `403 Forbidden` from Docker
  Hub's blob CDN (`production.cloudfront.docker.com`) — an egress-policy block on
  `postgres:15-alpine` and `testcontainers/ryuk`, so Testcontainers cannot start
  a database (`ContainerFetchException`). This is the same environment limitation
  that made item 080's backend PR (#63) a draft; GitHub CI *can* reach the
  registry (that PR's `test` check passed there), so CI will execute these tests
  on the PR. Opened as a draft so a green CI run gates the merge.
