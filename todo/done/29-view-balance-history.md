# Story 29: View Balance History

**As a** user
**I want to** view balance history for any account
**So that** I can track financial changes over time

## Acceptance Criteria

- Shows all balance changes chronologically
- Includes manual and automatic updates
- Differentiates between MANUAL and AUTOMATIC sources
- Paginated for performance (20 items per page default)
- Sorted by date descending (newest first)

## API Specification

```
GET /api/bank-accounts/{id}/balance-history?page=0&size=20

Success Response (200):
{
  "content": [
    {
      "id": "uuid",
      "balance": "decimal",
      "changeAmount": "decimal",
      "changeDate": "datetime",
      "comment": "string",
      "source": "MANUAL|AUTOMATIC",
      "budgetId": "uuid"
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 150,
    "totalPages": 8
  }
}
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `BalanceHistoryRepository`: method with Pageable parameter
     - `findAllByBankAccountIdOrderByChangeDateDesc(UUID accountId, Pageable pageable)`

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get paginated balance history
   - Implement in `DataService`

3. **DTOs**

   - Create `BalanceHistoryDtos.java` with:
     - `BalanceHistoryResponse` record
     - Page metadata in response

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to get balance history with pagination
   - Implement in `DomainService`:
     - Validate account exists
     - Retrieve paginated history from IDataService
     - Return DTO with page metadata

5. **Controller Implementation**

   - Add GET /api/bank-accounts/{id}/balance-history endpoint
   - Support pagination parameters (page, size)

6. **Integration Tests**
   - Test successful retrieval
   - Test pagination works correctly
   - Test sorting order (newest first)
   - Test MANUAL vs AUTOMATIC differentiation
   - Test budgetId is present for AUTOMATIC entries

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Pagination implemented and tested
- Code reviewed and approved
