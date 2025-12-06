# Story 30: Data Consistency & Integrity E2E Tests

**As a** developer
**I want to** verify data consistency across complex multi-step operations
**So that** balance history, account balances, and audit trails remain accurate over the application lifecycle

## Acceptance Criteria

- Balance history always matches current account balance after any sequence of operations
- No orphaned balance history entries exist when accounts are deleted
- Manual balance updates between lock/unlock cycles are handled correctly
- Balance history maintains correct chronological ordering even with future-dated entries
- Automatic balance history entries are correctly linked to their originating budgets

## Test Specifications

### Test 1: Balance History Divergence Detection

**Test Name:** `shouldDetectWhenCurrentBalanceDivergesFromBalanceHistoryAfterMultipleOperations`

**Description:** Verifies that after 10+ mixed manual and automatic balance updates, the account's currentBalance field always equals the balance value in the most recent history entry.

**Given:**
- Bank account created with initial balance $1000
- Perform sequence of operations:
  - 3 manual balance updates
  - Lock budget with savings to this account (automatic update)
  - 2 more manual updates
  - Lock second budget with savings (automatic update)
  - Unlock second budget (automatic reversal)
  - 2 final manual updates

**When:**
- Query account balance
- Query most recent balance history entry

**Then:**
- account.currentBalance == mostRecentHistory.balance
- All history entries form coherent audit trail
- No gaps or inconsistencies in balance progression

**Why:** Balance history could silently drift from actual balance through bugs in concurrent updates, transaction rollbacks, or calculation errors. This catches data corruption early.

---

### Test 2: Balance History Orphan Prevention

**Test Name:** `shouldPreventBalanceHistoryOrphansWhenUnlockingBudgetWithDeletedAccounts`

**Description:** Verifies the system gracefully handles unlocking budgets when referenced bank accounts have been deleted after locking.

**Given:**
- Create 3 bank accounts (A, B, C)
- Create budget with savings to all 3 accounts
- Lock budget (creates automatic balance history for all accounts)
- Budget is now locked, so accounts can be deleted
- Delete account B

**When:**
- Unlock the budget

**Then:**
- Budget unlocks successfully
- Accounts A and C balances are correctly restored
- No orphaned balance history entries for account B
- No exceptions thrown
- Unlock operation logs warning about missing account

**Why:** Real-world scenario where users clean up old accounts. System must handle gracefully without crashes or orphaned data.

---

### Test 3: Manual Update Between Lock/Unlock Cycles

**Test Name:** `shouldMaintainBalanceIntegrityWhenManualUpdateOccursBetweenLockAndUnlock`

**Description:** Tests that manual balance updates between locking and unlocking are preserved correctly and don't corrupt the unlock restoration logic.

**Given:**
- Account A starts with balance $500
- Lock budget with $100 savings to Account A → balance becomes $600
- Manually update Account A balance to $700 with comment "Bonus payment"

**When:**
- Unlock the budget

**Then:**
- Account A balance should be $600 (not $500)
  - Rationale: Unlock should subtract the $100 savings from $700 → $600
  - The manual +$200 update remains in effect
- Balance history shows:
  1. Initial: $500 (MANUAL)
  2. Budget lock: $600, +$100 (AUTOMATIC, budgetId set)
  3. Bonus payment: $700, +$100 (MANUAL)
  4. Budget unlock: $600, -$100 (AUTOMATIC reversal)

**Why:** Critical edge case. Users may receive income or make corrections while budget is locked. Unlock must correctly handle the interleaved manual updates.

---

### Test 4: Future-Dated Balance History Ordering

**Test Name:** `shouldHandleBalanceHistoryWithFutureDatesAndMaintainCorrectOrdering`

**Description:** Verifies balance history maintains correct ordering when entries have future dates mixed with current dates.

**Given:**
- Create account with initial balance
- Manually add balance history entry with date = tomorrow
- Manually add balance history entry with date = today
- Lock budget (creates entry with current timestamp)

**When:**
- Query balance history with pagination (newest first)

**Then:**
- Results ordered by changeDate DESC correctly
- Future-dated entry appears first
- Today's entry second
- Past entries follow
- Pagination boundaries respect date ordering

**Why:** Users might enter planned future transactions. System must handle time ordering edge cases without breaking queries or pagination.

---

### Test 5: Budget ID Linkage in Automatic History

**Test Name:** `shouldLinkBalanceHistoryEntriesToCorrectBudgetIdForAutomaticUpdates`

**Description:** Verifies that when multiple budgets affect the same account, each automatic balance history entry is correctly linked to its originating budget.

**Given:**
- Create Account A
- Create January budget with $100 savings to Account A
- Lock January budget
- Create February budget with $200 savings to Account A
- Lock February budget
- Create March budget with $150 savings to Account A
- Lock March budget

**When:**
- Query balance history for Account A

**Then:**
- Three AUTOMATIC entries exist
- Entry 1: changeAmount=$100, budgetId=January, source=AUTOMATIC
- Entry 2: changeAmount=$200, budgetId=February, source=AUTOMATIC
- Entry 3: changeAmount=$150, budgetId=March, source=AUTOMATIC
- Each budgetId correctly references its source budget
- Unlocking March removes only the March entry

**Why:** Budget linkage is critical for unlock operations. Wrong budgetId would cause unlocking to fail or reverse wrong amounts.

---

## Technical Implementation

1. **Test Class:** `BudgetSystemEndToEndTest` (or new `DataConsistencyE2ETest`)
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Extends standard integration test setup with Testcontainers

2. **Helper Methods Needed:**
   ```java
   private UUID createAccountWithBalance(String name, BigDecimal balance)
   private void manuallyUpdateBalance(UUID accountId, BigDecimal newBalance, String comment)
   private BalanceHistory getMostRecentBalanceHistory(UUID accountId)
   private List<BalanceHistory> getAllBalanceHistory(UUID accountId)
   private void verifyBalanceConsistency(UUID accountId)
   private void simulateAccountDeletion(UUID accountId)
   ```

3. **Verification Helpers:**
   ```java
   private void assertBalanceHistoryChainIsValid(List<BalanceHistory> history)
   private void assertNoOrphanedHistoryEntries()
   private BigDecimal calculateExpectedBalanceFromHistory(List<BalanceHistory> history)
   ```

4. **Test Data Builders:**
   - Use existing helper methods from other integration tests
   - Create reusable budget setup methods for complex scenarios

## Definition of Done

- All 5 test scenarios implemented and passing
- Tests use Given-When-Then structure with clear comments
- Helper methods created to reduce test boilerplate
- Tests run independently (no order dependencies)
- Tests clean up data properly in @BeforeEach
- Code coverage for edge cases documented
- Tests pass in CI environment with Testcontainers
- Performance acceptable (each test completes in < 5 seconds)
