# LocalDate Migration for Balance Updates Implementation Plan

## Overview

Migrate balance update API and storage from `LocalDateTime` to `LocalDate`. This simplifies the API for frontend date pickers and removes unnecessary time precision complexity. All balance history entries (both MANUAL and AUTOMATIC) will use `LocalDate`.

## Current State Analysis

**Current implementation:**
- `UpdateBalanceRequest.date` - `LocalDateTime` (user-provided)
- `BalanceHistory.changeDate` - `LocalDateTime` entity field
- `BalanceHistoryResponse.changeDate` - `LocalDateTime` in response
- `BalanceUpdateResponse.lastUpdated` - `LocalDateTime` in response
- Validation uses `truncatedTo(ChronoUnit.SECONDS)` to handle precision mismatches

**Pain points:**
- Frontend needs datetime picker when simple date picker suffices
- Validation logic uses truncation workarounds for precision handling
- Time component has no business value for balance changes

### Key Discoveries:
- `DomainService.java:143-198` - Contains date validation and history creation
- `DomainService.java:83-91` - Initial balance uses `LocalDateTime.now()`
- `DomainService.java:839-850` - Budget lock uses `LocalDateTime.now()`
- `BalanceHistory.java:28-29` - Entity field is `LocalDateTime`
- Tests use `LocalDateTime.now().toString()` in request bodies

## Desired End State

**After implementation:**
- API accepts `LocalDate` for balance updates (format: `"2025-12-28"`)
- Entity stores `LocalDate` in `changeDate` field
- Responses return `LocalDate` for all date fields
- Validation logic simplified (no truncation needed)
- All 30+ tests updated and passing

**Verification:**
- All tests pass: `./mvnw test`
- API accepts date format `"2025-12-28"` (not datetime)
- Balance history shows dates without time component
- Validation rejects future dates and dates before account creation

## What We're NOT Doing

- NOT changing `BankAccount.createdAt`/`updatedAt` - these remain `LocalDateTime` (JPA audit fields)
- NOT changing `BankAccount.deletedAt` - remains `LocalDateTime`
- NOT migrating existing database data (will be handled by Hibernate `ddl-auto`)
- NOT changing timezone configuration

## Implementation Approach

The migration follows a bottom-up approach: Entity → DTOs → Service → Tests. Each phase is independently testable.

---

## Phase 1: Entity Layer

### Overview
Change `BalanceHistory.changeDate` from `LocalDateTime` to `LocalDate`.

### Changes Required:

#### 1. BalanceHistory Entity
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistory.java`

**Change import:**
```java
// Remove
import java.time.LocalDateTime;

// Add
import java.time.LocalDate;
```

**Change field (line 28-29):**
```java
// Before
@Column(nullable = false, updatable = false)
private LocalDateTime changeDate;

// After
@Column(nullable = false, updatable = false)
private LocalDate changeDate;
```

**Change constructor (line 46-48):**
```java
// Before
public BalanceHistory(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount,
                     String comment, BalanceHistorySource source, UUID budgetId,
                     LocalDateTime changeDate) {

// After
public BalanceHistory(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount,
                     String comment, BalanceHistorySource source, UUID budgetId,
                     LocalDate changeDate) {
```

**Change getter/setter (lines 91-97):**
```java
// Before
public LocalDateTime getChangeDate() {
    return changeDate;
}

public void setChangeDate(LocalDateTime changeDate) {
    this.changeDate = changeDate;
}

// After
public LocalDate getChangeDate() {
    return changeDate;
}

public void setChangeDate(LocalDate changeDate) {
    this.changeDate = changeDate;
}
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`

#### Manual Verification:
- [x] Confirm entity field type changed in IDE

**Implementation Note**: The project won't compile after this phase until Phase 2-3 are complete due to type mismatches.

---

## Phase 2: DTO Layer

### Overview
Update all DTOs to use `LocalDate` instead of `LocalDateTime`.

### Changes Required:

#### 1. BankAccountDtos - UpdateBalanceRequest
**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/BankAccountDtos.java`

**Add import:**
```java
import java.time.LocalDate;
```

**Change field (line 45-46):**
```java
// Before
@NotNull(message = "Date is required")
LocalDateTime date,

// After
@NotNull(message = "Date is required")
LocalDate date,
```

#### 2. BankAccountDtos - BalanceUpdateResponse
**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/BankAccountDtos.java`

**Change field (line 58):**
```java
// Before
LocalDateTime lastUpdated

// After
LocalDate lastUpdated
```

#### 3. BalanceHistoryDtos - BalanceHistoryResponse
**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/BalanceHistoryDtos.java`

**Change import:**
```java
// Remove
import java.time.LocalDateTime;

// Add
import java.time.LocalDate;
```

**Change field (line 16):**
```java
// Before
LocalDateTime changeDate,

// After
LocalDate changeDate,
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`

#### Manual Verification:
- [x] Verify DTO changes in IDE

**Implementation Note**: Compilation may still fail until Phase 3 service layer changes are complete.

---

## Phase 3: Service Layer

### Overview
Update DomainService to use `LocalDate` for validation and history creation.

### Changes Required:

#### 1. DomainService - Imports
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**Add import:**
```java
import java.time.LocalDate;
```

**Remove unused import (if not used elsewhere):**
```java
import java.time.temporal.ChronoUnit;
```

#### 2. DomainService - Future Date Validation
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**Change validation (lines 144-147):**
```java
// Before
if (request.date().isAfter(LocalDateTime.now())) {
    throw new FutureDateException("Date cannot be in the future");
}

// After
if (request.date().isAfter(LocalDate.now())) {
    throw new FutureDateException("Date cannot be in the future");
}
```

#### 3. DomainService - Date Before Creation Validation
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**Change validation (lines 158-162):**
```java
// Before
if (request.date().truncatedTo(ChronoUnit.SECONDS)
        .isBefore(account.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))) {
    throw new DateBeforeAccountCreationException("Date cannot be before the account was created");
}

// After
if (request.date().isBefore(account.getCreatedAt().toLocalDate())) {
    throw new DateBeforeAccountCreationException("Date cannot be before the account was created");
}
```

**Key improvement:** No more truncation needed - `LocalDate` comparison is inherently day-level.

#### 4. DomainService - Initial Balance History
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**Change history creation (lines 83-91):**
```java
// Before
dataService.saveBalanceHistory(new BalanceHistory(
        savedAccount.getId(),
        savedAccount.getCurrentBalance(),
        savedAccount.getCurrentBalance(),
        "Initial balance",
        BalanceHistorySource.MANUAL,
        null,
        LocalDateTime.now()
));

// After
dataService.saveBalanceHistory(new BalanceHistory(
        savedAccount.getId(),
        savedAccount.getCurrentBalance(),
        savedAccount.getCurrentBalance(),
        "Initial balance",
        BalanceHistorySource.MANUAL,
        null,
        LocalDate.now()
));
```

#### 5. DomainService - Budget Lock Balance History
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**Change history creation (lines 841-850):**
```java
// Before
BalanceHistory history = new BalanceHistory(
        accountId,
        newBalance,
        totalSavings,
        comment,
        BalanceHistorySource.AUTOMATIC,
        budgetId,
        LocalDateTime.now()
);

// After
BalanceHistory history = new BalanceHistory(
        accountId,
        newBalance,
        totalSavings,
        comment,
        BalanceHistorySource.AUTOMATIC,
        budgetId,
        LocalDate.now()
);
```

### Success Criteria:

#### Automated Verification:
- [x] Project compiles: `./mvnw compile`
- [x] Existing tests fail as expected (due to test data using datetime format)

#### Manual Verification:
- [x] Review all service changes in IDE

**Implementation Note**: After this phase, the application code is complete. Tests will fail until Phase 4.

---

## Phase 4: Test Updates

### Overview
Update all integration tests to use `LocalDate` format in request bodies and assertions.

### Changes Required:

#### 1. BankAccountIntegrationTest - Update Request Format
**File**: `src/test/java/org/example/axelnyman/main/integration/BankAccountIntegrationTest.java`

**Pattern to find and replace in all balance update tests:**

```java
// Before (multiple occurrences)
updateRequest.put("date", java.time.LocalDateTime.now().toString());
// or
updateRequest.put("date", requestDate.toString()); // where requestDate is LocalDateTime

// After
updateRequest.put("date", java.time.LocalDate.now().toString());
// or
updateRequest.put("date", java.time.LocalDate.now().toString());
```

**Test methods to update:**
- `shouldUpdateBalanceSuccessfullyWithPositiveChange()` (line 314)
- `shouldUpdateBalanceSuccessfullyWithNegativeChange()` (line 335)
- `shouldUpdateBalanceToZero()` (line 354)
- `shouldUpdateBalanceFromZero()` (line 372)
- `shouldAllowNegativeBalance()` (line 391)
- `shouldCreateBalanceHistoryEntryWithManualSource()` (line 409)
- `shouldCalculateChangeAmountCorrectly()` (line 437)
- `shouldStoreCommentWhenProvided()` (line 454)
- `shouldHandleNullComment()` (line 475)
- `shouldRejectFutureDate()` (line 491)
- `shouldAcceptCurrentDate()` (line 508)
- `shouldAcceptPastDateAfterAccountCreation()` (line 524)
- `shouldStoreUserProvidedDateAsChangeDate()` (line 545)
- `shouldRejectDateBeforeAccountCreation()` (line 581)
- `shouldAcceptUpdateWithExactSameTimestampAsCreation()` (line 602) - **needs logic change**
- `shouldAcceptUpdateWithNanosecondAfterCreation()` (line 620) - **needs logic change**
- `shouldAcceptUpdateWithTruncatedCreationTime()` (line 638) - **may be removable**
- `shouldRejectDateOneSecondBeforeAccountCreation()` (line 657) - **needs logic change**
- `shouldHandleDecimalPrecision()` (line 692)

#### 2. Precision Tests - Special Handling

Several tests specifically test timestamp precision edge cases. These need to be rethought for `LocalDate`:

**Tests to simplify/remove:**
- `shouldAcceptUpdateWithExactSameTimestampAsCreation` → Rename to `shouldAcceptUpdateWithSameDateAsCreation`
- `shouldAcceptUpdateWithNanosecondAfterCreation` → Remove (no longer applicable)
- `shouldAcceptUpdateWithTruncatedCreationTime` → Remove (no longer applicable)
- `shouldRejectDateOneSecondBeforeAccountCreation` → Rename to `shouldRejectDateOneDayBeforeAccountCreation`

**Example updated test:**
```java
@Test
void shouldAcceptUpdateWithSameDateAsCreation() throws Exception {
    // Given - account created today
    var account = createBankAccountEntity("Test Account", "For edge case", new BigDecimal("1000.00"));

    // Update with today's date (same as creation date)
    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", java.time.LocalDate.now().toString());
    updateRequest.put("comment", "Same day update");

    // When & Then - should succeed (same day is allowed)
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk());
}

@Test
void shouldRejectDateOneDayBeforeAccountCreation() throws Exception {
    // Given - account created today
    var account = createBankAccountEntity("Test Account", "For edge case", new BigDecimal("1000.00"));

    // Try to update with yesterday's date
    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", java.time.LocalDate.now().minusDays(1).toString());
    updateRequest.put("comment", "Yesterday's update");

    // When & Then - should fail (date before creation)
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isBadRequest());
}
```

#### 3. Future Date Test Update

```java
@Test
void shouldRejectFutureDate() throws Exception {
    // Given - existing account
    var account = createBankAccountEntity("Test Account", "For future date test", new BigDecimal("1000.00"));

    // Tomorrow's date
    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", java.time.LocalDate.now().plusDays(1).toString());
    updateRequest.put("comment", "Future date");

    // When & Then - should fail
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isForbidden());
}
```

#### 4. Date Storage Verification Test

```java
@Test
void shouldStoreUserProvidedDateAsChangeDate() throws Exception {
    // Given
    var account = createBankAccountEntity("Test Account", "For date test", new BigDecimal("1000.00"));
    java.time.LocalDate requestDate = java.time.LocalDate.now();

    var updateRequest = new java.util.HashMap<String, Object>();
    updateRequest.put("newBalance", new BigDecimal("1500.00"));
    updateRequest.put("date", requestDate.toString());
    updateRequest.put("comment", "Test date storage");

    // When
    mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk());

    // Then - verify stored date matches
    var historyEntries = balanceHistoryRepository.findAll();
    var latestEntry = historyEntries.stream()
            .filter(h -> h.getBankAccountId().equals(account.getId()))
            .filter(h -> h.getComment() != null && h.getComment().equals("Test date storage"))
            .findFirst()
            .orElseThrow();

    assertThat(latestEntry.getChangeDate()).isEqualTo(requestDate);
}
```

#### 5. BudgetIntegrationTest Updates
**File**: `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java`

Tests that create `BalanceHistory` directly need to use `LocalDate`:

```java
// Before
var entry = new BalanceHistory(
        account.getId(), balance, changeAmount, comment,
        BalanceHistorySource.MANUAL, null,
        java.time.LocalDateTime.now());

// After
var entry = new BalanceHistory(
        account.getId(), balance, changeAmount, comment,
        BalanceHistorySource.MANUAL, null,
        java.time.LocalDate.now());
```

#### 6. TestDateTimeMatchers - May Need Update
**File**: `src/test/java/org/example/axelnyman/main/TestDateTimeMatchers.java`

If this matcher is only used for `LocalDateTime` comparisons in balance tests, it may no longer be needed. Check usages and update or remove as appropriate.

### Success Criteria:

#### Automated Verification:
- [x] All tests pass: `./mvnw test`
- [x] Test count remains the same (minus any removed precision tests)

#### Manual Verification:
- [ ] Review test changes for correctness
- [ ] Confirm date format in API requests is `"2025-12-28"` not `"2025-12-28T10:30:00"`

**Implementation Note**: Run tests incrementally to catch issues early. Fix one test category at a time.

---

## Phase 5: Final Verification

### Overview
End-to-end verification that the migration is complete and working.

### Success Criteria:

#### Automated Verification:
- [x] Full build passes: `./mvnw clean verify`
- [x] All tests green: `./mvnw test`
- [x] No compiler warnings related to dates

#### Manual Verification:
- [ ] Start application: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- [ ] Create a bank account via API
- [ ] Update balance with date `"2025-12-28"` (not datetime)
- [ ] Verify response contains date format (not datetime)
- [ ] Get balance history and verify date format
- [ ] Test future date rejection
- [ ] Test date-before-creation rejection
- [ ] Test budget lock creates history with current date

**Implementation Note**: Complete all manual verification before considering the migration done.

---

## Testing Strategy

### Unit Tests
Not applicable - all date logic is in DomainService which is tested via integration tests.

### Integration Tests
All existing tests updated to use `LocalDate` format:
- Balance update operations: 6 tests
- Balance history creation: 4 tests
- Date validation: 9 tests (some simplified/removed)
- Error handling: 2 tests
- Budget balance updates: 6 tests

### Manual Testing Steps
1. Start application with local profile
2. Create bank account via Swagger UI
3. Update balance using date picker format (`"2025-12-28"`)
4. Verify response shows date only
5. Try future date - expect 403
6. Try yesterday (if account created today) - expect 400
7. Check balance history response format

---

## Performance Considerations

None - `LocalDate` operations are simpler than `LocalDateTime` with truncation. May see minor performance improvement due to:
- Simpler date comparisons (no truncation calls)
- Smaller storage footprint (date vs timestamp in PostgreSQL)

---

## Migration Notes

### Database Schema
Hibernate `ddl-auto` handles schema changes in development. For production:
- PostgreSQL `timestamp` column becomes `date`
- Existing data will have time component stripped automatically

### API Contract Change
This is a **breaking change** to the API contract:
- Request format changes from `"2025-12-28T10:30:00"` to `"2025-12-28"`
- Response format changes similarly
- Frontend must be updated to use date picker instead of datetime picker

---

## References

- Original research: `.claude/thoughts/research/2025-12-28-timestamp-date-handling.md`
- Related plan (timezone fix): `.claude/thoughts/plans/2025-12-28-timezone-fix.md`
- Validation implementation: `DomainService.java:143-162`
- Entity definition: `BalanceHistory.java:28-29`
