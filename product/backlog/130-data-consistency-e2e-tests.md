# Harden balance/history integrity with data-consistency E2E tests

- **ID:** 130-data-consistency-e2e-tests
- **Scope:** backend (tests only)
- **Size:** M (about a day)

## Why

Every money write in the app lands on account balances plus the append-only
`BalanceHistory` audit trail, and the risky spots are exactly the interleaved
ones: manual corrections between lock and unlock, repeated lock/unlock cycles,
accounts deleted while a budget referencing them is locked. Existing
integration tests cover single lock/unlock balance updates but not multi-cycle
divergence, interleaved manual edits, orphaned history, or per-budget history
linkage. Promoting sprint-5 Story 30 turns those integrity invariants into
regression protection over the couple's real data — the same data-safety move
item 100 made for the transfer algorithm.

## What

Add a `@SpringBootTest` + Testcontainers E2E test class (e.g.
`DataConsistencyE2ETest` in `src/test/java/.../integration/`) driving the real
REST endpoints, covering the five Story 30 scenarios. **No production code
changes are expected**; if a test surfaces a genuine defect, stop and report
it in the PR rather than silently fixing it in the same change (item 100's
contract).

## Acceptance criteria

- [ ] **No divergence:** after 10+ mixed operations on one account (manual
      updates interleaved with budget lock, a second lock, an unlock),
      `currentBalance` equals the most recent history entry's `balance`, and
      the history chain is coherent (each entry's balance = previous balance +
      changeAmount)
- [ ] **Deleted-account unlock:** lock a budget with savings to three
      accounts, delete one (allowed for locked-budget references), then
      unlock — assert the actual observable behavior end-to-end: remaining
      accounts restored correctly and no orphaned/inconsistent writes. If
      current behavior is a crash or inconsistency, that is a reportable
      finding, not something to fix here
- [ ] **Interleaved manual update:** account at 500, lock with 100 savings
      (→ 600), manual update to 700, unlock → balance is 600 (unlock subtracts
      only the automatic 100; the manual +100 correction survives), with the
      four-entry history sequence from Story 30 Test 3
- [ ] **Future-dated ordering:** history entries with future `changeDate`
      values stay ordered `changeDate DESC` across pagination boundaries
- [ ] **Budget linkage:** three sequentially locked budgets saving to the same
      account produce three AUTOMATIC entries each carrying its own
      `budgetId`; unlocking the newest reverses only its own entry
- [ ] Tests run green in `./mvnw test`; `BigDecimal` assertions use
      `compareTo` (`isEqualByComparingTo`), never `equals`; no new
      dependencies

## API changes (if backend)

None — tests only.

## Out of scope

- Changing production code (report defects, don't fix them here).
- The other sprint-5 stories (37/atomicity is the strongest later candidate,
  but needs failure injection and its own scoping).
- Any frontend change.

## Notes

- Original spec: `todo/backlog/sprint-5/30-data-consistency-e2e-tests.md` —
  move it to `todo/done/` as part of this item's bookkeeping (in addition to
  the usual `product/` move and STATE.md update), as item 100 did for
  Story 32.
- Reuse the harness and helpers of `BudgetIntegrationTest` /
  `TransferAlgorithmE2ETest`.
- Story 30 wording occasionally speculates about implementation ("logs
  warning") — assert observable API/database behavior instead, and document
  any interpretation in the PR, following item 100's precedent.
