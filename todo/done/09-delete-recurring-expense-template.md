# Story 9: Delete Recurring Expense Template

**As a** user
**I want to** delete unused recurring expense templates
**So that** I can keep my templates organized

## Acceptance Criteria

- Soft delete (sets deletedAt timestamp)
- Does not affect existing budget expenses
- Deleted templates excluded from lists
- Cannot be reactivated

## API Specification

```
DELETE /api/recurring-expenses/{id}

Success Response (204): No Content

Error Response (404):
{
  "error": "Recurring expense not found"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to soft delete recurring expense
   - Implement in `DataService`

2. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete recurring expense
   - Implement in `DomainService`:
     - Validate template exists
     - Set deletedAt timestamp
     - Return success

3. **Controller Implementation**

   - Add DELETE /api/recurring-expenses/{id} endpoint

4. **Integration Tests**
   - Test successful deletion
   - Test exclusion from lists
   - Test existing expenses unaffected

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
