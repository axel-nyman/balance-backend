# Story 7: List Recurring Expenses

**As a** user
**I want to** see all active recurring expenses
**So that** I can manage regular expenses

## Acceptance Criteria

- Returns all active (non-deleted) recurring expenses
- Shows last used date for each
- Indicates if/when it's due based on interval
- Sorted by name alphabetically
- Excludes soft-deleted templates

## API Specification

```
GET /api/recurring-expenses

Success Response (200):
{
  "expenses": [
    {
      "id": "uuid",
      "name": "string",
      "amount": "decimal",
      "recurrenceInterval": "string",
      "isManual": "boolean",
      "lastUsedDate": "date",
      "nextDueDate": "date",
      "isDue": "boolean",
      "createdAt": "datetime"
    }
  ]
}
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `RecurringExpenseRepository`: `findAllByDeletedAtIsNull()` query

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get all active recurring expenses
   - Implement in `DataService`

3. **DTOs**

   - Add to `RecurringExpenseDtos.java`:
     - `RecurringExpenseListItemResponse` record with nextDueDate, isDue fields

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to get all recurring expenses with due date calculations
   - Implement in `DomainService`:
     - Get all active templates from IDataService
     - Calculate next due date based on interval
     - Determine if currently due
     - Sort by name
     - Return list of DTOs

5. **Controller Implementation**

   - Add GET /api/recurring-expenses endpoint

6. **Integration Tests**
   - Test retrieval
   - Test due date calculation
   - Test soft-delete exclusion
   - Test sorting

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Due date logic tested
- Code reviewed and approved
