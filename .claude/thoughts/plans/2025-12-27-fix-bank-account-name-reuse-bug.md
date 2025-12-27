# Fix Bank Account Name Reuse After Soft Delete

## Overview

Fix a bug where creating a bank account with a name previously used by a soft-deleted account fails with a 500 error. The root cause is a mismatch between application-level and database-level uniqueness enforcement.

## Current State Analysis

**The Bug:**
1. Application checks name uniqueness via `existsByNameAndDeletedAtIsNull()` which correctly filters soft-deleted accounts
2. Database has a standard unique constraint that applies to ALL records (including soft-deleted)
3. When inserting, database constraint fails → 500 error

**Key Files:**
- `BankAccount.java:13` - `@UniqueConstraint(columnNames = "name")` applies to all rows
- `BankAccount.java:21` - `@Column(unique = true)` redundant constraint
- `BankAccountRepository.java:15-16` - Application check correctly filters soft-deleted

## Desired End State

After implementation:
- Creating a bank account with a name previously used by a soft-deleted account succeeds
- Name uniqueness is still enforced among active (non-deleted) accounts
- Database constraint matches the business rule: unique names only among `deleted_at IS NULL` records

**Verification:**
1. Create account "Test Account"
2. Delete "Test Account" (soft delete)
3. Create new account "Test Account" → should succeed (currently fails with 500)

## What We're NOT Doing

- Migration scripts (app not in production)
- Changing the soft-delete pattern
- Modifying application-level validation logic (it's already correct)

## Implementation Approach

Replace the standard JPA unique constraint with a PostgreSQL partial unique index that only enforces uniqueness for non-deleted records.

## Phase 1: Remove JPA Unique Constraints

### Overview
Remove the JPA annotations that create the standard unique constraint.

### Changes Required:

#### 1. BankAccount Entity
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java`

**Change 1**: Remove `uniqueConstraints` from `@Table` annotation (line 13)

Before:
```java
@Table(name = "bank_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
```

After:
```java
@Table(name = "bank_accounts")
```

**Change 2**: Remove `unique = true` from `@Column` on name field (line 21)

Before:
```java
@Column(nullable = false, unique = true)
private String name;
```

After:
```java
@Column(nullable = false)
private String name;
```

---

## Phase 2: Add Partial Unique Index

### Overview
Create a PostgreSQL partial unique index using Hibernate's `import.sql`.

### Changes Required:

#### 1. Create import.sql
**File**: `src/main/resources/import.sql`

```sql
-- Partial unique index: enforce unique names only for non-deleted bank accounts
-- This allows reusing names after soft deletion
CREATE UNIQUE INDEX IF NOT EXISTS idx_bank_accounts_name_active
ON bank_accounts (name)
WHERE deleted_at IS NULL;
```

**Note**: `IF NOT EXISTS` prevents errors when running with `ddl-auto: update` in local profile.

---

## Phase 3: Add Integration Test

### Overview
Add a test that verifies name reuse after soft deletion works correctly.

### Changes Required:

#### 1. BankAccountIntegrationTest
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`

Add new test method:

```java
@Test
void shouldAllowReusingNameAfterSoftDelete() {
    // Given - create and delete a bank account
    String accountName = "Reusable Account";
    CreateBankAccountRequest createRequest = new CreateBankAccountRequest(
            accountName,
            "Original description",
            new BigDecimal("100.00")
    );

    ResponseEntity<BankAccountResponse> createResponse = restTemplate.postForEntity(
            BASE_URL, createRequest, BankAccountResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID originalId = createResponse.getBody().id();

    // Delete the account
    restTemplate.delete(BASE_URL + "/" + originalId);

    // When - create a new account with the same name
    CreateBankAccountRequest reuseRequest = new CreateBankAccountRequest(
            accountName,
            "New description",
            new BigDecimal("200.00")
    );

    ResponseEntity<BankAccountResponse> reuseResponse = restTemplate.postForEntity(
            BASE_URL, reuseRequest, BankAccountResponse.class);

    // Then - should succeed with a new ID
    assertThat(reuseResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(reuseResponse.getBody().id()).isNotEqualTo(originalId);
    assertThat(reuseResponse.getBody().name()).isEqualTo(accountName);
    assertThat(reuseResponse.getBody().currentBalance()).isEqualByComparingTo("200.00");
}
```

---

## Success Criteria

### Automated Verification:
- [x] All tests pass: `./mvnw test`
- [x] New test `shouldAllowReusingNameAfterSoftDelete` passes
- [x] Existing uniqueness tests still pass (duplicate name among active accounts rejected)

### Manual Verification:
- [x] Start app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- [x] Create account "Test", delete it, create "Test" again → succeeds
- [x] Create account "Active", try creating "Active" again → fails with 400 (duplicate)

---

## Testing Strategy

### Key Test Cases:
1. **Name reuse after soft delete** - New test above
2. **Duplicate active names rejected** - Existing test `shouldReturnBadRequestWhenCreatingDuplicateName`
3. **Update to existing name rejected** - Existing test for update uniqueness

---

## References

- Research doc: `.claude/thoughts/research/2025-12-26-bank-account-business-rules.md`
- Entity: `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java`
- Repository: `src/main/java/org/example/axelnyman/main/infrastructure/data/context/BankAccountRepository.java`
