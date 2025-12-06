# Story 11: List Budgets

**As a** user
**I want to** see all budgets
**So that** I can access historical and current budget data

## Acceptance Criteria

- Returns all budgets
- Includes both locked and unlocked budgets
- Shows calculated totals for each budget
- Shows lock status
- Sorted by year and month descending (newest first)
- Includes basic metadata but not detailed items

## API Specification

```
GET /api/budgets

Success Response (200):
{
  "budgets": [
    {
      "id": "uuid",
      "month": "integer",
      "year": "integer",
      "status": "UNLOCKED|LOCKED",
      "createdAt": "datetime",
      "lockedAt": "datetime",
      "totals": {
        "income": "decimal",
        "expenses": "decimal",
        "savings": "decimal",
        "balance": "decimal"
      }
    }
  ]
}
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `BudgetRepository`: method with custom query for sorting by year DESC, month DESC

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get all budgets sorted by year/month DESC
     - Methods to calculate totals (sum income, expenses, savings for a budget)
   - Implement in `DataService`

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to get all budgets with totals
   - Implement in `DomainService`:
     - Get all budgets from IDataService
     - Calculate totals for each budget
     - Return list of DTOs

4. **Controller Implementation**

   - Add GET /api/budgets endpoint

5. **Integration Tests**
   - Test retrieval of multiple budgets
   - Test sorting order
   - Test totals calculation
   - Test empty list scenario

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Query performance optimized
- Code reviewed and approved
