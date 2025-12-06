# Story 31: Numeric Precision & Rounding E2E Tests

**As a** developer
**I want to** verify that monetary calculations maintain precision across all operations
**So that** no "vanishing pennies" or rounding errors accumulate over time

## Acceptance Criteria

- BigDecimal calculations maintain precision across 10+ budget cycles
- System handles very large monetary amounts without overflow
- Negative account balances are calculated correctly in budget scenarios
- Extremely small fractional amounts don't lose precision
- Zero-balance detection uses exact comparison, not approximate

## Test Specifications

### Test 1: Rounding Error Accumulation

**Test Name:** `shouldAccumulateRoundingErrorsCorrectlyAcrossTenBudgetCycles`

**Description:** Verifies that repeating calculations with non-terminating decimals don't accumulate rounding errors over many budget cycles.

**Given:**
- Create bank account with initial balance $1000.00
- Create 10 consecutive monthly budgets (Jan-Oct)
- Each budget has:
  - Income: $1000.00
  - Expenses: $666.67
  - Savings: $333.33

**When:**
- Lock each budget in sequence

**Then:**
- After 10 cycles:
  - Account balance = $1000.00 + (10 × $333.33) = $4333.30
  - Balance history shows no accumulated rounding errors
  - Each individual history entry has correct changeAmount
- No "lost pennies" or unexpected rounding
- All calculations use HALF_UP rounding mode consistently

**Why:** Repeated operations with non-terminating decimals (like 1/3 = 0.333...) can accumulate rounding errors. This verifies BigDecimal is used correctly throughout.

---

### Test 2: Very Large Amount Handling

**Test Name:** `shouldHandleVeryLargeMoneyAmountsWithoutOverflow`

**Description:** Tests system behavior with amounts near the practical maximum for BigDecimal monetary values.

**Given:**
- Create account with balance $900,000,000.00 (900 million)
- Create budget with:
  - Income: $99,999,999.99 to this account
  - Expenses: $50,000,000.00 from this account
  - Savings: $49,999,999.99 to this account

**When:**
- Calculate budget balance (should be zero)
- Lock budget
- Query account balance

**Then:**
- Budget validates as balanced (income - expenses - savings = 0)
- Lock succeeds without overflow errors
- Account balance = $950,000,000.00 - $0.01 (initial + savings)
- All calculations precise to 2 decimal places
- No exceptions or numeric overflow

**Why:** Edge case for extremely wealthy users or systems using wrong units (cents instead of dollars). Verifies BigDecimal handles large values correctly.

---

### Test 3: Negative Balance Calculations

**Test Name:** `shouldHandleAccountsWithLargeNegativeBalancesInBudgetCalculations`

**Description:** Verifies transfer calculations and budget operations work correctly when accounts have negative balances.

**Given:**
- Account A: balance = -$5000.00 (overdraft)
- Account B: balance = $3000.00
- Create budget with:
  - Income: $1000.00 to Account A
  - Expenses: $200.00 from Account B
  - Savings: $800.00 to Account B

**When:**
- Lock budget

**Then:**
- Transfer calculation correctly handles negative starting balance
- No transfers generated (income/expenses on different accounts)
- Account A balance becomes: -$5000.00 + $0 = -$5000.00 (no savings)
- Account B balance becomes: $3000.00 + $800.00 = $3800.00
- All arithmetic with negative numbers correct

**Why:** Transfer algorithm might assume positive balances. Negative balances (overdrafts, credit cards) must be handled correctly.

---

### Test 4: Fractional Amount Precision

**Test Name:** `shouldHandleExtremelySmallFractionalAmountsWithoutPrecisionLoss`

**Description:** Tests that very small amounts (sub-cent values in some currencies) maintain precision through hundreds of operations.

**Given:**
- Create account with balance $100.00
- Perform 100 budget cycles, each with:
  - Income: $1.001
  - Expenses: $1.000
  - Savings: $0.001

**When:**
- Lock all 100 budgets in sequence

**Then:**
- After 100 cycles, account balance = $100.00 + (100 × $0.001) = $100.10
- No precision loss from many small operations
- Final balance exactly matches mathematical expectation
- All history entries show correct fractional amounts

**Why:** Microtransaction scenarios or foreign currency conversions (e.g., Japanese Yen to USD) could expose precision bugs with small fractions.

---

### Test 5: Exact Zero Detection

**Test Name:** `shouldCorrectlyCalculateBalanceWhenZeroingOutWithComplementaryOperations`

**Description:** Verifies that budget balance calculation treats mathematically exact zero as lockable, without floating-point comparison issues.

**Given:**
- Create budget with carefully chosen values that sum to exactly zero:
  - Income: $12345.67
  - Expenses: $11111.11
  - Savings: $1234.56
  - Mathematical balance: $12345.67 - $11111.11 - $1234.56 = $0.00

**When:**
- Calculate budget totals
- Attempt to lock budget

**Then:**
- Budget balance calculated as exactly $0.00
- Balance comparison uses `compareTo(BigDecimal.ZERO) == 0`
- Lock succeeds without "balance must be zero" error
- No false rejection due to floating point errors (0.00000001)

**Why:** Critical for lock validation. Wrong comparison (== vs compareTo) or floating point residue could prevent locking perfectly balanced budgets.

---

## Technical Implementation

1. **Test Class:** `NumericPrecisionE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on mathematical correctness

2. **Helper Methods Needed:**
   ```java
   private void createBudgetCycle(int month, BigDecimal income, BigDecimal expenses, BigDecimal savings)
   private void lockMultipleBudgets(int count, BudgetConfig config)
   private BigDecimal getAccountBalance(UUID accountId)
   private void assertExactBalance(UUID accountId, String expectedAmount)
   private void assertNoPrecisionLoss(List<BalanceHistory> history)
   ```

3. **Assertion Helpers:**
   ```java
   private void assertBigDecimalEquals(BigDecimal expected, BigDecimal actual, String message) {
       assertThat(actual).usingComparator(BigDecimal::compareTo).isEqualTo(expected);
   }

   private void assertBalanceSumsToExpected(List<BalanceHistory> history, BigDecimal expected) {
       BigDecimal calculated = history.stream()
           .map(BalanceHistory::getChangeAmount)
           .reduce(BigDecimal.ZERO, BigDecimal::add);
       assertBigDecimalEquals(expected, calculated, "Balance history sum mismatch");
   }
   ```

4. **Test Constants:**
   ```java
   private static final BigDecimal NINE_HUNDRED_MILLION = new BigDecimal("900000000.00");
   private static final BigDecimal MAX_TEST_AMOUNT = new BigDecimal("999999999.99");
   private static final BigDecimal ONE_THIRD = new BigDecimal("333.33");
   private static final BigDecimal MICRO_AMOUNT = new BigDecimal("0.001");
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Tests verify exact BigDecimal precision (no tolerance comparisons)
- Tests document expected rounding behavior (HALF_UP mode)
- Edge cases for negative numbers verified
- Tests for both very large and very small amounts
- No floating-point arithmetic used anywhere in tests
- All monetary values constructed with String constructor: `new BigDecimal("10.50")`
- Tests verify `compareTo()` is used for equality, not `equals()`
- Code coverage includes boundary values
- Performance acceptable (complex calculations complete in reasonable time)
