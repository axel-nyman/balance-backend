# Story 21: View Budget Details

**As a** user
**I want to** view complete details of a specific budget
**So that** I can see all income, expenses, and savings items

## Acceptance Criteria

- Returns full budget details including all items
- Groups items by type (income, expenses, savings)
- Shows running totals and final balance
- Includes metadata about creation and locking
- Shows linked bank accounts for each item

## API Specification

```
GET /api/budgets/{id}

Success Response (200):
{
  "id": "uuid",
  "month": "integer",
  "year": "integer",
  "status": "UNLOCKED|LOCKED",
  "createdAt": "datetime",
  "lockedAt": "datetime",
  "income": [
    {
      "id": "uuid",
      "name": "string",
      "amount": "decimal",
      "bankAccount": {
        "id": "uuid",
        "name": "string"
      }
    }
  ],
  "expenses": [
    {
      "id": "uuid",
      "name": "string",
      "amount": "decimal",
      "bankAccount": {
        "id": "uuid",
        "name": "string"
      },
      "recurringExpenseId": "uuid",
      "deductedAt": "date",
      "isManual": "boolean"
    }
  ],
  "savings": [
    {
      "id": "uuid",
      "name": "string",
      "amount": "decimal",
      "bankAccount": {
        "id": "uuid",
        "name": "string"
      }
    }
  ],
  "totals": {
    "income": "decimal",
    "expenses": "decimal",
    "savings": "decimal",
    "balance": "decimal"
  }
}

Error Response (404):
{
  "error": "Budget not found"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get budget by id with all related items
     - Methods to load income, expenses, savings for a budget
   - Implement in `DataService` (optimize to avoid N+1 queries)

2. **DTOs**

   - Add to `BudgetDtos.java`:
     - `BudgetDetailResponse` record with nested lists
     - Item response records with nested bank account info

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to get budget details
   - Implement in `DomainService`:
     - Validate budget exists
     - Load all related items efficiently from IDataService
     - Calculate totals
     - Group items by type
     - Return comprehensive DTO

4. **Controller Implementation**

   - Add GET /api/budgets/{id} endpoint

5. **Integration Tests**
   - Test successful retrieval
   - Test complete data loading
   - Test totals calculation
   - Test non-existent budget

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- N+1 queries avoided
- Code reviewed and approved
