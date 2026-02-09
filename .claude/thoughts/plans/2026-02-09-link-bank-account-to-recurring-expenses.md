# Link Bank Account to Recurring Expenses — Implementation Plan

## Overview

Add an optional `bankAccountId` foreign key to the `recurring_expenses` table and propagate this relationship through all layers. This allows recurring expenses to have a default bank account, so when they are added to budgets the frontend can pre-fill the bank account field. The link is informational only — no auto-fill behavior in budget expense creation.

## Current State Analysis

- `RecurringExpense` entity has no bank account reference
- `BudgetExpense` requires `bankAccountId` (not nullable) — every time a recurring expense is added to a budget, the user must manually select a bank account
- `BankAccountSummary(UUID id, String name, BigDecimal currentBalance)` already exists in `BudgetDtos.java:59-63` and is used by `BudgetIncomeResponse`, `BudgetExpenseResponse`, and `BudgetSavingsResponse`
- Only one Flyway migration exists (`V1__baseline_schema.sql`), so the next version is `V2`

### Key Discoveries:

- `BankAccountSummary` is defined inside `BudgetDtos.java:59-63` — we need to either import it from there or extract it to a shared location
- `RecurringExpenseExtensions.java` has three mapper methods that all need updating: `toResponse()`, `toListItemResponse()`, `toEntity()`
- The `DomainService` update method (line ~278) manually sets fields rather than using the `toEntity()` mapper — it will need a new `setBankAccountId()` call
- Integration tests exist at `src/test/java/.../integration/RecurringExpenseIntegrationTest.java` (~976 lines)

## Desired End State

After implementation:

1. Recurring expenses can optionally be linked to a bank account via `bankAccountId`
2. All recurring expense API responses include a `bankAccount` field (a `BankAccountSummary` object, or `null` if no account is linked or the linked account is soft-deleted)
3. Create and update requests accept an optional `bankAccountId` field
4. The database has a foreign key constraint from `recurring_expenses.bank_account_id` to `bank_accounts.id`
5. All existing data continues to work (the new column is nullable, so existing rows get `NULL`)
6. All existing tests pass, and new tests cover the bank account link scenarios

### Verification:

- `./mvnw test` — all tests pass
- `POST /api/recurring-expenses` with `bankAccountId` returns response with `bankAccount` summary
- `POST /api/recurring-expenses` without `bankAccountId` returns response with `bankAccount: null`
- `GET /api/recurring-expenses` list items include `bankAccount` field
- `PUT /api/recurring-expenses/{id}` can add, change, or remove the bank account link

## What We're NOT Doing

- **No auto-fill on budget expense creation** — the frontend will use the linked bank account data to pre-fill, but the backend budget expense creation logic stays unchanged
- **No cascading deletion** — if a linked bank account is soft-deleted, the `bankAccountId` stays on the recurring expense entity, but responses return `bankAccount: null`
- **No migration of existing data** — existing recurring expenses will have `bank_account_id = NULL`
- **No changes to budget expense endpoints** — they already work correctly with their own `bankAccountId`

## Implementation Approach

Follow the 3-layer architecture bottom-up: database → entity → DTOs → extensions → services → controller → tests. This is a single-phase change since all layers need to be updated together for the feature to work.

---

## Phase 1: Database Migration

### Overview

Add nullable `bank_account_id` column with foreign key constraint to the `recurring_expenses` table.

### Changes Required:

#### 1. New Flyway Migration

**File**: `src/main/resources/db/migration/V2__add_bank_account_to_recurring_expenses.sql`
**Action**: Create new file

```sql
-- Add optional bank account link to recurring expenses
ALTER TABLE recurring_expenses
    ADD COLUMN bank_account_id UUID;

ALTER TABLE recurring_expenses
    ADD CONSTRAINT fk_recurring_expenses_bank_account
    FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id);

CREATE INDEX idx_recurring_expenses_bank_account ON recurring_expenses(bank_account_id);
```

### Success Criteria:

#### Automated Verification:

- [ ] Application starts successfully with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (Flyway applies migration)
- [ ] Flyway migration applies cleanly (check application logs for `V2__add_bank_account_to_recurring_expenses`)

#### Manual Verification:

- [ ] Column exists in database via Adminer at `http://localhost:8081`
- [ ] Foreign key constraint is visible in table definition

---

## Phase 2: Entity & Repository

### Overview

Add `bankAccountId` field and lazy `@ManyToOne` relationship to the `RecurringExpense` entity.

### Changes Required:

#### 1. RecurringExpense Entity

**File**: `src/main/java/org/example/axelnyman/main/domain/model/RecurringExpense.java`
**Changes**: Add two new fields after `isManual`

Add field for the FK column:

```java
@Column(name = "bank_account_id")
private UUID bankAccountId;
```

Add lazy JPA relationship (read-only, same pattern as `BudgetExpense`):

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "bank_account_id", insertable = false, updatable = false)
private BankAccount bankAccount;
```

Update the constructor to accept `bankAccountId`:

```java
public RecurringExpense(String name, BigDecimal amount, RecurrenceInterval recurrenceInterval, Boolean isManual, UUID bankAccountId) {
    this.name = name;
    this.amount = amount;
    this.recurrenceInterval = recurrenceInterval;
    this.isManual = isManual != null ? isManual : false;
    this.bankAccountId = bankAccountId;
    this.lastUsedDate = null;
}
```

Add getter and setter for `bankAccountId`:

```java
public UUID getBankAccountId() {
    return bankAccountId;
}

public void setBankAccountId(UUID bankAccountId) {
    this.bankAccountId = bankAccountId;
}

public BankAccount getBankAccount() {
    return bankAccount;
}
```

### Success Criteria:

#### Automated Verification:

- [x] Project compiles: `./mvnw compile`

---

## Phase 3: DTOs

### Overview

Add `bankAccountId` to request DTOs and `bankAccount` (as `BankAccountSummary`) to response DTOs.

### Changes Required:

#### 1. RecurringExpenseDtos

**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java`

**CreateRecurringExpenseRequest** — add optional `bankAccountId` field (no `@NotNull`, it's optional):

```java
public record CreateRecurringExpenseRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Recurrence interval is required")
        String recurrenceInterval,

        @NotNull(message = "isManual is required")
        Boolean isManual,

        UUID bankAccountId
) {}
```

**UpdateRecurringExpenseRequest** — same addition:

```java
public record UpdateRecurringExpenseRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        BigDecimal amount,

        @NotNull(message = "Recurrence interval is required")
        String recurrenceInterval,

        @NotNull(message = "isManual is required")
        Boolean isManual,

        UUID bankAccountId
) {}
```

**RecurringExpenseResponse** — add `bankAccount` as `BankAccountSummary` (nullable):

```java
public record RecurringExpenseResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String recurrenceInterval,
        Boolean isManual,
        BudgetDtos.BankAccountSummary bankAccount,
        LocalDateTime lastUsedDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

**RecurringExpenseListItemResponse** — add `bankAccount` as `BankAccountSummary` (nullable):

```java
public record RecurringExpenseListItemResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String recurrenceInterval,
        Boolean isManual,
        BudgetDtos.BankAccountSummary bankAccount,
        LocalDateTime lastUsedDate,
        LocalDateTime nextDueDate,
        Boolean isDue,
        LocalDateTime createdAt
) {}
```

**Note**: Import `BudgetDtos` at the top of the file to reference `BudgetDtos.BankAccountSummary`. This avoids duplicating the record definition.

### Success Criteria:

#### Automated Verification:

- [x] Project compiles: `./mvnw compile`

---

## Phase 4: Extensions (Mappers)

### Overview

Update all three mapping methods to handle the bank account relationship.

### Changes Required:

#### 1. RecurringExpenseExtensions

**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java`

**`toEntity()`** — pass `bankAccountId` to constructor:

```java
public static RecurringExpense toEntity(CreateRecurringExpenseRequest request) {
    RecurrenceInterval interval = RecurrenceInterval.valueOf(request.recurrenceInterval().toUpperCase());

    return new RecurringExpense(
            request.name(),
            request.amount(),
            interval,
            request.isManual(),
            request.bankAccountId()
    );
}
```

**`toResponse()`** — accept `BankAccount` parameter (nullable), build summary:

```java
public static RecurringExpenseResponse toResponse(RecurringExpense recurringExpense, BankAccount bankAccount) {
    BudgetDtos.BankAccountSummary bankAccountSummary = bankAccount != null
            ? new BudgetDtos.BankAccountSummary(bankAccount.getId(), bankAccount.getName(), bankAccount.getCurrentBalance())
            : null;

    return new RecurringExpenseResponse(
            recurringExpense.getId(),
            recurringExpense.getName(),
            recurringExpense.getAmount(),
            recurringExpense.getRecurrenceInterval().name(),
            recurringExpense.getIsManual(),
            bankAccountSummary,
            recurringExpense.getLastUsedDate(),
            recurringExpense.getCreatedAt(),
            recurringExpense.getUpdatedAt()
    );
}
```

**`toListItemResponse()`** — accept `BankAccount` parameter (nullable), build summary:

```java
public static RecurringExpenseListItemResponse toListItemResponse(
        RecurringExpense recurringExpense,
        BankAccount bankAccount,
        LocalDateTime nextDueDate,
        Boolean isDue) {
    BudgetDtos.BankAccountSummary bankAccountSummary = bankAccount != null
            ? new BudgetDtos.BankAccountSummary(bankAccount.getId(), bankAccount.getName(), bankAccount.getCurrentBalance())
            : null;

    return new RecurringExpenseListItemResponse(
            recurringExpense.getId(),
            recurringExpense.getName(),
            recurringExpense.getAmount(),
            recurringExpense.getRecurrenceInterval().name(),
            recurringExpense.getIsManual(),
            bankAccountSummary,
            recurringExpense.getLastUsedDate(),
            nextDueDate,
            isDue,
            recurringExpense.getCreatedAt()
    );
}
```

### Success Criteria:

#### Automated Verification:

- [x] Project compiles: `./mvnw compile`

---

## Phase 5: Service Layer

### Overview

Update DomainService to validate the bank account on create/update and resolve the bank account for responses.

### Changes Required:

#### 1. DomainService — `createRecurringExpense()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java` (around line 250)

After the duplicate name check and before saving, add bank account validation:

```java
// Resolve bank account if provided
BankAccount bankAccount = null;
if (request.bankAccountId() != null) {
    bankAccount = dataService.getBankAccountById(request.bankAccountId())
            .orElseThrow(() -> new BankAccountNotFoundException(
                    "Bank account not found with id: " + request.bankAccountId()));
    if (bankAccount.getDeletedAt() != null) {
        throw new BankAccountNotFoundException(
                "Bank account not found with id: " + request.bankAccountId());
    }
}
```

Update the `toResponse()` call to pass the bank account:

```java
return RecurringExpenseExtensions.toResponse(savedExpense, bankAccount);
```

#### 2. DomainService — `getRecurringExpenseById()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java` (around line 268)

Resolve the bank account for the response:

```java
@Override
public RecurringExpenseResponse getRecurringExpenseById(UUID id) {
    RecurringExpense expense = dataService.getRecurringExpenseById(id)
            .orElseThrow(() -> new RecurringExpenseNotFoundException(
                    "Recurring expense not found with id: " + id));

    BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());
    return RecurringExpenseExtensions.toResponse(expense, bankAccount);
}
```

#### 3. DomainService — `updateRecurringExpense()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java` (around line 276)

Add bank account validation and update the field:

```java
// Validate and resolve bank account if provided
BankAccount bankAccount = null;
if (request.bankAccountId() != null) {
    bankAccount = dataService.getBankAccountById(request.bankAccountId())
            .orElseThrow(() -> new BankAccountNotFoundException(
                    "Bank account not found with id: " + request.bankAccountId()));
    if (bankAccount.getDeletedAt() != null) {
        throw new BankAccountNotFoundException(
                "Bank account not found with id: " + request.bankAccountId());
    }
}

// Update fields (add bankAccountId)
expense.setName(request.name());
expense.setAmount(request.amount());
expense.setRecurrenceInterval(interval);
expense.setIsManual(request.isManual());
expense.setBankAccountId(request.bankAccountId());  // Can be null to unlink
```

Update the `toResponse()` call:

```java
return RecurringExpenseExtensions.toResponse(updatedExpense, bankAccount);
```

#### 4. DomainService — `getAllRecurringExpenses()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java` (around line 307)

Resolve bank accounts for each expense in the list:

```java
@Override
public RecurringExpenseListResponse getAllRecurringExpenses() {
    List<RecurringExpense> expenses = dataService.getAllActiveRecurringExpenses();

    List<RecurringExpenseListItemResponse> expenseResponses = expenses.stream()
            .map(expense -> {
                LocalDateTime nextDueDate = calculateNextDueDate(expense);
                Boolean isDue = calculateIsDue(expense.getLastUsedDate(), nextDueDate);
                BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());

                return RecurringExpenseExtensions.toListItemResponse(expense, bankAccount, nextDueDate, isDue);
            })
            .sorted(Comparator.comparing(RecurringExpenseListItemResponse::name))
            .toList();

    return new RecurringExpenseListResponse(expenseResponses);
}
```

#### 5. DomainService — Add private helper method

Add a private helper to resolve bank account by ID, returning `null` for missing/deleted accounts:

```java
private BankAccount resolveBankAccount(UUID bankAccountId) {
    if (bankAccountId == null) {
        return null;
    }
    return dataService.getBankAccountById(bankAccountId)
            .filter(account -> account.getDeletedAt() == null)
            .orElse(null);
}
```

This method is used for GET operations (read paths) where a soft-deleted bank account should return `null` rather than throwing. For create/update operations, we validate and throw explicitly.

### Success Criteria:

#### Automated Verification:

- [x] Project compiles: `./mvnw compile`

---

## Phase 6: Integration Tests

### Overview

Update existing integration tests and add new test cases covering the bank account link.

### Changes Required:

#### 1. RecurringExpenseIntegrationTest

**File**: `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java`

**Update existing tests**: All existing create/update tests send requests without `bankAccountId`. Since the field is optional, these tests should still pass but need their assertions updated to verify `bankAccount` is `null` in the response.

**New test cases to add:**

1. `shouldCreateRecurringExpenseWithBankAccount()` — Create with valid `bankAccountId`, verify response includes `bankAccount` summary with correct id, name, balance
2. `shouldCreateRecurringExpenseWithoutBankAccount()` — Create without `bankAccountId`, verify `bankAccount` is `null`
3. `shouldReturnNotFoundWhenCreatingWithNonExistentBankAccount()` — Create with invalid `bankAccountId`, expect 404
4. `shouldReturnNotFoundWhenCreatingWithDeletedBankAccount()` — Create with soft-deleted `bankAccountId`, expect 404
5. `shouldUpdateRecurringExpenseWithBankAccount()` — Update to add a bank account link
6. `shouldUpdateRecurringExpenseToRemoveBankAccount()` — Update to set `bankAccountId` to null, unlinking the account
7. `shouldUpdateRecurringExpenseToChangeBankAccount()` — Change from one bank account to another
8. `shouldReturnNotFoundWhenUpdatingWithNonExistentBankAccount()` — Update with invalid `bankAccountId`, expect 404
9. `shouldGetRecurringExpenseWithBankAccount()` — Get by ID verifies bank account summary in response
10. `shouldGetAllRecurringExpensesWithBankAccounts()` — List endpoint includes bank account summaries
11. `shouldReturnNullBankAccountWhenLinkedAccountIsDeleted()` — Link a bank account, soft-delete the bank account, then GET the recurring expense — `bankAccount` should be `null`

### Success Criteria:

#### Automated Verification:

- [x] All tests pass: `./mvnw test` (330 tests, 0 failures, 0 errors)
- [x] Recurring expense tests specifically pass: `./mvnw test -Dtest=RecurringExpenseIntegrationTest` (54 tests, 0 failures)

#### Manual Verification:

- [x] Create a recurring expense with a bank account via Swagger UI — response includes bank account details
- [x] Create one without — response shows `bankAccount: null`
- [x] Update to change/remove bank account link — verify responses
- [x] List endpoint shows bank account info for linked expenses

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the manual testing was successful before proceeding.

---

## Testing Strategy

### Integration Tests (Primary):

- All CRUD operations with and without bank account links
- Validation: non-existent bank account, deleted bank account
- Edge case: bank account deleted after being linked (GET should return `bankAccount: null`)
- Edge case: update to remove bank account link (set to `null`)
- Existing tests continue to pass (backward compatibility for requests without `bankAccountId`)

### What NOT to test:

- Budget expense creation auto-fill (explicitly out of scope)
- Cascading deletion behavior (not implemented)

## Performance Considerations

- The `resolveBankAccount()` helper does an individual DB lookup per recurring expense in the list endpoint. For the current scale this is fine. If the list grows large, consider using `@EntityGraph` or batch-fetching bank accounts. This is a known N+1 pattern but acceptable at current data volumes.
- An index on `bank_account_id` is included in the migration for FK join performance.

## Migration Notes

- **Backward compatible**: The new column is nullable, so existing rows get `NULL`
- **No data migration needed**: Existing recurring expenses will have no bank account linked
- **No downtime**: `ALTER TABLE ADD COLUMN` with nullable column is non-blocking in PostgreSQL
- **Rollback**: `ALTER TABLE recurring_expenses DROP COLUMN bank_account_id;` (cascade drops FK and index)

## References

- Research document: `.claude/thoughts/research/2026-02-06-recurring-expenses-data-flow.md`
- Similar pattern: `BudgetExpense` entity's bank account relationship at `src/main/java/.../domain/model/BudgetExpense.java:28-29,56-58`
- `BankAccountSummary` DTO: `src/main/java/.../domain/dtos/BudgetDtos.java:59-63`
- Existing integration tests: `src/test/java/.../integration/RecurringExpenseIntegrationTest.java`
