---
date: 2025-12-29T14:30:00+01:00
researcher: Claude Code
git_commit: 688690a452818d38448de96c2f4ff126167b4f01
branch: main
repository: balance-backend
topic: "Budget Totals Bug in Get All Budgets Endpoint"
tags: [research, codebase, budget, totals, api-endpoint]
status: complete
last_updated: 2025-12-29
last_updated_by: Claude Code
---

# Research: Budget Totals Bug in Get All Budgets Endpoint

**Date**: 2025-12-29T14:30:00+01:00
**Researcher**: Claude Code
**Git Commit**: 688690a452818d38448de96c2f4ff126167b4f01
**Branch**: main
**Repository**: balance-backend

## Research Question

There is a bug with the get all budgets endpoint where the "totals" section doesn't display the contents of the budget. When creating a budget and adding income/expenses/savings to it, these amounts are not visible when fetching all budgets. However, fetching details for a specific budget endpoint returns correct totals.

## Summary

The issue lies in the `BudgetExtensions.toResponse()` method which always returns zero values for the totals section. This method is used by the `getAllBudgets()` endpoint. In contrast, the `toDetailResponse()` method correctly calculates totals from the income, expenses, and savings lists - this method is used by the `getBudgetDetails()` endpoint.

The `toResponse()` method does not receive income/expenses/savings data to calculate totals because it only takes a `Budget` entity as input, and the `Budget` entity does not have a direct relationship to its line items (income, expenses, savings are separate tables linked by `budgetId`).

## Detailed Findings

### Endpoint Flow Comparison

#### GET /api/budgets (List All Budgets)
1. `BudgetController.getAllBudgets()` (line 29-32)
2. Calls `domainService.getAllBudgets()`
3. `DomainService.getAllBudgets()` (line 411-418):
   - Gets budgets from `dataService.getAllBudgetsSorted()`
   - Maps each budget using `BudgetExtensions::toResponse`
4. `BudgetExtensions.toResponse(Budget budget)` (lines 15-32):
   - **Returns hardcoded zeros for totals**

#### GET /api/budgets/{id} (Get Single Budget Details)
1. `BudgetController.getBudgetDetails()` (line 34-37)
2. Calls `domainService.getBudgetDetails(id)`
3. `DomainService.getBudgetDetails()` (line 422-430):
   - Gets budget by ID
   - **Also fetches income, expenses, and savings lists**
   - Maps using `BudgetExtensions.toDetailResponse(budget, incomeList, expensesList, savingsList)`
4. `BudgetExtensions.toDetailResponse()` (lines 76-115):
   - **Calculates totals from the provided lists**

### The Core Issue: BudgetExtensions.toResponse()

Located at `src/main/java/org/example/axelnyman/main/domain/extensions/BudgetExtensions.java:15-32`

```java
public static BudgetResponse toResponse(Budget budget) {
    BudgetTotalsResponse totals = new BudgetTotalsResponse(
            BigDecimal.ZERO,  // income - always zero
            BigDecimal.ZERO,  // expenses - always zero
            BigDecimal.ZERO,  // savings - always zero
            BigDecimal.ZERO   // balance - always zero
    );

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

This method only accepts a `Budget` entity and has no way to access the income, expenses, or savings associated with that budget.

### Budget Entity Structure

Located at `src/main/java/org/example/axelnyman/main/domain/model/Budget.java`

The `Budget` entity does NOT have `@OneToMany` relationships to its line items:
- No `List<BudgetIncome>` field
- No `List<BudgetExpense>` field
- No `List<BudgetSavings>` field

The line items are stored in separate tables (`budget_income`, `budget_expense`, `budget_savings`) and reference the budget via a `budgetId` column, but there is no bidirectional mapping in the entity.

### Related Entities Relationship

`BudgetIncome` (and similarly `BudgetExpense`, `BudgetSavings`) has:
```java
@Column(nullable = false)
private UUID budgetId;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "budgetId", insertable = false, updatable = false)
private Budget budget;
```

The relationship is unidirectional from line items to Budget. Budget has no reference back to its line items.

### DomainService Implementation

**getAllBudgets() method** (line 411-418):
```java
public BudgetListResponse getAllBudgets() {
    List<Budget> budgets = dataService.getAllBudgetsSorted();

    List<BudgetResponse> budgetResponses = budgets.stream()
            .map(BudgetExtensions::toResponse)
            .toList();

    return new BudgetListResponse(budgetResponses);
}
```

This method only fetches `Budget` entities and does not fetch the associated income/expenses/savings for each budget.

**getBudgetDetails() method** (line 422-430):
```java
public BudgetDetailResponse getBudgetDetails(UUID id) {
    Budget budget = dataService.getBudgetById(id)
            .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + id));

    List<BudgetIncome> incomeList = dataService.getBudgetIncomeByBudgetId(id);
    List<BudgetExpense> expensesList = dataService.getBudgetExpensesByBudgetId(id);
    List<BudgetSavings> savingsList = dataService.getBudgetSavingsByBudgetId(id);

    return BudgetExtensions.toDetailResponse(budget, incomeList, expensesList, savingsList);
}
```

This method explicitly fetches all line items and passes them to the mapping method.

### Test Coverage Gap

The integration tests in `BudgetIntegrationTest.java` reveal a coverage gap:

1. **Test `shouldReturnZeroTotalsForBudgetsWithoutItems`** (lines 449-460): Tests getAllBudgets for a budget WITHOUT items - expects zeros (correct behavior for empty budget)

2. **Test `shouldListAllBudgets`** (lines 437-446): Only verifies totals object EXISTS but doesn't verify actual values

3. **Test `shouldGetBudgetDetails`** (lines 698-708): Verifies correct totals calculation for the single budget detail endpoint

**Missing test**: There is no test that verifies the getAllBudgets endpoint returns correct totals for budgets WITH income/expenses/savings items.

## Code References

- `BudgetController.java:29-32` - getAllBudgets endpoint definition
- `BudgetController.java:34-37` - getBudgetDetails endpoint definition
- `DomainService.java:411-418` - getAllBudgets implementation
- `DomainService.java:422-430` - getBudgetDetails implementation
- `BudgetExtensions.java:15-32` - toResponse method with hardcoded zero totals
- `BudgetExtensions.java:76-115` - toDetailResponse method with correct totals calculation
- `Budget.java:1-120` - Budget entity without line item relationships
- `BudgetIncome.java:41-43` - ManyToOne relationship from income to budget
- `DataService.java:168-169` - getAllBudgetsSorted returns only Budget entities
- `DataService.java:225-237` - Methods to fetch line items by budgetId
- `BudgetIntegrationTest.java:449-460` - Test for zero totals on empty budget
- `BudgetIntegrationTest.java:437-446` - Test that only checks totals existence

## Architecture Documentation

### Data Flow for Budget Totals

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          GET /api/budgets                               │
├─────────────────────────────────────────────────────────────────────────┤
│ Controller → DomainService.getAllBudgets()                              │
│           → DataService.getAllBudgetsSorted()                           │
│           → Returns List<Budget> (no line items)                        │
│           → BudgetExtensions.toResponse(budget)                         │
│           → Returns BudgetTotalsResponse(0, 0, 0, 0) ← HARDCODED ZEROS  │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                       GET /api/budgets/{id}                             │
├─────────────────────────────────────────────────────────────────────────┤
│ Controller → DomainService.getBudgetDetails(id)                         │
│           → DataService.getBudgetById(id)                               │
│           → DataService.getBudgetIncomeByBudgetId(id)                   │
│           → DataService.getBudgetExpensesByBudgetId(id)                 │
│           → DataService.getBudgetSavingsByBudgetId(id)                  │
│           → BudgetExtensions.toDetailResponse(budget, income, exp, sav) │
│           → Calculates totals from lists ← CORRECT CALCULATION          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Database Schema Relationships

```
budgets (Budget entity)
├── id (PK)
├── month
├── year
├── status
├── created_at
├── updated_at
├── locked_at
└── deleted_at

budget_income (BudgetIncome entity)
├── id (PK)
├── budget_id (FK → budgets.id) - NOT bidirectionally mapped in Budget
├── bank_account_id
├── name
├── amount
├── created_at
└── updated_at

budget_expense (BudgetExpense entity)
├── id (PK)
├── budget_id (FK → budgets.id) - NOT bidirectionally mapped in Budget
├── bank_account_id
├── name
├── amount
├── recurring_expense_id
├── deducted_at
├── is_manual
├── created_at
└── updated_at

budget_savings (BudgetSavings entity)
├── id (PK)
├── budget_id (FK → budgets.id) - NOT bidirectionally mapped in Budget
├── bank_account_id
├── name
├── amount
├── created_at
└── updated_at
```

## Historical Context (from thoughts/)

No existing research documents found related to this specific issue.

## Related Research

No related research documents found in `.claude/thoughts/research/`.

## Open Questions

1. Should the `Budget` entity include bidirectional `@OneToMany` relationships to income, expenses, and savings for easier data access?
2. Should totals be calculated at the database level (e.g., using `@Formula` annotation or native query with aggregation)?
3. Is there a performance consideration for fetching totals for all budgets (N+1 query concern)?
4. Should totals be cached or stored as denormalized columns in the budget table?
