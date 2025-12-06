# Story 22: Delete Unlocked Budget

**As a** user
**I want to** delete an unlocked budget
**So that** I can remove budgets created by mistake

## Acceptance Criteria

- Can only delete unlocked budgets
- Hard delete (immediate removal)
- Cascades to delete all budget items (income, expenses, savings)
- Cannot delete locked budgets

## API Specification

```
DELETE /api/budgets/{id}

Success Response (204): No Content

Error Response (400):
{
  "error": "Cannot delete locked budget. Unlock it first."
}

Error Response (404):
{
  "error": "Budget not found"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to delete budget by id (with cascade)
   - Implement in `DataService`

2. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete budget
   - Implement in `DomainService`:
     - Validate budget exists
     - Validate budget is unlocked
     - Delete budget (cascade handled by JPA)
   - Add custom exception: `CannotDeleteLockedBudgetException`

3. **Controller Implementation**

   - Add DELETE /api/budgets/{id} endpoint

4. **Integration Tests**
   - Test successful deletion
   - Test locked budget rejection
   - Test cascade deletion of income/expenses/savings
   - Test ability to create new budget for same month/year

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Cascade rules documented
- Code reviewed and approved
