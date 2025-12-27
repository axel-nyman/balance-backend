---
date: 2025-12-26T12:00:00+01:00
researcher: Claude Code
git_commit: 95bb7a903d035c1af358902770d12b27fb753787
branch: main
repository: balance-backend
topic: "Bank Account Business Rules for Creation and Deletion"
tags: [research, codebase, bank-accounts, business-rules, soft-delete, validation]
status: complete
last_updated: 2025-12-26
last_updated_by: Claude Code
---

# Research: Bank Account Business Rules for Creation and Deletion

**Date**: 2025-12-26T12:00:00+01:00
**Researcher**: Claude Code
**Git Commit**: 95bb7a903d035c1af358902770d12b27fb753787
**Branch**: main
**Repository**: balance-backend

## Research Question

Research everything regarding bank accounts, and specifically business rules regarding creation/deletion of bank accounts.

## Summary

Bank accounts are managed through a 3-layer architecture with strict separation between data access (DataService), business logic (DomainService), and API (Controller). The system implements **soft delete** for all account deletions, preserving historical data. Key business rules enforce unique account names among active accounts, prevent modification of deleted accounts, and protect accounts linked to unlocked budgets from deletion.

## Detailed Findings

### 1. Bank Account Entity Structure

**File**: `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java`

The entity defines the core structure with JPA auditing and soft delete support:

| Field | Type | Constraints | Purpose |
|-------|------|-------------|---------|
| `id` | UUID | Primary Key, Auto-generated | Unique identifier |
| `name` | String | NOT NULL, UNIQUE | Account identifier (unique among active accounts) |
| `description` | String | Max 500 chars | Optional description |
| `currentBalance` | BigDecimal | NOT NULL, precision(19,2) | Current monetary balance |
| `createdAt` | LocalDateTime | NOT NULL, immutable | JPA audit timestamp |
| `updatedAt` | LocalDateTime | NOT NULL | JPA audit timestamp |
| `deletedAt` | LocalDateTime | Nullable | Soft delete marker |

**Key Annotations**:
- `@EntityListeners(AuditingEntityListener.class)` - Automatic timestamp management
- `@Table(uniqueConstraints = @UniqueConstraint(columnNames = "name"))` - Database-level uniqueness

---

### 2. Creation Business Rules

**Implementation**: `DomainService.java:72-91`

#### Rule 1: Unique Name Validation
```
IF dataService.existsByBankAccountName(request.name()) THEN
    THROW DuplicateBankAccountNameException("Bank account name already exists")
```
- Checks only active accounts (WHERE deletedAt IS NULL)
- Enforced at both application and database level

#### Rule 2: Default Initial Balance
```
IF request.initialBalance IS NULL THEN
    initialBalance = BigDecimal.ZERO
```
- Handled in `BankAccountExtensions.toEntity()` (line 26-28)

#### Rule 3: Automatic Balance History Entry
On successful creation:
```
CREATE BalanceHistory(
    bankAccountId = savedAccount.id,
    newBalance = currentBalance,
    changeAmount = currentBalance,
    comment = "Initial balance",
    source = BalanceHistorySource.MANUAL,
    budgetId = null
)
```

#### Rule 4: Transactional Atomicity
- Method annotated `@Transactional`
- Account creation and balance history entry succeed or fail together

#### DTO Validation Rules (CreateBankAccountRequest)
| Field | Validation | Message |
|-------|-----------|---------|
| `name` | `@NotBlank` | "Name is required" |
| `description` | `@Size(max = 500)` | - |
| `initialBalance` | `@PositiveOrZero` | "Initial balance must be zero or positive" |

---

### 3. Deletion Business Rules

**Implementation**: `DomainService.java:193-212`

#### Rule 1: Account Must Exist
```
IF dataService.getBankAccountById(id) IS EMPTY THEN
    THROW BankAccountNotFoundException("Bank account not found with id: {id}")
```

#### Rule 2: Cannot Delete Already Deleted Account
```
IF account.deletedAt IS NOT NULL THEN
    THROW BankAccountNotFoundException("Bank account not found with id: {id}")
```
- Returns 404, treating soft-deleted accounts as "not found"

#### Rule 3: Budget Linkage Protection
```
IF dataService.isAccountLinkedToUnlockedBudget(id) THEN
    THROW AccountLinkedToBudgetException("Cannot delete account used in unlocked budget")
```

The linkage check queries three relationships:
- `BudgetIncome.bankAccountId` with `budget.status = UNLOCKED`
- `BudgetExpense.bankAccountId` with `budget.status = UNLOCKED`
- `BudgetSavings.bankAccountId` with `budget.status = UNLOCKED`

**Note**: Accounts linked to LOCKED budgets CAN be deleted (historical data preserved).

#### Rule 4: Soft Delete Implementation
```
account.setDeletedAt(LocalDateTime.now())
dataService.save(account)
```
- Record remains in database
- `deletedAt` timestamp marks deletion time
- Balance history entries are preserved

---

### 4. Consequences of Soft Delete

Once an account has `deletedAt != NULL`:

| Operation | Behavior | HTTP Status |
|-----------|----------|-------------|
| GET /api/bank-accounts | Excluded from list | 200 (without account) |
| GET total balance | Excluded from sum | 200 |
| PUT (update details) | Rejected | 404 |
| POST /balance | Rejected | 404 |
| DELETE (again) | Rejected | 404 |
| View balance history | Rejected | 404 |

The account name becomes available for reuse by new accounts.

---

### 5. Error Responses

| Exception | HTTP Status | Trigger |
|-----------|-------------|---------|
| `DuplicateBankAccountNameException` | 400 | Name already exists |
| `BankAccountNotFoundException` | 404 | Account not found or soft-deleted |
| `AccountLinkedToBudgetException` | 400 | Account used in unlocked budget |
| `FutureDateException` | 403 | Balance update with future date |
| `MethodArgumentNotValidException` | 400 | DTO validation failure |

All error responses follow format:
```json
{
  "error": "Error message"
}
```

---

### 6. API Endpoints

**Base Path**: `/api/bank-accounts`

| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| POST | `/` | Create account | None |
| GET | `/` | List active accounts | None |
| PUT | `/{id}` | Update name/description | None |
| POST | `/{id}/balance` | Update balance | None |
| DELETE | `/{id}` | Soft delete account | None |
| GET | `/{id}/balance-history` | Get paginated history | None |

**Note**: All endpoints are currently public (no authentication implemented).

---

### 7. Data Flow Diagrams

#### Creation Flow
```
Controller.createBankAccount(request)
    │
    ├─► @Valid validates DTO
    │
    └─► DomainService.createBankAccount(request)
            │
            ├─► Check name uniqueness (DataService.existsByBankAccountName)
            │       └─► If exists: throw DuplicateBankAccountNameException
            │
            ├─► Convert DTO to Entity (BankAccountExtensions.toEntity)
            │       └─► Default balance to ZERO if null
            │
            ├─► Save entity (DataService.saveBankAccount)
            │
            ├─► Create initial BalanceHistory (DataService.saveBalanceHistory)
            │
            └─► Return BankAccountResponse (201 Created)
```

#### Deletion Flow
```
Controller.deleteBankAccount(id)
    │
    └─► DomainService.deleteBankAccount(id)
            │
            ├─► Fetch account (DataService.getBankAccountById)
            │       └─► If not found: throw BankAccountNotFoundException
            │
            ├─► Check if already deleted (deletedAt != null)
            │       └─► If deleted: throw BankAccountNotFoundException
            │
            ├─► Check budget linkage (DataService.isAccountLinkedToUnlockedBudget)
            │       └─► If linked: throw AccountLinkedToBudgetException
            │
            ├─► Set deletedAt = now() and save
            │
            └─► Return void (204 No Content)
```

---

## Code References

### Core Files
- `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java` - Entity definition
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:72-91` - Create logic
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:193-212` - Delete logic
- `src/main/java/org/example/axelnyman/main/domain/dtos/BankAccountDtos.java` - Request/Response DTOs
- `src/main/java/org/example/axelnyman/main/domain/extensions/BankAccountExtensions.java` - Entity/DTO mapping

### Data Layer
- `src/main/java/org/example/axelnyman/main/infrastructure/data/context/BankAccountRepository.java` - JPA repository
- `src/main/java/org/example/axelnyman/main/infrastructure/data/services/DataService.java:62-100` - Data operations
- `src/main/java/org/example/axelnyman/main/domain/abstracts/IDataService.java:28-41` - Data interface

### API Layer
- `src/main/java/org/example/axelnyman/main/api/endpoints/BankAccountController.java` - REST endpoints
- `src/main/java/org/example/axelnyman/main/shared/exceptions/GlobalExceptionHandler.java` - Error handling

### Exceptions
- `src/main/java/org/example/axelnyman/main/shared/exceptions/BankAccountNotFoundException.java`
- `src/main/java/org/example/axelnyman/main/shared/exceptions/DuplicateBankAccountNameException.java`
- `src/main/java/org/example/axelnyman/main/shared/exceptions/AccountLinkedToBudgetException.java`

### Tests
- `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java` - 61 integration tests

---

## Architecture Documentation

### 3-Layer Service Pattern
1. **Controller** (`BankAccountController`) - HTTP handling, validation triggering, status codes
2. **DomainService** - Business rules, DTO transformation, exception throwing
3. **DataService** - Database access only, works with entities

### Soft Delete Pattern
- `deletedAt` field marks deletion timestamp
- All "active" queries filter `WHERE deletedAt IS NULL`
- Preserves referential integrity with historical budgets
- Enables name reuse after deletion

### Validation Layers
1. **DTO Validation** - Jakarta Bean Validation annotations, auto-triggered by `@Valid`
2. **Business Validation** - DomainService checks (uniqueness, linkage)
3. **Database Constraints** - `unique` constraint as final safety net

### Transactional Boundaries
- Create, update, and delete operations marked `@Transactional`
- Ensures atomic operations (account + balance history)
- Read operations not transactional (no need)

---

## Historical Context (from thoughts/)

No historical documentation exists in `.claude/thoughts/` directory. The directory structure has been created for future research documents.

---

## Related Research

This is the first research document in the repository.

---

## Open Questions

1. **Authentication**: All endpoints are currently public. When will JWT authentication be implemented?
2. **Hard Delete**: Is there ever a need to permanently remove bank account records (e.g., GDPR compliance)?
3. **Name Reuse**: Should deleted account names be permanently reserved, or is reuse acceptable?
4. **Cascade Behavior**: What happens to budget entries when their linked bank account is deleted?
