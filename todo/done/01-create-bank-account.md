# Story 1: Create Bank Account

**As a** user
**I want to** create a bank account representation
**So that** I can track my financial accounts in the app

## Acceptance Criteria

- Can create bank account with name, description, and initial balance
- Name is required and must be non-empty
- Initial balance defaults to 0 if not provided
- Initial balance creates first history entry
- Account names must be unique
- Created timestamp is set automatically

## API Specification

```
POST /api/bank-accounts
Request Body:
{
  "name": "string",
  "description": "string",
  "initialBalance": "decimal"
}

Success Response (201):
{
  "id": "uuid",
  "name": "string",
  "description": "string",
  "currentBalance": "decimal",
  "createdAt": "datetime"
}

Error Response (400):
{
  "error": "Bank account name already exists"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `BankAccount` entity: id, name, description, currentBalance, createdAt, deletedAt
   - Create `BalanceHistory` entity: id, bankAccountId, balance, changeAmount, changeDate, comment, source (MANUAL/AUTOMATIC), budgetId (nullable)

2. **Repository Implementation**

   - Create `BankAccountRepository` interface extending `JpaRepository`
   - Add custom query: `existsByName(String name)`
   - Create `BalanceHistoryRepository` interface extending `JpaRepository`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to save bank account
     - Method to check if account name exists
     - Method to save balance history entry
   - Implement in `DataService` using repositories

4. **DTOs and Extensions**

   - Create `BankAccountDtos.java` with:
     - `CreateBankAccountRequest` record
     - `BankAccountResponse` record
   - Create `BankAccountExtensions.java` with mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to create bank account (validates uniqueness, creates account, creates initial history)
   - Implement in `DomainService`:
     - Validate name uniqueness using IDataService
     - Create bank account entity with initial balance
     - Create initial balance history entry with source=MANUAL, budgetId=null
     - Return DTO
   - Add custom exception: `DuplicateBankAccountNameException`

6. **Controller Implementation**

   - Create `BankAccountController` with `@RestController`
   - Implement POST /api/bank-accounts endpoint
   - Delegate to IDomainService
   - Add OpenAPI annotations

7. **Integration Tests**
   - Test successful creation
   - Test duplicate name validation
   - Test default initial balance (0)
   - Test negative initial balance
   - Test balance history creation

## Definition of Done

- All acceptance criteria met
- Unit tests for domain service logic
- Integration tests passing
- API documentation updated
- Decimal precision handled correctly
- Code reviewed and approved
