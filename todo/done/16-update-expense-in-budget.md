# Story 16: Update Expense in Budget

**As a** user
**I want to** update expense items in my budget
**So that** I can adjust amounts or payment details

## Acceptance Criteria

- Can update expenses in unlocked budgets only
- Can update name, amount, bank account, payment flags, and date
- Amount must remain positive
- Updates budget totals immediately

## API Specification

```
PUT /api/budgets/{budgetId}/expenses/{id}
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "bankAccountId": "uuid",
  "deductedAt": "date",
  "isManual": "boolean"
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
  "recurringExpenseId": "uuid",
  "deductedAt": "date",
  "isManual": "boolean",
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

   - Add `updatedAt` field to BudgetExpense

2. **DTOs**

   - Add to `BudgetDtos.java`:
     - `UpdateBudgetExpenseRequest` record

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get budget expense by id
   - Implement in `DataService`

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update budget expense
   - Implement in `DomainService`:
     - Validate expense exists
     - Validate budget is unlocked
     - Validate new bank account if changed
     - Update fields
     - Return DTO

5. **Controller Implementation**

   - Add PUT /api/budgets/{budgetId}/expenses/{id} endpoint

6. **Integration Tests**
   - Test successful update
   - Test locked budget rejection
   - Test bank account validation
   - Test partial updates

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
