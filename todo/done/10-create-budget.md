# Story 10: Create Budget

**As a** user
**I want to** create a monthly budget
**So that** I can plan my finances for a specific month

## Acceptance Criteria

- Can create budget for any month/year combination
- Only one budget allowed per month/year
- Budget starts in UNLOCKED status
- Cannot create duplicate budget for same month/year
- Month must be valid (1-12)
- Year must be reasonable (e.g., 2000-2100)
- Only one budget in UNLOCKED status allowed at a time

## API Specification

```
POST /api/budgets
Request Body:
{
  "month": "integer",
  "year": "integer"
}

Success Response (201):
{
  "id": "uuid",
  "month": "integer",
  "year": "integer",
  "status": "UNLOCKED",
  "createdAt": "datetime",
  "totals": {
    "income": "decimal",
    "expenses": "decimal",
    "savings": "decimal",
    "balance": "decimal"
  }
}

Error Response (400):
{
  "error": "Budget already exists for this month"
}

Error Response (400):
{
  "error": "Invalid month value. Must be between 1 and 12"
}

Error Response (400):
{
  "error": "Another budget is currently unlocked. Lock or delete it before creating a new budget."
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `Budget` entity: id, month, year, status (enum: UNLOCKED/LOCKED), createdAt, lockedAt
   - Create `BudgetStatus` enum: UNLOCKED, LOCKED
   - Add unique constraint on (month, year)

2. **Repository Implementation**

   - Create `BudgetRepository` extending JpaRepository
   - Add queries:
     - `existsByMonthAndYear(int month, int year)`
     - `existsByStatus(BudgetStatus status)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save budget
     - Method to check if budget exists for month/year
     - Method to check if unlocked budget exists
   - Implement in `DataService`

4. **DTOs and Extensions**

   - Create `BudgetDtos.java` with:
     - `CreateBudgetRequest` record
     - `BudgetResponse` record
     - `BudgetTotalsResponse` record
   - Create `BudgetExtensions.java` with mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to create budget
   - Implement in `DomainService`:
     - Validate month (1-12)
     - Validate year (reasonable range)
     - Check no other unlocked budget exists
     - Check for existing budget for this month/year
     - Create budget with UNLOCKED status
     - Initialize totals to zero
     - Return DTO
   - Add custom exceptions: `DuplicateBudgetException`, `InvalidMonthException`, `InvalidYearException`, `UnlockedBudgetExistsException`

6. **Controller Implementation**

   - Create `BudgetController` with `@RestController`
   - Implement POST /api/budgets endpoint

7. **Integration Tests**
   - Test successful creation
   - Test duplicate budget prevention
   - Test invalid month values
   - Test invalid year values
   - Test unlocked budget constraint
   - Test initial status and totals

## Definition of Done

- All acceptance criteria met
- Unit tests for validation logic
- Integration tests passing
- API documentation updated
- Database constraints in place
- Code reviewed and approved
