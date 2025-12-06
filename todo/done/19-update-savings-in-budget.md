# Story 19: Update Savings in Budget

**As a** user
**I want to** update savings allocations in my budget
**So that** I can adjust my savings goals

## Acceptance Criteria

- Can update savings in unlocked budgets only
- Can update name, amount, and bank account
- Amount must remain positive
- Updates budget totals immediately

## API Specification

```
PUT /api/budgets/{budgetId}/savings/{id}
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
```

## Technical Implementation

1. **Domain Model Changes**

   - Add `updatedAt` field to BudgetSavings

2. **DTOs**

   - Add to `BudgetDtos.java`:
     - `UpdateBudgetSavingsRequest` record

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get budget savings by id
   - Implement in `DataService`

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update budget savings
   - Implement in `DomainService`:
     - Validate savings exists
     - Validate budget is unlocked
     - Update only provided fields
     - Return DTO

5. **Controller Implementation**

   - Add PUT /api/budgets/{budgetId}/savings/{id} endpoint

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
