# Story 2: List Bank Accounts

**As a** user
**I want to** see all bank accounts
**So that** I can get a complete financial overview

## Acceptance Criteria

- Returns all active accounts
- Shows total balance across all accounts
- Excludes soft-deleted accounts

## API Specification

```
GET /api/bank-accounts

Success Response (200):
{
  "totalBalance": "decimal",
  "accountCount": "integer",
  "accounts": [
    {
      "id": "uuid",
      "name": "string",
      "description": "string",
      "currentBalance": "decimal",
      "createdAt": "datetime"
    }
  ]
}
```

## Technical Implementation

1. **Repository Implementation**

   - Add to `BankAccountRepository`: `findAllByDeletedAtIsNull()` query

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get all active bank accounts
   - Implement in `DataService`

3. **DTOs**

   - Add to `BankAccountDtos.java`:
     - `BankAccountListResponse` record with totalBalance, accountCount, accounts

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to get all bank accounts with totals
   - Implement in `DomainService`:
     - Get all active accounts from IDataService
     - Calculate total balance
     - Sort by name or creation date
     - Return DTO with aggregated data

5. **Controller Implementation**

   - Add GET /api/bank-accounts endpoint to `BankAccountController`

6. **Integration Tests**
   - Test retrieval of all accounts
   - Test total balance calculation
   - Test exclusion of deleted accounts

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Performance optimized for large number of accounts
- Code reviewed and approved
