# Story 15: Add Expense to Budget

**As a** user
**I want to** add expense items to my monthly budget
**So that** I can track planned expenditures and bills

## Acceptance Criteria

- Can add expenses to unlocked budgets only
- Must specify name, amount, and bank account
- Can mark as needing manual payment
- Can set optional deduction date
- Can link to recurring expense template
- Amount must be positive
- Bank account must exist and not be deleted

## API Specification

```
POST /api/budgets/{budgetId}/expenses
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "bankAccountId": "uuid",
  "recurringExpenseId": "uuid" (optional),
  "deductedAt": "date" (optional),
  "isManual": "boolean"
}

Success Response (201):
{
  "id": "uuid",
  "budgetId": "uuid",
  "name": "string",
  "amount": "decimal",
  "bankAccount": {
    "id": "uuid",
    "name": "string",
    "currentBalance": "decimal"
  },
  "recurringExpenseId": "uuid",
  "deductedAt": "date",
  "isManual": "boolean",
  "createdAt": "datetime"
}

Error Response (400):
{
  "error": "Cannot modify locked budget"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `BudgetExpense` entity: id, budgetId, name, amount, bankAccountId, recurringExpenseId, deductedAt, isManual, createdAt

2. **Repository Implementation**

   - Create `BudgetExpenseRepository` extending JpaRepository
   - Add query: `findAllByBudgetId(UUID budgetId)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save budget expense
     - Method to get recurring expense by id (for validation)
   - Implement in `DataService`
   - **Update `isAccountLinkedToUnlockedBudget` method:**
     - Extend existing implementation from Story 12
     - Also check if account is used in any BudgetExpense where budget.status = UNLOCKED
     - Add to `BudgetExpenseRepository`: query to check if account is used in expenses for unlocked budgets
     - Method should now return true if account is in either income OR expenses for unlocked budgets

4. **DTOs and Extensions**

   - Add to `BudgetDtos.java`:
     - `CreateBudgetExpenseRequest` record
     - `BudgetExpenseResponse` record
   - Update `BudgetExtensions.java` with expense mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to add expense to budget
   - Implement in `DomainService`:
     - Validate budget exists and is unlocked
     - Validate bank account exists and not deleted
     - Validate recurring expense exists if provided
     - Validate amount is positive
     - Create expense item
     - Return DTO

6. **Controller Implementation**

   - Add POST /api/budgets/{budgetId}/expenses endpoint

7. **Integration Tests**
   - Test successful creation
   - Test locked budget rejection
   - Test invalid bank account
   - Test recurring expense link
   - Test optional date handling
   - Test that bank account used in unlocked budget expense cannot be deleted (verifies Story 5 constraint)

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Date handling implemented
- Code reviewed and approved
