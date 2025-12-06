# Story 37: Transaction Atomicity & Rollback E2E Tests

**As a** developer
**I want to** verify that complex operations are fully atomic with proper rollback
**So that** partial failures never leave the system in an inconsistent state

## Acceptance Criteria

- Budget lock operation fully rolls back if any step fails
- Budget unlock operation fully rolls back if any step fails
- System handles and recovers from corrupted balance history data
- Concurrent operations on same budget maintain data consistency

## Test Specifications

### Test 1: Lock Operation Rollback

**Test Name:** `shouldRollbackEntireLockOperationIfTodoGenerationFails`

**Description:** Verifies that if any part of budget lock fails (balance update, todo generation, recurring expense update), the entire operation rolls back atomically.

**Given:**
- Create budget with income, expenses, savings
- Budget is balanced and ready to lock
- Mock or simulate failure during todo generation phase (e.g., database constraint violation)

**When:**
- Attempt to lock budget
- Operation fails during todo list generation

**Then:**
- Budget status remains UNLOCKED
- lockedAt timestamp remains null
- No todo list created in database
- No balance history entries created (no automatic balance updates)
- No bank account balances changed
- No recurring expense lastUsedDate updated
- Transaction rolled back completely
- Error response indicates failure reason
- System remains in consistent pre-lock state

**How to simulate failure:**
```java
// Option 1: Use @Transactional test with savepoint and forced exception
// Option 2: Temporarily inject failing mock for TodoListRepository
// Option 3: Database constraint violation (duplicate todo item ID)
```

**Why:** Lock is complex multi-step operation (update status → generate todos → update balances → update recurring expenses). Partial completion would corrupt system state. Tests @Transactional works correctly across all steps.

---

### Test 2: Unlock Operation Rollback

**Test Name:** `shouldRollbackEntireUnlockOperationIfBalanceReversalFails`

**Description:** Verifies that if unlock operation fails during balance reversal, entire operation rolls back.

**Given:**
- Create and lock budget (creates balance history and updates accounts)
- Budget is locked with todo list and updated balances

**When:**
- Simulate failure during balance reversal phase:
  - Option 1: Manually corrupt one balance history entry (set invalid changeAmount)
  - Option 2: Temporarily drop foreign key constraint to cause error
  - Option 3: Mock account service to throw exception during balance update
- Attempt to unlock budget

**Then:**
- Budget status remains LOCKED
- lockedAt timestamp unchanged
- Todo list still exists (not deleted)
- Bank account balances unchanged (reversal didn't partially complete)
- Balance history entries remain (not deleted)
- Transaction rolled back completely
- Error response indicates failure
- System remains in consistent locked state

**Why:** Unlock reverses multiple operations. Partial unlock would leave accounts with wrong balances or orphaned data. Tests rollback mechanism works for complex undo operation.

---

### Test 3: Corrupted Balance History Handling

**Test Name:** `shouldHandleUnlockWhenBalanceHistoryIsInconsistentOrCorrupted`

**Description:** Tests graceful handling when balance history data has been corrupted or is inconsistent.

**Given:**
- Create budget, lock it (balance=$500 → $600, history shows +$100 change)
- Manually corrupt balance history:
  - Change history entry changeAmount from $100 to $200 (mismatch)
  - OR delete one history entry but not others
  - OR set budgetId to wrong value

**When:**
- Attempt to unlock budget

**Then:**
Option A (Graceful Degradation):
- Unlock proceeds but logs warnings about inconsistency
- Uses available data to reverse what it can
- Budget unlocks successfully
- Account balance may be slightly off (system tries best-effort recovery)

Option B (Strict Validation):
- Unlock fails with validation error
- Error message: "Balance history corrupted, manual intervention required"
- Budget remains locked
- Admin must fix data before unlock possible

**Why:** Real-world data corruption can happen (bugs, manual DB edits, migration issues). System should either handle gracefully or fail safely with clear error, not crash with null pointer exception.

---

### Test 4: Concurrent Budget Operations

**Test Name:** `shouldMaintainConsistencyWhenConcurrentOperationsAttemptedOnSameBudget`

**Description:** Tests that database locking or optimistic locking prevents race conditions when multiple users operate on same budget simultaneously.

**Given:**
- Create budget (unlocked) with income, expenses, savings
- Budget is balanced and ready to lock

**When:**
- Simulate concurrent operations (use threads or CompletableFuture):
  - Thread 1: Attempt to lock budget
  - Thread 2: Attempt to add new expense to budget
  - Both execute at nearly same time

**Then:**
Possible outcomes (all acceptable):

**Outcome 1** (Pessimistic Locking):
- Lock succeeds, expense addition fails
- Error: "Cannot modify locked budget"
- Database row-level lock prevents race

**Outcome 2** (Optimistic Locking):
- One operation succeeds
- Other fails with "Budget was modified by another transaction"
- Retry recommended

**Outcome 3** (Sequential Execution):
- Operations execute in sequence (transaction isolation)
- Final state consistent

**Unacceptable outcome:**
- Both operations appear to succeed
- Budget is locked AND new expense added
- Inconsistent state

**Why:** Tests concurrency control. Without proper locking, simultaneous operations could interleave in bad ways (lock reads budget, add expense modifies budget, lock commits with stale data).

---

## Technical Implementation

1. **Test Class:** `TransactionAtomicityE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on transaction boundaries and rollback

2. **Failure Simulation Methods:**
   ```java
   private void simulateTodoGenerationFailure() {
       // Inject mock or use database constraint violation
   }

   private void corruptBalanceHistory(UUID historyId, String corruptionType) {
       // Manually update database to simulate corruption
       jdbcTemplate.update(
           "UPDATE balance_history SET change_amount = ? WHERE id = ?",
           new BigDecimal("999.99"), historyId
       );
   }

   private void simulateBalanceReversalFailure(UUID accountId) {
       // Drop constraint or inject failing mock
   }
   ```

3. **Concurrency Test Utilities:**
   ```java
   private ExecutorService executor = Executors.newFixedThreadPool(2);

   private ConcurrentOperationResult executeSimultaneously(
       Callable<Response> operation1,
       Callable<Response> operation2
   ) throws Exception {
       Future<Response> future1 = executor.submit(operation1);
       Future<Response> future2 = executor.submit(operation2);

       Response response1 = future1.get(5, TimeUnit.SECONDS);
       Response response2 = future2.get(5, TimeUnit.SECONDS);

       return new ConcurrentOperationResult(response1, response2);
   }
   ```

4. **State Verification:**
   ```java
   private void assertBudgetStateUnchanged(UUID budgetId, BudgetSnapshot before) {
       Budget after = budgetRepository.findById(budgetId).orElseThrow();
       assertThat(after.getStatus()).isEqualTo(before.getStatus());
       assertThat(after.getLockedAt()).isEqualTo(before.getLockedAt());
   }

   private void assertNoBalanceChanges(List<UUID> accountIds, Map<UUID, BigDecimal> beforeBalances) {
       accountIds.forEach(id -> {
           BigDecimal afterBalance = getAccountBalance(id);
           assertThat(afterBalance).isEqualByComparingTo(beforeBalances.get(id));
       });
   }
   ```

5. **Database Direct Access:**
   ```java
   @Autowired
   private JdbcTemplate jdbcTemplate;

   private void corruptData(String sql, Object... params) {
       jdbcTemplate.update(sql, params);
   }

   private int countRecords(String tableName, String whereClause) {
       return jdbcTemplate.queryForObject(
           "SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause,
           Integer.class
       );
   }
   ```

## Definition of Done

- All 4 test scenarios implemented and passing
- Rollback tests verify complete state restoration (no partial updates)
- Failure simulation realistic (not just throwing random exceptions)
- Corrupted data test documents expected behavior (graceful vs strict)
- Concurrency test uses actual threads with timing coordination
- Tests verify database transaction isolation levels work as expected
- Error messages captured and verified (user-facing clarity)
- Tests clean up corrupted data in @AfterEach
- Thread pools properly shut down after tests
- Code coverage includes all transaction boundaries
- Tests document @Transactional annotation requirements for each service method
