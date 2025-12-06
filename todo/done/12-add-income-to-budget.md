# Story 12: Add Income to Budget

**As a** user
**I want to** add income items to my monthly budget
**So that** I can track expected income

## Acceptance Criteria

- Can add income to unlocked budgets only
- Must specify name, amount, and bank account
- Amount must be positive
- Bank account must exist and not be deleted
- Name cannot be empty
- Updates budget totals immediately

## API Specification

```
POST /api/budgets/{budgetId}/income
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "bankAccountId": "uuid"
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
  "createdAt": "datetime"
}

Error Response (400):
{
  "error": "Cannot modify locked budget"
}

Error Response (400):
{
  "error": "Bank account not found or deleted"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `BudgetIncome` entity: id, budgetId, bankAccountId, name, amount, createdAt

2. **Repository Implementation**

   - Create `BudgetIncomeRepository` extending JpaRepository
   - Add query: `findAllByBudgetId(UUID budgetId)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save budget income
     - Method to get budget by id
     - Method to get bank account by id
   - Implement in `DataService`
   - **Update `isAccountLinkedToUnlockedBudget` method:**
     - Remove TODO comment from Story 5
     - Implement check: query if account is used in any BudgetIncome where budget.status = UNLOCKED
     - Add to `BudgetIncomeRepository`: query to check if account is used in income for unlocked budgets
     - This method will be further updated in Stories 15 and 18 to also check expenses and savings

4. **DTOs and Extensions**

   - Add to `BudgetDtos.java`:
     - `CreateBudgetIncomeRequest` record
     - `BudgetIncomeResponse` record (with nested bank account)
   - Update `BudgetExtensions.java` with income mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to add income to budget
   - Implement in `DomainService`:
     - Validate budget exists and is unlocked
     - Validate bank account exists and not deleted
     - Validate amount is positive
     - Validate name not empty
     - Create income item
     - Return DTO
   - Add custom exceptions: `BudgetLockedException`, `InvalidBankAccountException`

6. **Controller Implementation**

   - Add POST /api/budgets/{budgetId}/income endpoint to `BudgetController`

7. **Integration Tests**
   - Test successful creation
   - Test locked budget rejection
   - Test invalid bank account
   - Test deleted bank account rejection
   - Test negative amount rejection
   - Test empty name rejection
   - Test that bank account used in unlocked budget income cannot be deleted (verifies Story 5 constraint now works)

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Validation comprehensive
- Code reviewed and approved
