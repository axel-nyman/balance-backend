# Story 3: Update Bank Account Balance

**As a** bank account owner
**I want to** manually update the account balance
**So that** I can keep it synchronized with my actual bank

## Acceptance Criteria

- User can update balance
- Must provide new balance, date and optional comment
- Creates balance history entry with MANUAL source
- Calculates and stores change amount
- Updates current balance on account
- Date cannot be in the future

## API Specification

```
POST /api/bank-accounts/{id}/balance
Request Body:
{
  "newBalance": "decimal",
  "date": "datetime",
  "comment": "string"
}

Success Response (200):
{
  "id": "uuid",
  "name": "string",
  "currentBalance": "decimal",
  "previousBalance": "decimal",
  "changeAmount": "decimal",
  "lastUpdated": "datetime"
}

Error Response (403):
{
  "error": "Date cannot be in the future"
}
```

## Technical Implementation

1. **DTOs**

   - Add to `BankAccountDtos.java`:
     - `UpdateBalanceRequest` record
     - `BalanceUpdateResponse` record

2. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get bank account by id
     - Method to save bank account
     - Method to save balance history
   - Implement in `DataService`

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update bank account balance
   - Implement in `DomainService`:
     - Validate date is not in future
     - Calculate change amount (new - current)
     - Update account currentBalance
     - Create balance history entry with source=MANUAL, budgetId=null
     - Return DTO with previous and new balance

4. **Controller Implementation**

   - Add POST /api/bank-accounts/{id}/balance endpoint

5. **Integration Tests**
   - Test successful update
   - Test negative balance
   - Test future date rejection
   - Test history creation
   - Test change calculation

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Decimal precision handled
- Code reviewed and approved
