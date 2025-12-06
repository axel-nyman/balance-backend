# Story 38: Performance & Scale E2E Tests

**As a** developer
**I want to** verify the system performs acceptably under extreme data volumes
**So that** edge users with many accounts or long history don't experience degradation

## Acceptance Criteria

- System handles budgets with 50 bank accounts and 200+ items
- Balance history pagination works correctly with 1000+ entries
- Accounts with 100+ savings items in single budget aggregate correctly
- All operations complete in reasonable time (documented thresholds)
- No N+1 query problems or memory leaks with large datasets

## Test Specifications

### Test 1: Large Budget with Many Accounts

**Test Name:** `shouldHandleBudgetWithFiftyAccountsAndHundredsOfItems`

**Description:** Tests system performance and correctness with unrealistic but possible extreme scale.

**Given:**
- Create 50 bank accounts (A1, A2, ..., A50)
- Create budget with 200+ items:
  - 50 income items (one per account)
  - 100 expense items (distributed across accounts)
  - 50 savings items (one per account)
- Budget is balanced (income - expenses - savings = 0)

**When:**
- Lock budget (triggers transfer calculation with 50 accounts)
- Measure time for:
  - Transfer calculation
  - Todo list generation
  - Balance updates (50 accounts)
  - Overall lock operation

**Then:**
- Lock completes successfully within reasonable time:
  - Transfer calculation: < 2 seconds
  - Overall lock operation: < 10 seconds
- Transfer algorithm scales to 50 accounts
- Todo list generated with correct transfers
- All 50 account balances updated correctly
- GET /api/budgets/{id} returns full budget details efficiently
- No OutOfMemoryError or stack overflow
- Database query count reasonable (no N+1)

**Why:** Tests algorithm performance at scale. Transfer calculation is O(n log n) where n=account count. 50 accounts is extreme but verifies no exponential blow-up.

---

### Test 2: Balance History Pagination Boundaries

**Test Name:** `shouldHandlePaginationBoundariesForBalanceHistoryWithThousandsOfEntries`

**Description:** Tests balance history pagination with realistic long-term usage volume.

**Given:**
- Create bank account
- Generate 1000+ balance history entries:
  - 500 manual updates
  - 500 automatic updates from locking 500 budgets with savings
- Mix of different amounts, dates, comments

**When:**
- Query balance history with pagination:
  - GET /api/bank-accounts/{id}/balance-history?page=0&size=20
  - GET page=0 (first page)
  - GET page=25 (middle page)
  - GET page=49 (last page with entries)
  - GET page=50 (beyond last page)

**Then:**
- First page: 20 entries, newest first (ordered by changeDate DESC)
- Middle page: 20 entries, correct offset
- Last page: remaining entries (1-20 entries)
- Beyond last page: empty content array, valid page metadata
- Page metadata correct:
  - totalElements: 1000+
  - totalPages: calculated correctly
  - number: current page number
  - size: 20
- Query performance acceptable (< 500ms per page)
- No full table scan (EXPLAIN shows index usage)

**Why:** Tests pagination implementation with realistic long-term volume. Users might have years of history. Ensures pagination doesn't degrade and handles boundaries correctly.

---

### Test 3: Many Savings Items Per Account

**Test Name:** `shouldHandleAccountWithHundredsOfSavingsItemsInSingleBudget`

**Description:** Tests aggregation logic when one account receives many savings allocations in single budget.

**Given:**
- Create Account A
- Create budget with 100 savings items all targeting Account A:
  - "Emergency Fund": $100
  - "Vacation Fund": $50
  - "Home Repair": $75
  - ... (97 more entries)
  - Total savings to Account A: calculate sum

**When:**
- Lock budget
- System must aggregate all 100 savings for Account A

**Then:**
- Lock completes successfully
- Account A balance increases by exact sum of all 100 savings
- Balance history shows one entry with correct total changeAmount
- Aggregation query efficient (single SUM query, not N individual queries)
- Performance acceptable (< 3 seconds for lock)
- Budget totals show correct savings sum

**Why:** Tests aggregation logic. Naive implementation might process each savings individually. Efficient implementation uses SQL aggregation (GROUP BY + SUM). Verifies no O(n) individual account updates.

---

## Technical Implementation

1. **Test Class:** `PerformanceScaleE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on performance with large datasets

2. **Data Generation Helpers:**
   ```java
   private List<UUID> createManyAccounts(int count) {
       return IntStream.range(0, count)
           .mapToObj(i -> createBankAccount("Account-" + i, BigDecimal.ZERO))
           .collect(Collectors.toList());
   }

   private void createManyBudgetItems(UUID budgetId, List<UUID> accounts, int itemCount) {
       // Distribute items across accounts
       for (int i = 0; i < itemCount; i++) {
           UUID accountId = accounts.get(i % accounts.size());
           // Create income/expense/savings
       }
   }

   private void generateBalanceHistory(UUID accountId, int entryCount) {
       for (int i = 0; i < entryCount; i++) {
           manuallyUpdateBalance(accountId, randomAmount(), "Entry " + i);
       }
   }
   ```

3. **Performance Measurement:**
   ```java
   private <T> TimedResult<T> measurePerformance(
       String operationName,
       Supplier<T> operation,
       long maxMillis
   ) {
       long start = System.currentTimeMillis();
       T result = operation.get();
       long duration = System.currentTimeMillis() - start;

       assertThat(duration)
           .as(operationName + " should complete within " + maxMillis + "ms")
           .isLessThan(maxMillis);

       System.out.println(operationName + " completed in " + duration + "ms");
       return new TimedResult<>(result, duration);
   }

   private void assertFastQuery(long actualMs, long maxMs, String queryDescription) {
       assertThat(actualMs)
           .as(queryDescription + " query should be fast (< " + maxMs + "ms)")
           .isLessThan(maxMs);
   }
   ```

4. **Query Analysis (optional, for debugging):**
   ```java
   @Autowired
   private EntityManager entityManager;

   private void logQueryPlan(String sql) {
       Query query = entityManager.createNativeQuery("EXPLAIN " + sql);
       List<?> results = query.getResultList();
       results.forEach(row -> System.out.println(row));
   }

   private long countQueriesExecuted(Runnable operation) {
       // Use Spring Boot's query counter or logging
       // Or P6Spy for query counting
       return queryCounter.getCount();
   }
   ```

5. **Memory Monitoring:**
   ```java
   private MemorySnapshot captureMemoryUsage() {
       Runtime runtime = Runtime.getRuntime();
       return new MemorySnapshot(
           runtime.totalMemory(),
           runtime.freeMemory(),
           runtime.maxMemory()
       );
   }

   private void assertNoMemoryLeak(MemorySnapshot before, MemorySnapshot after) {
       long usedBefore = before.getTotalMemory() - before.getFreeMemory();
       long usedAfter = after.getTotalMemory() - after.getFreeMemory();
       long growth = usedAfter - usedBefore;

       assertThat(growth)
           .as("Memory growth should be reasonable")
           .isLessThan(50 * 1024 * 1024); // 50 MB threshold
   }
   ```

## Definition of Done

- All 3 test scenarios implemented and passing
- Performance thresholds documented and justified
- Tests run successfully in CI environment (may need increased timeout)
- Data generation efficient (uses batch operations where possible)
- Test cleanup doesn't timeout (delete 1000+ entries efficiently)
- Performance measurements logged to console for analysis
- Tests verify no N+1 query problems (use query counter)
- Memory usage monitored (optional: assert no memory leaks)
- Tests document expected O(n) complexity for each operation
- Database indexes verified (pagination uses index, aggregation efficient)
- Tests run with real database (Testcontainers), not H2
- Consider @Tag("slow") annotation to separate from fast tests
- Code coverage includes batch operations and aggregation queries
