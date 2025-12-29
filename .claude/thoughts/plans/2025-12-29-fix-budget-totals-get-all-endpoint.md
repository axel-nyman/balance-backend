# Fix Budget Totals in Get All Budgets Endpoint

## Overview

Fix the bug where `GET /api/budgets` always returns zero values for totals (income, expenses, savings, balance) even when budgets have items. The single budget detail endpoint `GET /api/budgets/{id}` correctly calculates totals, but the list endpoint does not.

## Current State Analysis

### The Problem
- `BudgetExtensions.toResponse(Budget budget)` at lines 15-32 returns hardcoded zeros for all totals
- The method only receives a `Budget` entity which has no direct relationship to income/expenses/savings
- `DomainService.getAllBudgets()` at lines 411-418 maps budgets using this method without fetching totals

### Why Single Budget Works
- `DomainService.getBudgetDetails()` at lines 422-430 explicitly fetches:
  - `dataService.getBudgetIncomeByBudgetId(id)`
  - `dataService.getBudgetExpensesByBudgetId(id)`
  - `dataService.getBudgetSavingsByBudgetId(id)`
- Then passes them to `BudgetExtensions.toDetailResponse()` which calculates totals

### Key Discoveries:
- `DataService` already has `calculateTotalIncome/Expenses/Savings(UUID budgetId)` methods (lines 241-253)
- These methods use repository aggregation queries to sum amounts efficiently
- Test coverage gap: No test verifies getAllBudgets returns correct totals for budgets WITH items

## Desired End State

After this fix:
1. `GET /api/budgets` returns correct totals for each budget based on actual income/expenses/savings
2. A new integration test verifies this behavior
3. The solution is performant and avoids N+1 query problems

## What We're NOT Doing

- NOT adding bidirectional JPA relationships (over-engineering for this use case)
- NOT changing the Budget entity structure
- NOT modifying the BudgetDetailResponse or its mapping
- NOT changing the database schema

## Implementation Approach

Create an overloaded `toResponse()` method that accepts pre-calculated totals, and modify `getAllBudgets()` to calculate totals for each budget. While this creates N queries per budget, the existing repository methods use efficient SUM queries, and for typical use (few budgets displayed at once) this is acceptable. If performance becomes an issue, we can later add a single batch query.

## Phase 1: Update BudgetExtensions

### Overview
Add an overloaded `toResponse()` method that accepts totals.

### Changes Required:

#### 1. BudgetExtensions.java
**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/BudgetExtensions.java`
**Changes**: Add a new overloaded method that accepts totals

```java
public static BudgetResponse toResponse(Budget budget, BudgetTotalsResponse totals) {
    return new BudgetResponse(
            budget.getId(),
            budget.getMonth(),
            budget.getYear(),
            budget.getStatus(),
            budget.getCreatedAt(),
            budget.getLockedAt(),
            totals
    );
}
```

### Success Criteria:

#### Automated Verification:
- [x] Code compiles without errors: `./mvnw compile`

---

## Phase 2: Update DomainService.getAllBudgets()

### Overview
Modify the `getAllBudgets()` method to calculate totals for each budget using the existing DataService methods.

### Changes Required:

#### 1. DomainService.java
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Update `getAllBudgets()` method (lines 411-418)

```java
@Override
public BudgetListResponse getAllBudgets() {
    List<Budget> budgets = dataService.getAllBudgetsSorted();

    List<BudgetResponse> budgetResponses = budgets.stream()
            .map(budget -> {
                BigDecimal totalIncome = dataService.calculateTotalIncome(budget.getId());
                BigDecimal totalExpenses = dataService.calculateTotalExpenses(budget.getId());
                BigDecimal totalSavings = dataService.calculateTotalSavings(budget.getId());
                BigDecimal balance = totalIncome.subtract(totalExpenses).subtract(totalSavings);

                BudgetTotalsResponse totals = new BudgetTotalsResponse(
                        totalIncome,
                        totalExpenses,
                        totalSavings,
                        balance
                );

                return BudgetExtensions.toResponse(budget, totals);
            })
            .toList();

    return new BudgetListResponse(budgetResponses);
}
```

### Success Criteria:

#### Automated Verification:
- [x] Code compiles without errors: `./mvnw compile`
- [x] Existing tests still pass: `./mvnw test`

---

## Phase 3: Add Integration Test

### Overview
Add a test that verifies the getAllBudgets endpoint returns correct totals for budgets with items.

### Changes Required:

#### 1. BudgetIntegrationTest.java
**File**: `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java`
**Changes**: Add new test in the ListAllBudgets nested class (after `shouldReturnZeroTotalsForBudgetsWithoutItems`)

```java
@Test
void shouldReturnCorrectTotalsForBudgetsWithItems() throws Exception {
    // Given - create budget with income, expenses, and savings
    createBudget(6, 2024);
    var budget = budgetRepository.findAll().get(0);
    UUID budgetId = budget.getId();

    var bankAccount = createBankAccountEntity("Test Account", "Test", new BigDecimal("10000.00"));

    // Add income: 3000
    Map<String, Object> incomeRequest = new HashMap<>();
    incomeRequest.put("name", "Salary");
    incomeRequest.put("amount", "3000.00");
    incomeRequest.put("bankAccountId", bankAccount.getId().toString());

    mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(incomeRequest)))
            .andExpect(status().isCreated());

    // Add expense: 1500
    Map<String, Object> expenseRequest = new HashMap<>();
    expenseRequest.put("name", "Rent");
    expenseRequest.put("amount", "1500.00");
    expenseRequest.put("bankAccountId", bankAccount.getId().toString());
    expenseRequest.put("deductedAt", "2024-06-01");
    expenseRequest.put("isManual", true);

    mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(expenseRequest)))
            .andExpect(status().isCreated());

    // Add savings: 500
    Map<String, Object> savingsRequest = new HashMap<>();
    savingsRequest.put("name", "Emergency Fund");
    savingsRequest.put("amount", "500.00");
    savingsRequest.put("bankAccountId", bankAccount.getId().toString());

    mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(savingsRequest)))
            .andExpect(status().isCreated());

    // When & Then - verify totals are correctly calculated
    // Expected: income=3000, expenses=1500, savings=500, balance=1000
    mockMvc.perform(get("/api/budgets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.budgets", hasSize(1)))
            .andExpect(jsonPath("$.budgets[0].totals.income", is(3000.00)))
            .andExpect(jsonPath("$.budgets[0].totals.expenses", is(1500.00)))
            .andExpect(jsonPath("$.budgets[0].totals.savings", is(500.00)))
            .andExpect(jsonPath("$.budgets[0].totals.balance", is(1000.00)));
}
```

### Success Criteria:

#### Automated Verification:
- [x] New test fails initially (TDD red phase): `./mvnw test -Dtest=BudgetIntegrationTest#shouldReturnCorrectTotalsForBudgetsWithItems` (skipped - implemented Phases 1 & 2 first)
- [x] After Phase 2 implementation, test passes: `./mvnw test -Dtest=BudgetIntegrationTest#shouldReturnCorrectTotalsForBudgetsWithItems`
- [x] All existing tests still pass: `./mvnw test` (319 tests, 0 failures)

---

## Testing Strategy

### Integration Tests:
- New test `shouldReturnCorrectTotalsForBudgetsWithItems` - verifies fix works
- Existing test `shouldReturnZeroTotalsForBudgetsWithoutItems` - must still pass
- Existing test `shouldIncludeAllBudgetFields` - must still pass

### Manual Testing Steps:
1. [x] Start the application with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
2. [x] Create a budget via POST /api/budgets
3. [x] Add income, expense, and savings to the budget
4. [x] Call GET /api/budgets and verify totals are calculated correctly
5. [x] Compare with GET /api/budgets/{id} - both should show same totals

**Manual testing completed successfully on 2025-12-29.**

## Performance Considerations

The current implementation makes 3 additional queries per budget (for income, expenses, savings totals). This is acceptable for typical use cases where:
- Users typically have only a few budgets displayed
- The queries use efficient SUM aggregations in the database

If performance becomes an issue with many budgets, a future optimization could:
- Add a single batch query to calculate all totals at once
- Consider denormalizing totals into the budget table (with careful sync)

## References

- Research document: `.claude/thoughts/research/2025-12-29-budget-totals-get-all-endpoint.md`
- Existing correct implementation: `BudgetExtensions.toDetailResponse()` lines 76-115
- Repository calculation methods: `DataService.calculateTotalIncome/Expenses/Savings()` lines 241-253
