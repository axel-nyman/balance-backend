# Story 32: Transfer Algorithm Correctness E2E Tests

**As a** developer
**I want to** verify the transfer calculation algorithm produces optimal and correct results
**So that** users receive minimal, mathematically sound transfer todo lists

## Acceptance Criteria

- Transfer algorithm never generates circular transfers (A→B→C→A)
- Algorithm never creates self-transfers (A→A)
- Transfer count is minimal (greedy algorithm optimization verified)
- Edge cases handled: all deficit accounts, single dominant account
- Algorithm correctness verified with complex multi-account scenarios

## Test Specifications

### Test 1: Circular Transfer Prevention

**Test Name:** `shouldNeverGenerateCircularTransfersInComplexMultiAccountScenarios`

**Description:** Verifies that the greedy transfer algorithm cannot produce circular money flows even with deliberately tricky account arrangements.

**Given:**
- Create 6 bank accounts (A, B, C, D, E, F)
- Create budget designed to potentially cause circular transfers:
  - Account A: $1000 income, $200 expenses, $600 savings = +$200 net
  - Account B: $500 income, $300 expenses, $0 savings = +$200 net
  - Account C: $0 income, $150 expenses, $0 savings = -$150 net
  - Account D: $0 income, $150 expenses, $0 savings = -$150 net
  - Account E: $0 income, $50 expenses, $0 savings = -$50 net
  - Account F: $0 income, $50 expenses, $0 savings = -$50 net

**When:**
- Lock budget and generate todo list with transfers

**Then:**
- No circular paths exist in transfer graph
- Each transfer flows from surplus to deficit account
- Total transfer amount = $400 (sum of all deficits)
- Verify each account appears at most once as source OR destination in transfer chain
- Example valid output: A→C $150, A→D $50, B→E $50, B→F $50

**Why:** Buggy graph algorithms could create circular transfers where users move money in circles, wasting effort. Greedy algorithm should naturally prevent this, but edge cases must be verified.

---

### Test 2: Self-Transfer Prevention

**Test Name:** `shouldNeverGenerateSelfTransfersFromAccountToItself`

**Description:** Tests that accounts with net-zero position don't generate meaningless self-transfers.

**Given:**
- Create 3 accounts:
  - Account A: $500 income, $300 expenses, $200 savings = $0 net
  - Account B: $1000 income, $0 expenses, $600 savings = +$400 net
  - Account C: $0 income, $400 expenses, $0 savings = -$400 net

**When:**
- Lock budget and generate transfers

**Then:**
- No transfer with fromAccountId == toAccountId exists
- Account A appears in zero transfers (balanced on itself)
- Only one transfer exists: B→C $400
- Total transfers generated: 1

**Why:** Edge case where account is simultaneously balanced. Algorithm must recognize net-zero and exclude from transfers entirely, not create A→A transfer.

---

### Test 3: Transfer Count Minimization

**Test Name:** `shouldOptimizeTransfersToMinimumCountWithComplexMultiAccountWeb`

**Description:** Verifies the greedy algorithm produces minimal transfer count, not naive one-per-deficit approach.

**Given:**
- Create 8 accounts with complex arrangement:
  - Account A: +$5000 net (large surplus)
  - Account B: +$500 net
  - Account C: +$300 net
  - Account D: -$2000 net
  - Account E: -$1500 net
  - Account F: -$1200 net
  - Account G: -$600 net
  - Account H: -$500 net
  - Total surplus: $5800, Total deficit: $5800 (balanced)

**When:**
- Lock budget and generate transfers

**Then:**
- Maximum of 7 transfers generated (N-1 where N=8 accounts with non-zero positions)
- Typically fewer due to greedy optimization
- Each deficit satisfied exactly
- Each surplus exhausted exactly
- Verify transfer count is mathematically minimal
- Example optimal solution uses 5-6 transfers (A covers multiple deficits)

**Why:** Tests algorithm optimization. Naive approach might create 5 transfers (one per deficit). Greedy should minimize by leveraging large surplus accounts efficiently.

---

### Test 4: All-Deficit Edge Case

**Test Name:** `shouldHandleTransferCalculationWhenAllAccountsAreDeficit`

**Description:** Tests algorithm behavior when mathematical impossibility occurs (should not happen in balanced budget, but algorithm must handle gracefully).

**Given:**
- Create intentionally unbalanced budget (should be rejected at lock, but test algorithm in isolation):
  - Account A: $0 income, $500 expenses, $0 savings = -$500 net
  - Account B: $0 income, $300 expenses, $0 savings = -$300 net
  - Account C: $0 income, $200 expenses, $0 savings = -$200 net
  - Total net: -$1000 (no surplus accounts)

**When:**
- Call TransferCalculationUtils.calculateTransfers() directly (bypassing lock validation)

**Then:**
- Algorithm returns empty transfer list (no transfers possible)
- No exceptions thrown
- OR algorithm throws descriptive exception explaining impossibility
- No infinite loops or hangs

**Why:** Edge case testing algorithm robustness. Should never happen in production (budget lock prevents unbalanced budgets), but algorithm must handle gracefully in unit tests or future refactoring.

---

### Test 5: Dominant Hub Account

**Test Name:** `shouldCalculateCorrectTransfersWhenOneAccountDominatesAllActivity`

**Description:** Tests realistic pattern where one primary account handles most money, others are minor.

**Given:**
- Create scenario matching typical user with one main account:
  - Account A (Main Checking): $10,000 income, $2,000 savings = +$8,000 net
  - Account B (Bills): $0 income, $2,000 expenses = -$2,000 net
  - Account C (Rent): $0 income, $3,000 expenses = -$3,000 net
  - Account D (Groceries): $0 income, $1,500 expenses = -$1,500 net
  - Account E (Gas): $0 income, $1,500 expenses = -$1,500 net

**When:**
- Lock budget and generate transfers

**Then:**
- Exactly 4 transfers generated (all from A to others)
- A→B $2,000
- A→C $3,000
- A→D $1,500
- A→E $1,500
- Total transferred: $8,000 (equals A's surplus)
- All deficit accounts satisfied

**Why:** Most realistic user pattern. Tests that algorithm efficiently handles one-to-many transfer scenarios, common in real usage.

---

## Technical Implementation

1. **Test Class:** `TransferAlgorithmE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Mix of integration tests (full budget cycle) and direct utility tests

2. **Helper Methods Needed:**
   ```java
   private List<TransferPlan> calculateTransfersForBudget(UUID budgetId)
   private void assertNoCircularTransfers(List<TransferPlan> transfers)
   private void assertNoSelfTransfers(List<TransferPlan> transfers)
   private void assertTransfersAreMinimal(List<TransferPlan> transfers, int maxExpected)
   private void assertTransfersBalanceAllAccounts(List<TransferPlan> transfers, Map<UUID, BigDecimal> netPositions)
   private Map<UUID, BigDecimal> buildTransferGraph(List<TransferPlan> transfers)
   ```

3. **Graph Analysis Helpers:**
   ```java
   private boolean containsCycle(Map<UUID, List<UUID>> adjacencyList) {
       // DFS-based cycle detection
   }

   private void assertAllDeficitsSatisfied(List<TransferPlan> transfers, Set<UUID> deficitAccounts) {
       // Verify each deficit account receives exactly what it needs
   }

   private int calculateMinimumPossibleTransfers(Map<UUID, BigDecimal> netPositions) {
       // Theoretical minimum: max(surplusCount, deficitCount)
   }
   ```

4. **Direct Algorithm Testing:**
   ```java
   @Test
   void testTransferUtilDirectly() {
       Budget budget = createBudgetEntity(...);
       List<BudgetIncome> income = ...;
       List<BudgetExpense> expenses = ...;
       List<BudgetSavings> savings = ...;

       List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
           budget, income, expenses, savings
       );

       // Assertions...
   }
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Tests verify both integration (full budget cycle) and unit (direct utility call) levels
- Graph analysis helpers implemented for circular transfer detection
- Transfer optimality mathematically verified
- Edge cases documented with explanations
- Performance acceptable (even with 8+ accounts)
- Algorithm behavior with unbalanced budgets documented (should not occur in production)
- Tests verify TransferCalculationUtils is pure function (same input → same output)
- Test coverage includes realistic user patterns (single main account scenario)
