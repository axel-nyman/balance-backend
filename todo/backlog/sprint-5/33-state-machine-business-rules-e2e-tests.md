# Story 33: State Machine & Business Rules E2E Tests

**As a** developer
**I want to** verify business rules are enforced consistently across all system states
**So that** data integrity constraints prevent invalid operations and state transitions

## Acceptance Criteria

- Only one unlocked budget can exist at any time (even under race conditions)
- Budget lock requires exactly zero balance (not approximately zero)
- Only the most recent budget can be unlocked (by year DESC, month DESC)
- Locked budgets cannot be modified through any endpoint
- Account deletion blocked only for unlocked budget references, not locked

## Test Specifications

### Test 1: Race Condition Prevention for Unlocked Budgets

**Test Name:** `shouldPreventSecondUnlockedBudgetThroughRaceConditionSimulation`

**Description:** Verifies that database constraints or application-level locking prevents creating two unlocked budgets, even with concurrent requests.

**Given:**
- No existing budgets in system
- Prepare two budget creation requests for different months

**When:**
- Simulate concurrent creation (use CompletableFuture or multiple threads):
  - Thread 1: POST /api/budgets {month: 1, year: 2025}
  - Thread 2: POST /api/budgets {month: 2, year: 2025}
  - Both execute nearly simultaneously

**Then:**
- Exactly one request succeeds with 201 Created
- One request fails with 400 Bad Request: "Another budget is currently unlocked"
- Database contains exactly 1 unlocked budget
- No race condition allows both to succeed
- Retry logic works: after locking first budget, second request succeeds

**Why:** Classic concurrency bug. Without proper database constraints or pessimistic locking, two requests could both pass the "check if unlocked exists" test and both create unlocked budgets. Tests database UNIQUE constraint or application-level locking.

---

### Test 2: Strict Zero Balance Validation

**Test Name:** `shouldDetectAndRejectBudgetLockWhenBalanceIsNonZeroButVeryClose`

**Description:** Tests that balance validation uses exact BigDecimal comparison, not floating-point tolerance.

**Given:**
- Create budget with values designed to produce tiny rounding residue:
  - Income: $10.00
  - Expenses: $3.33
  - Savings: $6.67
  - Mathematical balance: $10.00 - $3.33 - $6.67 = $0.00

- Then modify to create non-zero balance:
  - Change expenses to $3.34
  - Balance: $10.00 - $3.34 - $6.67 = -$0.01

**When:**
- Attempt to lock budget

**Then:**
- Lock rejected with 400 Bad Request
- Error message: "Budget must have zero balance. Current balance: -0.01"
- Budget remains UNLOCKED
- No todo list generated
- No balance updates applied
- Validation uses: `balance.compareTo(BigDecimal.ZERO) != 0`

**Why:** Critical precision test. If code uses floating-point comparison with tolerance (like `Math.abs(balance) < 0.001`), slightly unbalanced budgets could be locked. Must use exact BigDecimal comparison.

---

### Test 3: Most Recent Budget Unlock Constraint

**Test Name:** `shouldHandleUnlockingBudgetWhenItsNotMostRecentDueToYearBoundary`

**Description:** Verifies that unlock permission correctly compares budgets across year boundaries.

**Given:**
- Create and lock budgets in chronological order:
  - Oct 2024 (locked)
  - Nov 2024 (locked)
  - Dec 2024 (locked)
  - Jan 2025 (locked)
  - Feb 2025 (locked)

**When:**
- Attempt to unlock Dec 2024 → should fail (not most recent)
- Attempt to unlock Jan 2025 → should fail (not most recent)
- Attempt to unlock Feb 2025 → should succeed (most recent)

**Then:**
- Dec 2024 unlock: 400 Bad Request "Only the most recent budget can be unlocked"
- Jan 2025 unlock: 400 Bad Request "Only the most recent budget can be unlocked"
- Feb 2025 unlock: 200 OK, budget status changes to UNLOCKED
- Ordering logic: ORDER BY year DESC, month DESC LIMIT 1

**Why:** Year boundary comparison bugs are common (comparing months without years). Tests that "most recent" correctly spans year changes: Jan 2025 is newer than Dec 2024.

---

### Test 4: Locked Budget Modification Prevention

**Test Name:** `shouldPreventModifyingBudgetItemsInLockedBudgetThroughAllEndpoints`

**Description:** Comprehensive test that locked status prevents modifications through every possible endpoint.

**Given:**
- Create budget with income, expense, and savings items
- Lock the budget

**When:**
- Attempt every modification endpoint:
  - POST /api/budgets/{id}/income → add new income
  - PUT /api/budgets/{id}/income/{incomeId} → update income
  - DELETE /api/budgets/{id}/income/{incomeId} → delete income
  - POST /api/budgets/{id}/expenses → add new expense
  - PUT /api/budgets/{id}/expenses/{expenseId} → update expense
  - DELETE /api/budgets/{id}/expenses/{expenseId} → delete expense
  - POST /api/budgets/{id}/savings → add new savings
  - PUT /api/budgets/{id}/savings/{savingsId} → update savings
  - DELETE /api/budgets/{id}/savings/{savingsId} → delete savings

**Then:**
- All 9 endpoints return 400 Bad Request
- Error message: "Cannot modify locked budget" or "Cannot modify items in locked budget"
- Budget items remain unchanged
- No database modifications occurred
- Lock enforcement consistent across all endpoints

**Why:** Ensures lock is enforced universally. Easy to miss one endpoint or forget validation in new features. Comprehensive test catches gaps in enforcement.

---

### Test 5: Account Deletion Constraint by Budget Status

**Test Name:** `shouldAllowDeletingAccountUsedOnlyInLockedBudgetsButNotUnlocked`

**Description:** Tests that account deletion constraint differentiates between locked and unlocked budget references.

**Given:**
- Create Account A
- Create January budget (unlocked), add income using Account A
- Create February budget, add expense using Account A, lock February
- Create March budget, add savings using Account A, lock March

**When:**
- Attempt to delete Account A while January still unlocked → should fail
- Lock January budget
- Attempt to delete Account A (now only referenced in locked budgets) → should succeed

**Then:**
- First deletion attempt: 400 Bad Request "Cannot delete account used in unlocked budget"
- After locking January: deletion succeeds (204 No Content)
- Account marked with deletedAt timestamp
- All locked budget references remain intact (foreign keys preserved)
- Constraint query checks: `isAccountLinkedToUnlockedBudget(accountId) == false`

**Why:** Critical business rule. Unlocked budgets can be modified, so accounts must be protected. Locked budgets are immutable, so accounts can be safely deleted (soft delete preserves historical data). Tests constraint logic differentiates correctly.

---

## Technical Implementation

1. **Test Class:** `StateMachineBusinessRulesE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on state transitions and constraint enforcement

2. **Helper Methods Needed:**
   ```java
   private void attemptConcurrentBudgetCreation() throws InterruptedException
   private void assertExactlyOneUnlockedBudgetExists()
   private void attemptAllModificationEndpoints(UUID budgetId)
   private void assertAllModificationsFailed()
   private UUID getMostRecentBudgetId()
   private void lockBudgetAndAssertSuccess(UUID budgetId)
   private boolean isAccountDeletionBlocked(UUID accountId)
   ```

3. **Concurrency Testing Utilities:**
   ```java
   private ExecutorService executorService = Executors.newFixedThreadPool(2);

   private List<CompletableFuture<MockHttpServletResponse>> executeSimultaneously(
       Callable<MockHttpServletResponse>... requests
   ) {
       return Arrays.stream(requests)
           .map(req -> CompletableFuture.supplyAsync(() -> {
               try { return req.call(); } catch (Exception e) { throw new RuntimeException(e); }
           }, executorService))
           .collect(Collectors.toList());
   }
   ```

4. **Comprehensive Modification Testing:**
   ```java
   private void verifyAllModificationEndpointsBlocked(UUID lockedBudgetId) {
       // Test all 9 modification endpoints
       assertEndpointReturns400(POST, "/api/budgets/" + lockedBudgetId + "/income", incomeRequest);
       assertEndpointReturns400(PUT, "/api/budgets/" + lockedBudgetId + "/income/..." , updateRequest);
       // ... etc for all endpoints
   }
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Concurrency test uses actual threads (not just sequential calls)
- All modification endpoints verified for lock enforcement
- Year boundary logic tested explicitly (not just same-year budgets)
- Account deletion constraint tested with both locked and unlocked references
- Tests verify exact error messages for user-facing clarity
- Database constraints documented (UNIQUE on status=UNLOCKED where applicable)
- Race condition test runs multiple times to catch intermittent failures
- Performance acceptable (concurrency test completes in < 2 seconds)
- Tests clean up threads properly (@AfterEach shutdown executor)
