# Story 14: Delete Income from Budget

**As a** user
**I want to** delete income items from my budget
**So that** I can remove incorrect entries

## Acceptance Criteria

- Can delete from unlocked budgets only
- Hard delete (immediate removal)
- Updates budget totals immediately

## API Specification

```
DELETE /api/budgets/{budgetId}/income/{id}

Success Response (204): No Content

Error Response (400):
{
  "error": "Cannot modify items in locked budget"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to delete budget income by id
   - Implement in `DataService`

2. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete budget income
   - Implement in `DomainService`:
     - Validate income exists
     - Validate budget is unlocked
     - Delete income record

3. **Controller Implementation**

   - Add DELETE /api/budgets/{budgetId}/income/{id} endpoint

4. **Integration Tests**
   - Test successful deletion
   - Test locked budget rejection

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
