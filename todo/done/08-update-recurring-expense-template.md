# Story 8: Update Recurring Expense Template

**As a** user
**I want to** update recurring expense templates
**So that** I can adjust for price changes

## Acceptance Criteria

- Can update name, amount, interval, and auto-pay flag
- Name must remain unique among non-deleted templates
- Does not affect existing budget expenses
- Amount must be positive
- Cannot update deleted templates

## API Specification

```
PUT /api/recurring-expenses/{id}
Request Body:
{
  "name": "string",
  "amount": "decimal",
  "recurrenceInterval": "MONTHLY|QUARTERLY|BIANNUALLY|YEARLY",
  "isManual": "boolean"
}

Success Response (200):
{
  "id": "uuid",
  "name": "string",
  "amount": "decimal",
  "recurrenceInterval": "string",
  "isManual": "boolean",
  "lastUsedDate": "date",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Add `updatedAt` field to RecurringExpense

2. **DTOs**

   - Add to `RecurringExpenseDtos.java`:
     - `UpdateRecurringExpenseRequest` record

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to check name uniqueness excluding specific id
   - Implement in `DataService`

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update recurring expense
   - Implement in `DomainService`:
     - Validate template exists and not deleted
     - Validate name uniqueness if changed
     - Validate amount is positive
     - Update fields
     - Do not modify lastUsedDate
     - Return DTO

5. **Controller Implementation**

   - Add PUT /api/recurring-expenses/{id} endpoint

6. **Integration Tests**
   - Test successful update
   - Test duplicate name rejection
   - Test deleted template rejection
   - Test existing expenses unaffected

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
