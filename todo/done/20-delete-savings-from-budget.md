# Story 20: Delete Savings from Budget

**As a** user
**I want to** delete savings items from my budget
**So that** I can remove unnecessary allocations

## Acceptance Criteria

- Can delete from unlocked budgets only
- Hard delete (immediate removal)
- Updates budget totals immediately

## API Specification

```
DELETE /api/budgets/{budgetId}/savings/{id}

Success Response (204): No Content

Error Response (400):
{
  "error": "Cannot modify items in locked budget"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to delete budget savings by id
   - Implement in `DataService`

2. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete budget savings
   - Implement in `DomainService`:
     - Validate savings exists
     - Validate budget is unlocked
     - Delete savings record

3. **Controller Implementation**

   - Add DELETE /api/budgets/{budgetId}/savings/{id} endpoint

4. **Integration Tests**
   - Test successful deletion
   - Test locked budget rejection

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
