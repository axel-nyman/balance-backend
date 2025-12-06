# Story 27: Unlock Budget

**As a** user
**I want to** unlock a previously locked budget
**So that** I can make corrections if needed

## Acceptance Criteria

- Can only unlock the most recent budget (by year DESC, month DESC)
- Unlocking reverses all automatic balance updates for this budget
- Reversal simply subtracts savings that were added during lock
- Restores `lastUsedDate` and `lastUsedBudgetId` on recurring expense templates to previous locked budget state
- Deletes associated todo list
- Clears lockedAt timestamp
- Sets status back to UNLOCKED
- All reversals in single transaction

## API Specification

```
PUT /api/budgets/{id}/unlock

Success Response (200):
{
  "id": "uuid",
  "month": "integer",
  "year": "integer",
  "status": "UNLOCKED",
  "lockedAt": null
}

Error Response (400):
{
  "error": "Only the most recent budget can be unlocked"
}

Error Response (400):
{
  "error": "Budget is not locked"
}
```

## Balance Reversal Logic

```
For each BalanceHistory entry with:
  - source = AUTOMATIC
  - budgetId = {this budget's id}

Do:
  1. Load the associated bank account
  2. Subtract the change amount that was added:
     account.currentBalance = account.currentBalance - history.changeAmount
  3. Save the account
  4. Delete the history entry
```

## Recurring Expense Reversal Logic

```
For each recurring expense where lastUsedBudgetId == this budget's id:
  1. Find all OTHER locked budgets that have expenses linking to this recurring expense
  2. Order by year DESC, month DESC (most recent first)
  3. Filter out the current budget being unlocked
  4. If a previous locked budget found:
     - Set lastUsedDate to that budget's lockedAt timestamp
     - Set lastUsedBudgetId to that budget's id
  5. If no previous locked budget found:
     - Set lastUsedDate to null
     - Set lastUsedBudgetId to null
  6. Save recurring expense
```

**Query Logic:**
```java
// Find previous locked budget that used this recurring expense
// Join: Budget -> BudgetExpense -> RecurringExpense
// Filter: budget.status = LOCKED AND budgetExpense.recurringExpenseId = {recurringExpenseId}
// Order: year DESC, month DESC
// Exclude: current budget being unlocked
// Take first result (most recent)
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `BudgetRepository`:
     - Custom query to find most recent budget (ORDER BY year DESC, month DESC LIMIT 1)
     - Custom query to find locked budgets that use a specific recurring expense, ordered by year DESC, month DESC
   - Add to `BalanceHistoryRepository`:
     - `findAllByBudgetId(UUID budgetId)`
     - `deleteAllByBudgetId(UUID budgetId)`
   - Add to `BudgetExpenseRepository`:
     - Query to find all budget expenses with a specific recurringExpenseId

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get most recent budget
     - Method to get all balance history entries by budget id
     - Method to delete balance history entries by budget id
     - Method to delete todo list by budget id
     - Method to get all budget expenses for this budget
     - Method to find locked budgets using a specific recurring expense (ordered by year DESC, month DESC)
     - Method to get recurring expense by id
     - Method to save recurring expense
   - Implement in `DataService`

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to unlock budget
     - Method to restore recurring expenses for budget (called during unlock)
   - Implement in `DomainService`:
     - Validate budget exists and is locked
     - Get most recent budget from IDataService
     - Validate this is the most recent budget (compare ids)
     - Begin transaction:
       - **Reverse balance changes:**
         - Find all AUTOMATIC balance history entries with budgetId = {id}
         - For each history entry:
           - Load associated bank account from IDataService
           - Reverse the balance change:
             - account.currentBalance -= history.changeAmount
           - Save account via IDataService
         - Delete all balance history entries for this budget
       - **Restore recurring expenses:**
         - Load all budget expenses for this budget from IDataService
         - Get unique set of recurring expense IDs that have non-null recurringExpenseId
         - For each unique recurring expense ID:
           - Load recurring expense from IDataService
           - Check if lastUsedBudgetId == this budget's id (only restore if this budget was the last to use it)
           - If yes:
             - Find previous locked budget using this recurring expense (via IDataService query)
             - Filter out current budget being unlocked
             - If previous budget found:
               - Set lastUsedDate = previous budget's lockedAt
               - Set lastUsedBudgetId = previous budget's id
             - Else (no previous budget):
               - Set lastUsedDate = null
               - Set lastUsedBudgetId = null
             - Save recurring expense via IDataService
       - Delete todo list for this budget via IDataService
       - Update budget status to UNLOCKED
       - Set lockedAt = null
       - Save budget via IDataService
     - Commit transaction
     - Return DTO
   - Add custom exceptions: `NotMostRecentBudgetException`, `BudgetNotLockedException`

4. **Controller Implementation**

   - Add PUT /api/budgets/{id}/unlock endpoint

5. **Integration Tests**
   - Test successful unlocking of most recent budget
   - Test non-most-recent budget rejection (create 2 budgets, lock both, try to unlock older one)
   - Test already unlocked budget rejection
   - Test balance reversals restore exact original balances
   - Test todo list deletion
   - Test transaction rollback on failure
   - Test can lock again after unlock (and balances work correctly)
   - Test with the same example from Story 26:
     - Lock: A: 500→600, B: 300→400, C: 1000→1100
     - Unlock: A: 600→500, B: 400→300, C: 1100→1000
   - **Recurring expense restore tests:**
     - Test lastUsedDate restored to previous locked budget when unlocking
     - Test lastUsedDate set to null when no other locked budgets use the template
     - Test sequence: Lock Jan (using template X), Lock Feb (using template X), Lock Mar (using template X), Unlock Mar → verify lastUsedDate = Feb's lockedAt timestamp
     - Test that unlocking doesn't affect recurring expenses used by other locked budgets (lastUsedBudgetId mismatch)
     - Test can lock again after unlock and recurring expense lastUsedDate updates correctly

## Definition of Done

- All acceptance criteria met
- Transaction handling tested thoroughly
- Integration tests passing
- API documentation updated
- Reversal logic verified: simply subtracts the savings that were added
- Recurring expense restore logic verified: restores to previous locked budget or null
- Code reviewed and approved
