# Story 17: Delete Expense from Budget

**As a** user
**I want to** delete expense items from my budget
**So that** I can remove unnecessary expenses

## Acceptance Criteria

- Can delete from unlocked budgets only
- Hard delete (immediate removal)
- Does not affect recurring expense template
- Updates budget totals immediately

## API Specification

```
DELETE /api/budgets/{budgetId}/expenses/{id}

Success Response (204): No Content

Error Response (400):
{
  "error": "Cannot modify items in locked budget"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to delete budget expense by id
   - Implement in `DataService`

2. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete budget expense
   - Implement in `DomainService`:
     - Validate expense exists
     - Validate budget is unlocked
     - Delete expense record

3. **Controller Implementation**

   - Add DELETE /api/budgets/{budgetId}/expenses/{id} endpoint

4. **Integration Tests**
   - Test successful deletion
   - Test locked budget rejection
   - Test recurring expense unaffected

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
