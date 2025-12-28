# Fix Balance History changeDate Bug Implementation Plan

## Overview

Fix a bug where manual balance history entries use the server's current timestamp for `changeDate` instead of the user-provided date. The root cause is the `@CreatedDate` JPA annotation overwriting manually set values during entity persistence.

## Current State Analysis

### The Bug

**Location**: `DomainService.java:140-191` - `updateBankAccountBalance` method

**Sequence of Events**:
1. User calls `POST /api/bank-accounts/{id}/balance` with `date: "2025-12-01T10:00:00"`
2. Service creates `BalanceHistory` entity (lines 168-175)
3. Service calls `historyEntry.setChangeDate(request.date())` (line 178)
4. Service calls `dataService.saveBalanceHistory(historyEntry)` (line 180)
5. JPA Auditing's `@CreatedDate` **overwrites** `changeDate` with `LocalDateTime.now()`
6. Balance history entry is saved with current server time, not user-provided date

**Root Cause**: `BalanceHistory.java:29-31`:
```java
@CreatedDate
@Column(nullable = false, updatable = false)
private LocalDateTime changeDate;
```

The `@CreatedDate` annotation triggers during persist operations and overwrites any manually set value.

### Key Discoveries:
- `DomainService.java:178` - Setter call is effectively ignored
- `BalanceHistory.java:29` - `@CreatedDate` causes the overwrite
- `BankAccountIntegrationTest.java` - No test verifies the stored `changeDate` matches request date
- Future date validation exists at `DomainService.java:142-144`
- No validation exists for dates before account creation

## Desired End State

After this plan is complete:
1. Balance history `changeDate` will correctly use the user-provided date
2. Dates before the bank account's creation date will be rejected with a clear error
3. Test coverage will verify the correct behavior
4. The fix will not break existing automatic balance history entries (budget lock)

### Verification:
- Unit test verifies user-provided date is stored correctly
- Unit test verifies dates before account creation are rejected
- All existing tests continue to pass
- Manual testing confirms the fix works end-to-end

## What We're NOT Doing

- Not changing the `createdAt`/`updatedAt` audit fields on `BalanceHistory` (they're for entity lifecycle, not business logic)
- Not changing how automatic balance history entries work (they should use current timestamp)
- Not adding complex date validation beyond "not future" and "not before account creation"
- Not modifying the API contract or response structure

## Implementation Approach

The fix requires:
1. Remove `@CreatedDate` from `changeDate` in `BalanceHistory` entity
2. Ensure `changeDate` is explicitly set in all code paths that create balance history
3. Add validation for dates before account creation
4. Add a new exception for this validation error
5. Update tests to verify correct behavior

## Phase 1: Fix the Entity and Add Exception

### Overview
Remove `@CreatedDate` annotation and add new exception for date validation.

### Changes Required:

#### 1. Update BalanceHistory Entity
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistory.java`
**Changes**: Remove `@CreatedDate` annotation from `changeDate` field

```java
// BEFORE (lines 29-31):
@CreatedDate
@Column(nullable = false, updatable = false)
private LocalDateTime changeDate;

// AFTER:
@Column(nullable = false, updatable = false)
private LocalDateTime changeDate;
```

**Rationale**: `changeDate` is a business date (when the balance change occurred), not an audit timestamp. Removing `@CreatedDate` allows manual control of this field.

#### 2. Add Constructor Parameter for changeDate
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistory.java`
**Changes**: Add `changeDate` parameter to constructor for explicit control

```java
// BEFORE (lines 48-56):
public BalanceHistory(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount,
                     String comment, BalanceHistorySource source, UUID budgetId) {
    this.bankAccountId = bankAccountId;
    this.balance = balance;
    this.changeAmount = changeAmount;
    this.comment = comment;
    this.source = source;
    this.budgetId = budgetId;
}

// AFTER:
public BalanceHistory(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount,
                     String comment, BalanceHistorySource source, UUID budgetId,
                     LocalDateTime changeDate) {
    this.bankAccountId = bankAccountId;
    this.balance = balance;
    this.changeAmount = changeAmount;
    this.comment = comment;
    this.source = source;
    this.budgetId = budgetId;
    this.changeDate = changeDate;
}
```

#### 3. Add DateBeforeAccountCreationException
**File**: `src/main/java/org/example/axelnyman/main/shared/exceptions/DateBeforeAccountCreationException.java` (new file)
**Changes**: Create new exception class

```java
package org.example.axelnyman.main.shared.exceptions;

public class DateBeforeAccountCreationException extends RuntimeException {
    public DateBeforeAccountCreationException(String message) {
        super(message);
    }
}
```

#### 4. Add Exception Handler
**File**: `src/main/java/org/example/axelnyman/main/shared/exceptions/GlobalExceptionHandler.java`
**Changes**: Add handler for the new exception (return 400 Bad Request)

```java
@ExceptionHandler(DateBeforeAccountCreationException.class)
public ResponseEntity<ErrorResponse> handleDateBeforeAccountCreation(DateBeforeAccountCreationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse(ex.getMessage()));
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles without errors: `./mvnw compile`
- [x] Existing tests pass: `./mvnw test`

#### Manual Verification:
- [x] N/A for this phase

**Implementation Note**: After completing this phase and all automated verification passes, proceed to Phase 2.

---

## Phase 2: Update Service Layer

### Overview
Update all code paths that create `BalanceHistory` to explicitly provide `changeDate`, and add validation for dates before account creation.

### Changes Required:

#### 1. Update Manual Balance Update
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Add date validation and use new constructor

At line ~145 (after future date check, before account fetch), add validation for date before account creation:

```java
@Override
@Transactional
public BalanceUpdateResponse updateBankAccountBalance(UUID id, UpdateBalanceRequest request) {
    // Validate date is not in the future
    if (request.date().isAfter(LocalDateTime.now())) {
        throw new FutureDateException("Date cannot be in the future");
    }

    // Get bank account by ID
    BankAccount account = dataService.getBankAccountById(id)
            .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

    // Check if account is soft-deleted
    if (account.getDeletedAt() != null) {
        throw new BankAccountNotFoundException("Cannot update balance of deleted bank account");
    }

    // NEW: Validate date is not before account creation
    if (request.date().isBefore(account.getCreatedAt())) {
        throw new DateBeforeAccountCreationException("Date cannot be before the account was created");
    }

    // ... rest of method unchanged until balance history creation

    // Create balance history entry with MANUAL source - use new constructor with explicit changeDate
    BalanceHistory historyEntry = new BalanceHistory(
            updatedAccount.getId(),
            request.newBalance(),
            changeAmount,
            request.comment(),
            BalanceHistorySource.MANUAL,
            null,  // budgetId is null for manual updates
            request.date()  // NEW: explicit changeDate
    );

    // REMOVE this line - no longer needed:
    // historyEntry.setChangeDate(request.date());

    dataService.saveBalanceHistory(historyEntry);

    // ... rest of method unchanged
}
```

#### 2. Update Bank Account Creation (Initial Balance History)
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Update `createBankAccount` method to use new constructor

At lines ~81-88:

```java
// BEFORE:
dataService.saveBalanceHistory(new BalanceHistory(
        savedAccount.getId(),
        savedAccount.getCurrentBalance(),
        savedAccount.getCurrentBalance(),
        "Initial balance",
        BalanceHistorySource.MANUAL,
        null
));

// AFTER:
dataService.saveBalanceHistory(new BalanceHistory(
        savedAccount.getId(),
        savedAccount.getCurrentBalance(),
        savedAccount.getCurrentBalance(),
        "Initial balance",
        BalanceHistorySource.MANUAL,
        null,
        LocalDateTime.now()  // Use current time for initial balance
));
```

#### 3. Update Budget Lock Balance History
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Update `updateBalancesForBudget` method to use new constructor

At lines ~834-842:

```java
// BEFORE:
BalanceHistory history = new BalanceHistory(
        accountId,
        newBalance,
        totalSavings,
        comment,
        BalanceHistorySource.AUTOMATIC,
        budgetId
);

// AFTER:
BalanceHistory history = new BalanceHistory(
        accountId,
        newBalance,
        totalSavings,
        comment,
        BalanceHistorySource.AUTOMATIC,
        budgetId,
        LocalDateTime.now()  // Automatic entries use current timestamp
);
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles without errors: `./mvnw compile`
- [x] Existing tests pass: `./mvnw test`

#### Manual Verification:
- [x] N/A for this phase

**Implementation Note**: After completing this phase and all automated verification passes, proceed to Phase 3.

---

## Phase 3: Update Tests

### Overview
Add tests that verify the fix works correctly and update test helper methods.

### Changes Required:

#### 1. Update Test Helper Method
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`
**Changes**: Update `createBalanceHistoryEntry` helper to use new constructor

```java
private void createBalanceHistoryEntry(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount, String comment) {
    org.example.axelnyman.main.domain.model.BalanceHistory entry = new org.example.axelnyman.main.domain.model.BalanceHistory(
        bankAccountId, balance, changeAmount, comment,
        org.example.axelnyman.main.domain.model.BalanceHistorySource.MANUAL, null,
        java.time.LocalDateTime.now());  // Add changeDate
    balanceHistoryRepository.save(entry);
}
```

#### 2. Add Test: Verify changeDate Matches Request Date
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`
**Changes**: Add new test

```java
@Test
void shouldStoreUserProvidedDateAsChangeDate() throws Exception {
    // Given - create account and prepare update request with specific past date
    var account = createBankAccountEntity("Test Account", "For date test", new BigDecimal("1000.00"));
    java.time.LocalDateTime requestDate = java.time.LocalDateTime.now().minusDays(5);

    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", requestDate.toString());
    updateRequest.put("comment", "Test date storage");

    // When - update balance
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk());

    // Then - verify the balance history entry has the user-provided date
    var historyEntries = balanceHistoryRepository.findAll();
    var latestEntry = historyEntries.stream()
            .filter(h -> h.getBankAccountId().equals(account.getId()))
            .filter(h -> h.getComment() != null && h.getComment().equals("Test date storage"))
            .findFirst()
            .orElseThrow();

    // Compare dates ignoring nanoseconds (DB precision)
    assertThat(latestEntry.getChangeDate().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
            .isEqualTo(requestDate.truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
}
```

#### 3. Add Test: Reject Date Before Account Creation
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`
**Changes**: Add new test

```java
@Test
void shouldRejectDateBeforeAccountCreation() throws Exception {
    // Given - create account
    var account = createBankAccountEntity("Test Account", "For date validation", new BigDecimal("1000.00"));

    // Wait a moment to ensure account has a createdAt timestamp
    Thread.sleep(100);

    // Use a date well before account creation (guaranteed to be before)
    java.time.LocalDateTime dateBeforeCreation = java.time.LocalDateTime.of(2020, 1, 1, 0, 0);

    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", dateBeforeCreation.toString());
    updateRequest.put("comment", "Should fail");

    // When & Then - should reject with 400
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", is("Date cannot be before the account was created")));
}
```

#### 4. Update Budget Integration Test Helper (if needed)
**File**: `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java`
**Changes**: Check if there are any direct `BalanceHistory` instantiations and update them

Search for `new BalanceHistory(` in `BudgetIntegrationTest.java` and update any occurrences to use the new constructor with `LocalDateTime.now()` as the `changeDate` parameter.

### Success Criteria:

#### Automated Verification:
- [x] All tests pass: `./mvnw test`
- [x] New test `shouldStoreUserProvidedDateAsChangeDate` passes
- [x] New test `shouldRejectDateBeforeAccountCreation` passes
- [ ] Coverage maintained at 80%+: `./mvnw clean test jacoco:report`

#### Manual Verification:
- [ ] Manually test balance update via API/UI with a past date and verify it's stored correctly in the database

**Implementation Note**: After completing this phase and all automated verification passes, the bug fix is complete.

---

## Testing Strategy

### Unit Tests:
- N/A - this is primarily a persistence layer fix

### Integration Tests:
1. **Verify user-provided date is stored**: Send balance update with past date, verify DB stores that date
2. **Verify date before account creation is rejected**: Send balance update with date before account creation, expect 400
3. **Verify future date is still rejected**: Existing test covers this
4. **Verify automatic balance history still works**: Existing budget lock tests cover this

### Manual Testing Steps:
1. Start the application with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
2. Create a bank account via API
3. Update balance with a date from 5 days ago
4. Query balance history and verify `changeDate` matches the provided date
5. Try to update balance with a date from 2020 (before account existed)
6. Verify 400 error with appropriate message

## Performance Considerations

No performance impact - this is a simple fix to how dates are handled during persistence.

## Migration Notes

No data migration needed. Existing balance history entries will retain their current `changeDate` values (even if they were incorrectly set to server timestamp instead of intended date). Only new entries will be affected.

## References

- Original ticket: User-reported bug about balance history dates
- `BalanceHistory.java:29-31` - Root cause location
- `DomainService.java:140-191` - Manual balance update method
- `DomainService.java:799-844` - Automatic balance history (budget lock)
- Spring Data JPA Auditing documentation: `@CreatedDate` behavior
