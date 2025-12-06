# Story 5: Delete Bank Account

**As a** bank account owner
**I want to** delete an account I no longer use
**So that** it doesn't clutter my account list

## Acceptance Criteria

- Users can delete accounts
- Sets deletedAt timestamp (soft delete)
- Deleted accounts excluded from GET all accounts
- Balance history preserved
- Cannot delete account if used in an unlocked budget

## API Specification

```
DELETE /api/bank-accounts/{id}

Success Response (204): No Content

Error Response (400):
{
  "error": "Cannot delete account used in unlocked budget"
}
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `BudgetRepository` (created later): method to check if account is used in unlocked budget

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to check if account is used in unlocked budget
     - Method to soft delete account (set deletedAt)
   - Implement in `DataService`

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to delete bank account
   - Implement in `DomainService`:
     - Check account exists
     - Check no unlocked budgets use this account
     - Set deletedAt timestamp
     - Preserve all related data
   - Add custom exception: `AccountLinkedToBudgetException`

4. **Controller Implementation**

   - Add DELETE /api/bank-accounts/{id} endpoint

5. **Integration Tests**
   - Test successful deletion
   - Test unlocked budget constraint
   - Test data preservation
   - Test exclusion from get all endpoints

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Cascade rules documented
- Code reviewed and approved
