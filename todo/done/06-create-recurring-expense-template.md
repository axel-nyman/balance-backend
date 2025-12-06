# Story 6: Create Recurring Expense Template

**As a** user
**I want to** create recurring expense templates
**So that** I can easily add regular expenses to monthly budgets

## Acceptance Criteria

- Can create template with name, amount, and interval
- Intervals: MONTHLY, QUARTERLY, BIANNUALLY, YEARLY
- Can be marked as requiring manual payment
- Amount must be positive
- Name must be unique (excluding soft-deleted templates)
- Tracks last used date for interval calculation

## API Specification

```
POST /api/recurring-expenses
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "recurrenceInterval": "MONTHLY|QUARTERLY|BIANNUALLY|YEARLY",
  "isManual": "boolean"
}

Success Response (201):
{
  "id": "uuid",
  "name": "string",
  "amount": "decimal",
  "recurrenceInterval": "string",
  "isManual": "boolean",
  "lastUsedDate": null,
  "createdAt": "datetime"
}

Error Response (400):
{
  "error": "Recurring expense with this name already exists"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `RecurringExpense` entity: id, name, amount, recurrenceInterval (enum), isManual, lastUsedDate, createdAt, deletedAt
   - Create `RecurrenceInterval` enum: MONTHLY, QUARTERLY, BIANNUALLY, YEARLY

2. **Repository Implementation**

   - Create `RecurringExpenseRepository` extending JpaRepository
   - Add query: `existsByNameAndDeletedAtIsNull(String name)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save recurring expense
     - Method to check name uniqueness
   - Implement in `DataService`

4. **DTOs and Extensions**

   - Create `RecurringExpenseDtos.java` with:
     - `CreateRecurringExpenseRequest` record
     - `RecurringExpenseResponse` record
   - Create `RecurringExpenseExtensions.java` with mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to create recurring expense template
   - Implement in `DomainService`:
     - Validate name uniqueness
     - Validate amount is positive
     - Validate recurrence interval enum
     - Create template with null lastUsedDate
     - Return DTO
   - Add custom exception: `DuplicateRecurringExpenseException`

6. **Controller Implementation**

   - Create `RecurringExpenseController`
   - Add POST /api/recurring-expenses endpoint

7. **Integration Tests**
   - Test successful creation
   - Test duplicate name rejection
   - Test enum validation
   - Test amount validation

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Enum validation implemented
- Code reviewed and approved
