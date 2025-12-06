# Story 36: Multi-Budget Temporal E2E Tests

**As a** developer
**I want to** verify the system handles long-term usage patterns and temporal edge cases
**So that** date comparisons, budget queries, and performance remain correct over months/years

## Acceptance Criteria

- System handles 30+ budget cycles without performance degradation
- Budget (month, year) uniqueness constraint allows recreation after deletion
- Leap year dates handled correctly without calculation errors
- Most recent budget identification works across multiple year boundaries
- Extreme year values (2100) work without date calculation issues

## Test Specifications

### Test 1: Long-Term Usage Performance

**Test Name:** `shouldHandleThirtyBudgetCyclesWithRecurringExpenseAndMaintainPerformance`

**Description:** Tests system scalability and performance with realistic long-term usage (2.5 years of monthly budgets).

**Given:**
- Create recurring expense "Rent"
- Create 30 consecutive monthly budgets (Jan 2023 - Jun 2025)
- Each budget includes the "Rent" recurring expense
- Lock all 30 budgets in chronological order

**When:**
- Measure performance of operations:
  - Query all budgets (GET /api/budgets)
  - Query most recent budget
  - Query recurring expense "Rent" to see lastUsedDate
  - Unlock most recent budget (triggers "find previous budget" query)

**Then:**
- All operations complete in acceptable time:
  - List budgets: < 1 second
  - Find most recent: < 100ms
  - Recurring expense query: < 100ms
  - Unlock operation: < 2 seconds
- "Find previous locked budget using template" query performs well even with 30 budgets
- No N+1 query problems
- Pagination works correctly for budget list
- Memory usage reasonable (no memory leaks from 30 cycles)

**Why:** Tests scalability. Users will accumulate many budgets over years. Queries must remain performant, especially the "find previous budget" query used during unlock.

---

### Test 2: Budget Recreati on After Deletion

**Test Name:** `shouldHandleBudgetCreationForSameMonthAfterDeletingPrevious`

**Description:** Verifies that (month, year) uniqueness constraint allows recreation after deletion.

**Given:**
- Create budget for January 2025
- Add income, expenses, savings
- Delete budget (without locking it)

**When:**
- Create new budget for January 2025 again

**Then:**
- Creation succeeds with 201 Created
- No constraint violation on (month, year)
- New budget gets different UUID
- No data from old budget appears in new one
- Deletion was complete (no orphaned data)

**Alternative scenario:**
- Lock budget before deleting → should fail (cannot delete locked)
- Unlock budget, then delete → should succeed
- Recreate for same month → succeeds

**Why:** Tests that UNIQUE constraint on (month, year) only applies to active budgets, not deleted ones. Soft delete might need special handling in constraint.

---

### Test 3: Leap Year Handling

**Test Name:** `shouldHandleLeapYearBudgetCreationWithoutDateIssues`

**Description:** Tests that date calculations don't fail on leap year edge cases.

**Given:**
- Create budgets for February in leap and non-leap years:
  - February 2024 (leap year - 29 days)
  - February 2025 (non-leap year - 28 days)
  - February 2028 (leap year - 29 days)

**When:**
- Perform operations that might involve date calculations:
  - Create budgets (month validation)
  - Lock budgets (timestamp comparisons)
  - Determine "most recent" (year/month comparison)
  - Query balance history around Feb 29

**Then:**
- All operations succeed without date calculation errors
- Feb 29, 2024 is valid date
- Feb 29, 2025 doesn't cause crashes (doesn't exist)
- Budget ordering correct: Feb 2024 < Feb 2025 < Feb 2028
- No DateTimeException or calendar arithmetic errors

**Why:** Leap years are classic edge case. Tests that month/year storage and comparison don't assume all Februaries are equal or perform invalid date arithmetic.

---

### Test 4: Year Boundary Budget Ordering

**Test Name:** `shouldCorrectlyIdentifyMostRecentBudgetAcrossMultipleYears`

**Description:** Verifies "most recent budget" query works correctly across multiple year boundaries.

**Given:**
- Create and lock budgets spanning multiple years:
  - December 2023
  - January 2024
  - December 2024
  - January 2025
  - June 2025

**When:**
- Query for most recent budget after each lock
- Attempt to unlock each budget (only most recent should succeed)

**Then:**
- After locking June 2025:
  - Most recent query returns June 2025
  - Can unlock June 2025 (succeeds)
  - Cannot unlock January 2025 (fails - not most recent)
  - Cannot unlock December 2024 (fails)
- Ordering query: `ORDER BY year DESC, month DESC LIMIT 1`
- Year comparison takes precedence over month
- Jan 2025 > Dec 2024 (year boundary handled correctly)

**Why:** Classic date comparison bug: comparing months without years. Tests that "most recent" correctly handles year rollover (Jan 2025 is newer than Dec 2024).

---

### Test 5: Extreme Year Value Handling

**Test Name:** `shouldHandleBudgetOperationsWithMaximumReasonableYearValue`

**Description:** Tests system behavior with year at upper boundary of validation (2100).

**Given:**
- Create budget for December 2100 (assuming validation allows 2000-2100)
- Add income, expenses, savings

**When:**
- Lock budget
- Create balance history entries
- Query budgets
- Unlock budget

**Then:**
- All operations succeed without integer overflow
- Year stored correctly as 2100 (not wrapped or truncated)
- Date comparisons work (2100 > 2025)
- Timestamps valid (lockedAt uses 2100 year)
- No arithmetic overflow in year calculations
- Validation accepts 2100 but rejects 2101 (boundary test)

**Alternative boundary tests:**
- Year 1999 (below minimum 2000) → rejected
- Year 2101 (above maximum 2100) → rejected
- Year 2000 (minimum valid) → accepted
- Year 2100 (maximum valid) → accepted

**Why:** Tests year validation boundaries and ensures no integer overflow or invalid date calculations at extremes. 2100 is reasonable upper bound for budget planning.

---

## Technical Implementation

1. **Test Class:** `MultiBudgetTemporalE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on temporal logic and long-term scenarios

2. **Helper Methods Needed:**
   ```java
   private void createAndLockBudgetCycles(int startYear, int startMonth, int count)
   private UUID getMostRecentBudgetId()
   private long measureQueryTime(Callable<Void> operation)
   private void assertPerformanceAcceptable(long milliseconds, long maxMillis)
   private void assertBudgetOrdering(List<UUID> expectedOrder)
   private boolean isLeapYear(int year)
   ```

3. **Performance Measurement:**
   ```java
   private <T> TimedResult<T> measureExecutionTime(Supplier<T> operation) {
       long start = System.currentTimeMillis();
       T result = operation.get();
       long duration = System.currentTimeMillis() - start;
       return new TimedResult<>(result, duration);
   }

   private void assertOperationFast(String operationName, long actualMs, long maxMs) {
       assertThat(actualMs)
           .as(operationName + " should complete in less than " + maxMs + "ms")
           .isLessThan(maxMs);
   }
   ```

4. **Date Utilities:**
   ```java
   private LocalDate getLastDayOfMonth(int year, int month) {
       return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastDayOfMonth());
   }

   private void assertValidDate(int year, int month, int day) {
       assertThatCode(() -> LocalDate.of(year, month, day)).doesNotThrowAnyException();
   }
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Performance tests include actual timing measurements with assertions
- Budget recreation after deletion verified at database level
- Leap year edge cases tested (Feb 29 exists/doesn't exist)
- Year boundary ordering verified with assertions on query results
- Extreme year values tested at validation boundaries (1999, 2000, 2100, 2101)
- Tests document acceptable performance thresholds
- Long-term test (30 budgets) doesn't timeout in CI
- All date comparisons use proper temporal APIs (no string comparison)
- Code coverage includes year rollover scenarios
