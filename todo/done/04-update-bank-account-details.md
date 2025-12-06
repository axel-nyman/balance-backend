# Story 4: Update Bank Account Details

**As a** bank account owner
**I want to** update account name and description
**So that** I can keep account information current

## Acceptance Criteria

- User can update account details
- Can update name and/or description
- Name must remain unique
- Cannot update soft-deleted accounts
- Balance cannot be updated through this endpoint

## API Specification

```
PUT /api/bank-accounts/{id}
Request Body:
{
  "name": "string",
  "description": "string"
}

Success Response (200):
{
  "id": "uuid",
  "name": "string",
  "description": "string",
  "currentBalance": "decimal",
  "createdAt": "datetime",
  "updatedAt": "datetime"
}

Error Response (400):
{
  "error": "Bank account name already exists"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Add `updatedAt` field to BankAccount (with @LastModifiedDate annotation)

2. **DTOs**

   - Add to `BankAccountDtos.java`:
     - `UpdateBankAccountRequest` record

3. **Data Service Layer**

   - Add to `IDataService`:
     - Method to check name uniqueness excluding specific account id
   - Implement in `DataService`

4. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update bank account details
   - Implement in `DomainService`:
     - Validate account exists and not deleted
     - Validate name uniqueness if changed
     - Update only provided fields
     - Return updated DTO

5. **Controller Implementation**

   - Add PUT /api/bank-accounts/{id} endpoint

6. **Integration Tests**
   - Test successful update
   - Test duplicate name rejection
   - Test partial updates
   - Test deleted account update rejection

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
