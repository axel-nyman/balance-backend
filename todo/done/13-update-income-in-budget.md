# Story 13: Update Income in Budget

**As a** user
**I want to** update income items in my budget
**So that** I can correct amounts or details

## Acceptance Criteria

- Can update income in unlocked budgets only
- Can update name, amount, and bank account
- Amount must remain positive
- Updates budget totals immediately

## API Specification

```
PUT /api/budgets/{budgetId}/income/{id}
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "bankAccountId": "uuid"
}

Success Response (200):
{
  "id": "uuid",
  "budgetId": "uuid",
  "name": "string",
  "amount": "decimal",
  "bankAccount": {
    "id": "uuid",
    "name": "string"
  },
  "createdAt": "datetime",
  "updatedAt": "datetime"
}

Error Response (400):
{
  "error": "Cannot modify items in locked budget"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Add `updatedAt` field to BudgetIncome

2. **DTOs**

   - Add to `BudgetDtos.java`:
     - `UpdateBudgetIncomeRequest` record

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get budget income by id
   - Implement in `DataService`

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update budget income
   - Implement in `DomainService`:
     - Validate income item exists
     - Validate budget is unlocked
     - Validate bank account if changed
     - Update only provided fields
     - Return DTO

5. **Controller Implementation**

   - Add PUT /api/budgets/{budgetId}/income/{id} endpoint

6. **Integration Tests**
   - Test successful update
   - Test locked budget rejection
   - Test partial updates
   - Test validation

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
