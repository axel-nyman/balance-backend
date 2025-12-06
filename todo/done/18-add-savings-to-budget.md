# Story 18: Add Savings to Budget

**As a** user
**I want to** allocate money to savings in my budget
**So that** I can plan for future financial goals

## Acceptance Criteria

- Can add savings to unlocked budgets only
- Must specify name, amount, and bank account
- Amount must be positive
- Bank account must exist and not be deleted
- Updates budget totals immediately

## API Specification

```
POST /api/budgets/{budgetId}/savings
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
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `BudgetSavings` entity: id, budgetId, bankAccountId, name, amount, createdAt

2. **Repository Implementation**

   - Create `BudgetSavingsRepository` extending JpaRepository
   - Add query: `findAllByBudgetId(UUID budgetId)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save budget savings
   - Implement in `DataService`
   - **Update `isAccountLinkedToUnlockedBudget` method:**
     - Extend existing implementation from Stories 12 and 15
     - Also check if account is used in any BudgetSavings where budget.status = UNLOCKED
     - Add to `BudgetSavingsRepository`: query to check if account is used in savings for unlocked budgets
     - Method should now return true if account is in income OR expenses OR savings for unlocked budgets
     - This completes the implementation started in Story 5

4. **DTOs and Extensions**

   - Add to `BudgetDtos.java`:
     - `CreateBudgetSavingsRequest` record
     - `BudgetSavingsResponse` record
   - Update `BudgetExtensions.java` with savings mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to add savings to budget
   - Implement in `DomainService`:
     - Validate budget exists and is unlocked
     - Validate bank account exists and not deleted
     - Validate amount is positive
     - Create savings item
     - Return DTO

6. **Controller Implementation**

   - Add POST /api/budgets/{budgetId}/savings endpoint

7. **Integration Tests**
   - Test successful creation
   - Test locked budget rejection
   - Test invalid bank account
   - Test negative amount rejection
   - Test that bank account used in unlocked budget savings cannot be deleted (verifies Story 5 constraint)
   - Test that locked budget does not prevent account deletion (only unlocked budgets should prevent deletion)

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
