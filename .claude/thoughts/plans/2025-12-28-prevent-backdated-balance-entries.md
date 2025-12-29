# Prevent Backdated Balance History Entries - Implementation Plan

## Overview

Add validation to prevent creating balance history entries with dates before the most recent existing entry's date. This ensures that `changeAmount` calculations remain accurate by enforcing chronological order of entries. Same-date entries are allowed since multiple balance changes can occur on a single day.

## Current State Analysis

**Current behavior** (problematic):
- Users can add balance entries with any date (past or present)
- `changeAmount` is calculated as `newBalance - currentBalance` at API call time
- Backdated entries create historically inaccurate `changeAmount` values

**Location**: `DomainService.java:143-197` - `updateBankAccountBalance()` method

### Key Discoveries:
- Date validation exists for: future dates (`FutureDateException`) and dates before account creation (`DateBeforeAccountCreationException`)
- No validation for dates falling before existing history entries
- Repository already has ordering query: `findAllByBankAccountIdOrderByChangeDateDescCreatedAtDesc`

## Desired End State

When a user attempts to update a bank account balance:
1. If the requested date is **before** the most recent entry's `changeDate` -> **Reject** with `BackdatedBalanceUpdateException`
2. If the requested date is **equal to** the most recent entry's `changeDate` -> **Allow** (multiple entries on same date are valid)
3. If the requested date is **after** the most recent entry's `changeDate` -> **Allow**

### Verification:
- Unit tests pass for all scenarios
- Integration tests verify API returns 400 BAD_REQUEST for backdated entries
- Existing tests continue to pass (no regression)

## What We're NOT Doing

- Recalculating existing `changeAmount` values
- Modifying existing balance history entries
- Changing how `changeAmount` is calculated for valid entries
- Adding warnings for same-date entries

## Implementation Approach

Follow the 3-layer architecture pattern:
1. Add repository method to find most recent entry date
2. Expose via DataService/IDataService
3. Add validation in DomainService
4. Create custom exception with handler
5. Write integration tests (TDD)

---

## Phase 1: Create Exception and Handler

### Overview
Create the new exception class and register it with the global exception handler.

### Changes Required:

#### 1. New Exception Class
**File**: `src/main/java/org/example/axelnyman/main/shared/exceptions/BackdatedBalanceUpdateException.java`

```java
package org.example.axelnyman.main.shared.exceptions;

public class BackdatedBalanceUpdateException extends RuntimeException {
    public BackdatedBalanceUpdateException(String message) {
        super(message);
    }
}
```

#### 2. Exception Handler
**File**: `src/main/java/org/example/axelnyman/main/shared/exceptions/GlobalExceptionHandler.java`
**Changes**: Add handler method after `DateBeforeAccountCreationException` handler (around line 67)

```java
@ExceptionHandler(BackdatedBalanceUpdateException.class)
public ResponseEntity<Object> handleBackdatedBalanceUpdateException(BackdatedBalanceUpdateException ex) {
    Map<String, String> errorResponse = new HashMap<>();
    errorResponse.put("error", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`

---

## Phase 2: Add Repository and DataService Methods

### Overview
Add infrastructure layer support for querying the most recent entry date.

### Changes Required:

#### 1. Repository Method
**File**: `src/main/java/org/example/axelnyman/main/infrastructure/data/context/BalanceHistoryRepository.java`
**Changes**: Add method to find most recent changeDate for an account

```java
import java.time.LocalDate;
import java.util.Optional;

// Add this method:
Optional<LocalDate> findTopChangeDateByBankAccountIdOrderByChangeDateDesc(UUID bankAccountId);
```

Note: Spring Data JPA doesn't support this directly. We need a custom query:

```java
import org.springframework.data.jpa.repository.Query;

@Query("SELECT MAX(bh.changeDate) FROM BalanceHistory bh WHERE bh.bankAccountId = :bankAccountId")
Optional<LocalDate> findMostRecentChangeDateByBankAccountId(UUID bankAccountId);
```

#### 2. IDataService Interface
**File**: `src/main/java/org/example/axelnyman/main/domain/abstracts/IDataService.java`
**Changes**: Add method declaration after existing BalanceHistory operations (around line 59)

```java
Optional<LocalDate> getMostRecentBalanceHistoryDate(UUID bankAccountId);
```

#### 3. DataService Implementation
**File**: `src/main/java/org/example/axelnyman/main/infrastructure/data/services/DataService.java`
**Changes**: Add implementation

```java
@Override
public Optional<LocalDate> getMostRecentBalanceHistoryDate(UUID bankAccountId) {
    return balanceHistoryRepository.findMostRecentChangeDateByBankAccountId(bankAccountId);
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`

---

## Phase 3: Add Validation in DomainService

### Overview
Add the business logic validation to prevent backdated entries.

### Changes Required:

#### 1. DomainService Validation
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Add validation after the "date before account creation" check (after line 161)

```java
// Validate date is not before the most recent balance history entry
Optional<LocalDate> mostRecentDate = dataService.getMostRecentBalanceHistoryDate(id);
if (mostRecentDate.isPresent() && request.date().isBefore(mostRecentDate.get())) {
    throw new BackdatedBalanceUpdateException(
        "Date cannot be before the most recent balance history entry (" + mostRecentDate.get() + ")"
    );
}
```

Also add import at top of file:
```java
import org.example.axelnyman.main.shared.exceptions.BackdatedBalanceUpdateException;
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`

---

## Phase 4: Write Integration Tests

### Overview
Add comprehensive tests following TDD principles to verify the new validation.

### Changes Required:

#### 1. Integration Tests
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`
**Changes**: Add test methods in the balance update test section

```java
@Test
void shouldRejectBalanceUpdateWithDateBeforeMostRecentEntry() throws Exception {
    // Given - account with existing balance history
    var account = createBankAccountEntity("Test Account", "For backdating test", new BigDecimal("1000.00"));

    // Create an entry dated today
    updateBalanceViaApi(account.getId(), new BigDecimal("1500.00"), LocalDate.now(), "Recent update");

    // When - try to add entry with date before the most recent
    var backdatedRequest = new UpdateBalanceRequest(
        new BigDecimal("1200.00"),
        LocalDate.now().minusDays(1),
        "Backdated entry"
    );

    // Then - should be rejected with 400 BAD_REQUEST
    mockMvc.perform(patch("/api/bank-accounts/" + account.getId() + "/balance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(backdatedRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error", containsString("cannot be before the most recent")));
}

@Test
void shouldAllowBalanceUpdateWithSameDateAsMostRecentEntry() throws Exception {
    // Given - account with existing balance history dated today
    var account = createBankAccountEntity("Test Account", "For same-date test", new BigDecimal("1000.00"));
    updateBalanceViaApi(account.getId(), new BigDecimal("1500.00"), LocalDate.now(), "First update today");

    // When - add another entry with the same date
    var sameDateRequest = new UpdateBalanceRequest(
        new BigDecimal("1800.00"),
        LocalDate.now(),
        "Second update today"
    );

    // Then - should succeed
    mockMvc.perform(patch("/api/bank-accounts/" + account.getId() + "/balance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(sameDateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentBalance").value("1800.00"));
}

@Test
void shouldAllowBalanceUpdateWithDateAfterMostRecentEntry() throws Exception {
    // Given - account with existing balance history dated in the past
    var account = createBankAccountEntity("Test Account", "For future date test", new BigDecimal("1000.00"));

    // Create entry dated yesterday (using direct repository to bypass today's initial entry)
    createBalanceHistoryEntryWithDate(account.getId(), new BigDecimal("1500.00"), new BigDecimal("500.00"), "Yesterday's entry", LocalDate.now().minusDays(1));

    // When - add entry with today's date (after the most recent)
    var futureRequest = new UpdateBalanceRequest(
        new BigDecimal("2000.00"),
        LocalDate.now(),
        "Today's entry"
    );

    // Then - should succeed
    mockMvc.perform(patch("/api/bank-accounts/" + account.getId() + "/balance")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(futureRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentBalance").value("2000.00"));
}
```

Note: May need to add helper method `updateBalanceViaApi` if it doesn't exist, or adjust to use existing patterns.

### Success Criteria:

#### Automated Verification:
- [x] All tests pass: `./mvnw test`
- [x] New tests specifically pass: `./mvnw test -Dtest=BankAccountIntegrationTest#shouldRejectBalanceUpdateWithDateBeforeMostRecentEntry`
- [x] No regressions in existing tests

#### Manual Verification:
- [x] API returns clear error message when backdating is attempted
- [x] Same-date entries work correctly via API testing

---

## Testing Strategy

### Unit Tests:
- None needed - validation is simple conditional logic best tested via integration tests

### Integration Tests:
1. **Reject backdated entry**: Entry with date before most recent should return 400
2. **Allow same-date entry**: Entry with same date as most recent should succeed
3. **Allow future-dated entry**: Entry with date after most recent should succeed
4. **Edge case - first entry**: First entry after account creation should always succeed (no prior entries to check against)

### Manual Testing Steps:
1. Create a bank account via API
2. Update balance with today's date
3. Attempt to update balance with yesterday's date -> should fail
4. Update balance again with today's date -> should succeed

## Performance Considerations

The new query `findMostRecentChangeDateByBankAccountId` uses `MAX()` aggregate which is efficient with an index on `(bank_account_id, change_date)`. Consider adding index if performance issues arise with large history tables.

## Migration Notes

No database migration needed - this is a validation-only change. Existing backdated entries will remain in the database (historical data preserved).

## References

- Original research: `.claude/thoughts/research/2025-12-28-balance-history-change-amount.md`
- Current implementation: `DomainService.java:143-197`
- Similar validation pattern: `DateBeforeAccountCreationException` at `DomainService.java:158-161`
