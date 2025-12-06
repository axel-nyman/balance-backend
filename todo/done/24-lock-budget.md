# Story 24: Lock Budget

**As a** user
**I want to** lock a completed budget
**So that** it's finalized and prohibits accidental changes

## Acceptance Criteria

- Budget must have zero balance (income - expenses - savings = 0)
- Cannot lock already locked budget
- Sets status to LOCKED and records lockedAt timestamp
- Updates `lastUsedDate` and `lastUsedBudgetId` on recurring expense templates referenced by budget expenses
- Locking is atomic (all-or-nothing transaction)

## API Specification

```
PUT /api/budgets/{id}/lock

Success Response (200):
{
  "id": "uuid",
  "month": "integer",
  "year": "integer",
  "status": "LOCKED",
  "lockedAt": "datetime",
  "totals": {
    "income": "decimal",
    "expenses": "decimal",
    "savings": "decimal",
    "balance": "decimal"
  }
}

Error Response (400):
{
  "error": "Budget must have zero balance. Current balance: 250.00"
}

Error Response (400):
{
  "error": "Budget is already locked"
}
```

## Domain Model Changes

- Add `lastUsedBudgetId` field to `RecurringExpense` entity (UUID, nullable)
  - Tracks which budget last used this recurring expense template
  - Used during unlock to restore previous lastUsedDate

## Technical Implementation

1. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to lock budget
   - Implement in `DomainService`:
     - Validate budget exists and is unlocked
     - Calculate total balance using IDataService
     - Verify balance equals zero (use BigDecimal.ZERO.compareTo())
     - Begin transaction
     - Update budget status to LOCKED
     - Set lockedAt timestamp
     - Save budget via IDataService
     - Commit transaction
     - Return DTO
   - Add custom exceptions: `BudgetNotBalancedException`, `BudgetAlreadyLockedException`

2. **Data Service Layer**

   - Add to `IDataService`:
     - Methods to calculate budget totals (sum income, expenses, savings)
     - Method to get all budget expenses for a budget
     - Method to get recurring expense by id
     - Method to save recurring expense
   - Implement in `DataService`

3. **Recurring Expense Update Logic**

   - Add to `IDomainService`:
     - Method to update recurring expenses for budget (called during lock)
   - Implement in `DomainService`:
     - **updateRecurringExpensesForBudget(UUID budgetId, LocalDateTime lockedAt)**:
       - Load all budget expenses for this budget from IDataService
       - Filter to only those with non-null `recurringExpenseId`
       - Get unique set of recurring expense IDs
       - For each unique recurring expense:
         - Load recurring expense from IDataService
         - Update `lastUsedDate` to budget's `lockedAt` timestamp
         - Update `lastUsedBudgetId` to this budget's id
         - Save recurring expense via IDataService
   - Integration with `lockBudget()` method:
     - After todo list generation (Story 25)
     - After balance updates (Story 26)
     - Before committing transaction
     - Call `updateRecurringExpensesForBudget(budgetId, lockedAt)`
     - If update fails, rollback entire transaction

4. **Controller Implementation**

   - Add PUT /api/budgets/{id}/lock endpoint

5. **Integration Tests**
   - Test successful locking (balanced budget)
   - Test non-zero balance rejection
   - Test already locked budget rejection
   - Test transaction rollback on failure
   - Test lockedAt timestamp is set correctly
   - Test recurring expense lastUsedDate and lastUsedBudgetId are updated when budget with linked expenses is locked
   - Test multiple budgets locking with same recurring expense (latest lock wins)

## Definition of Done

- All acceptance criteria met
- Transaction handling tested
- Integration tests passing
- API documentation updated
- Code reviewed and approved
