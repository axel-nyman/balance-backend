---
date: 2025-12-28T10:00:00+01:00
researcher: Claude Code
git_commit: cfa9494b06aaa2d2a102e5ce6d05b5bc21b7dff5
branch: main
repository: balance-backend
topic: "Timestamp and Date Handling for Bank Accounts and Balance Updates"
tags: [research, codebase, timestamps, dates, bank-accounts, balance-history, localdatetime, jpa-auditing]
status: complete
last_updated: 2025-12-28
last_updated_by: Claude Code
---

# Research: Timestamp and Date Handling for Bank Accounts and Balance Updates

**Date**: 2025-12-28T10:00:00+01:00
**Researcher**: Claude Code
**Git Commit**: cfa9494b06aaa2d2a102e5ce6d05b5bc21b7dff5
**Branch**: main
**Repository**: balance-backend

## Research Question

Research timestamp and date handling regarding bank accounts and bank account balance updates.

## Summary

The codebase uses `LocalDateTime` exclusively for all timestamp and date operations. JPA Auditing (`@CreatedDate`, `@LastModifiedDate`) handles automatic entity timestamps. Balance history entries distinguish between **manual entries** (user-provided dates) and **automatic entries** (system-generated timestamps). No timezone information is stored; the application relies on JVM timezone configuration. Precision differences between Java (nanoseconds) and PostgreSQL (microseconds) are handled through truncation in comparisons.

## Detailed Findings

### 1. Entity Timestamp Fields

#### BankAccount Entity
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java`

| Field | Type | Annotation | Behavior |
|-------|------|------------|----------|
| `createdAt` | `LocalDateTime` | `@CreatedDate` | Auto-set on first persist, immutable |
| `updatedAt` | `LocalDateTime` | `@LastModifiedDate` | Auto-updated on every save |
| `deletedAt` | `LocalDateTime` | None | Manually set for soft delete |

JPA auditing enabled via `@EntityListeners(AuditingEntityListener.class)`.

#### BalanceHistory Entity
**File**: `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistory.java`

| Field | Type | Annotation | Behavior |
|-------|------|------------|----------|
| `changeDate` | `LocalDateTime` | None (removed `@CreatedDate`) | Explicitly set via constructor |

The `changeDate` field was previously annotated with `@CreatedDate`, which caused a bug where user-provided dates were overwritten with `LocalDateTime.now()`. This was fixed by removing the annotation and requiring explicit date passing through the constructor.

---

### 2. Two-Source Pattern for Balance History Dates

The system distinguishes between two types of balance history entries:

#### Manual Entries (User-Provided Date)
- Source: `BalanceHistorySource.MANUAL`
- Used when: User manually updates balance via API
- Date source: `request.date()` from `UpdateBalanceRequest`
- The user-provided date is stored directly in `changeDate`

```java
// DomainService.java:176-188
BalanceHistory historyEntry = new BalanceHistory(
    updatedAccount.getId(),
    request.newBalance(),
    changeAmount,
    request.comment(),
    BalanceHistorySource.MANUAL,
    null,
    request.date()  // User-provided date
);
```

#### Automatic Entries (System-Generated Date)
- Source: `BalanceHistorySource.AUTOMATIC`
- Used when: Budget lock creates balance updates, or initial account balance
- Date source: `LocalDateTime.now()`

```java
// DomainService.java:83-92 (account creation)
new BalanceHistory(
    savedAccount.getId(),
    savedAccount.getCurrentBalance(),
    savedAccount.getCurrentBalance(),
    "Initial balance",
    BalanceHistorySource.MANUAL,
    null,
    LocalDateTime.now()  // System-generated
);

// DomainService.java:839-851 (budget lock)
new BalanceHistory(
    accountId, newBalance, totalSavings,
    comment, BalanceHistorySource.AUTOMATIC,
    budgetId,
    LocalDateTime.now()  // System-generated
);
```

---

### 3. Date Validation Rules

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:144-162`

Two validation rules apply to user-provided dates in balance updates:

#### Rule 1: No Future Dates
```java
if (request.date().isAfter(LocalDateTime.now())) {
    throw new FutureDateException("Date cannot be in the future");
}
```
- HTTP Status: 403 Forbidden
- Exception: `FutureDateException`

#### Rule 2: No Dates Before Account Creation
```java
if (request.date().truncatedTo(ChronoUnit.SECONDS)
        .isBefore(account.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))) {
    throw new DateBeforeAccountCreationException("Date cannot be before the account was created");
}
```
- HTTP Status: 400 Bad Request
- Exception: `DateBeforeAccountCreationException`
- Uses `truncatedTo(ChronoUnit.SECONDS)` to handle precision differences

---

### 4. DTO Date Handling

**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/BankAccountDtos.java`

#### Request DTO
```java
public record UpdateBalanceRequest(
    @NotNull(message = "New balance is required")
    BigDecimal newBalance,

    @NotNull(message = "Date is required")
    LocalDateTime date,

    @Size(max = 500, message = "Comment must be less than 500 characters")
    String comment
) {}
```

#### Response DTOs
```java
public record BankAccountResponse(
    UUID id, String name, String description,
    BigDecimal currentBalance,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

public record BalanceHistoryResponse(
    UUID id, BigDecimal balance, BigDecimal changeAmount,
    LocalDateTime changeDate,
    String comment, BalanceHistorySource source, UUID budgetId
) {}
```

All date fields use `LocalDateTime`. Jackson automatically handles ISO-8601 serialization/deserialization.

---

### 5. JSON Serialization

- **Format**: ISO-8601 (e.g., `"2025-12-28T10:30:00"`)
- **No custom configuration**: Default Jackson behavior
- **No `@JsonFormat` annotations**: Relies on Spring Boot auto-configuration
- **Input format**: Clients send ISO-8601 strings, Jackson parses to `LocalDateTime`
- **Output format**: `LocalDateTime` serialized to ISO-8601 strings

---

### 6. Timezone Handling

The application uses `LocalDateTime` throughout, which has **no timezone awareness**. The effective timezone depends on:

1. **Docker deployment**: JVM timezone configured via `-Duser.timezone=Europe/Stockholm` in Dockerfile
2. **Local development**: JVM uses system default timezone

#### Known Issue (Documented in Plans)
**File**: `.claude/thoughts/plans/2025-12-28-timezone-fix.md`

When running in Docker with UTC default:
- Account created at 08:45 Stockholm time appears as 07:45 in API
- Balance updates with Stockholm timestamps rejected as "future dates"
- Fix: Add `-Duser.timezone=Europe/Stockholm` to Docker ENTRYPOINT

---

### 7. Precision Handling

#### Database vs Java Precision
- **Java `LocalDateTime`**: Nanosecond precision (9 decimal places)
- **PostgreSQL `timestamp`**: Microsecond precision (6 decimal places)

#### Comparison Strategy
When comparing timestamps, use truncation to avoid precision mismatches:

```java
// Business logic (DomainService.java:160)
request.date().truncatedTo(ChronoUnit.SECONDS)
    .isBefore(account.getCreatedAt().truncatedTo(ChronoUnit.SECONDS))
```

#### Test Matcher
**File**: `src/test/java/org/example/axelnyman/main/TestDateTimeMatchers.java`

Custom Hamcrest matcher for test assertions:
```java
public static Matcher<String> matchesTimestampIgnoringNanos(String expected) {
    // Allows 2 microsecond tolerance for rounding differences
    // Truncates both to microseconds before comparison
}
```

---

### 8. Date Arithmetic Operations

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:337-374`

Used for calculating recurring expense due dates:

```java
// Addition operations
lastUsedDate.plusMonths(1)   // Monthly
lastUsedDate.plusMonths(3)   // Quarterly
lastUsedDate.plusMonths(6)   // Biannually
lastUsedDate.plusYears(1)    // Yearly

// Comparison operations
nextDueDate.isBefore(LocalDateTime.now())
nextDueDate.isEqual(LocalDateTime.now())
```

---

### 9. Soft Delete Pattern

Soft deletion uses manual timestamp setting (not JPA auditing):

**File**: `src/main/java/org/example/axelnyman/main/infrastructure/data/services/DataService.java:94-99`

```java
public void deleteBankAccount(UUID accountId) {
    BankAccount account = bankAccountRepository.findById(accountId)
        .orElseThrow(() -> new RuntimeException("Account not found"));
    account.setDeletedAt(LocalDateTime.now());
    bankAccountRepository.save(account);
}
```

---

## Code References

### Entities
- `src/main/java/org/example/axelnyman/main/domain/model/BankAccount.java` - Account entity with audit timestamps
- `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistory.java` - History entity with changeDate
- `src/main/java/org/example/axelnyman/main/domain/model/BalanceHistorySource.java` - MANUAL/AUTOMATIC enum

### DTOs
- `src/main/java/org/example/axelnyman/main/domain/dtos/BankAccountDtos.java:26-50` - Request/Response records

### Service Logic
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:144-162` - Date validation
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:176-188` - Manual balance update
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:83-92` - Initial balance history
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:839-851` - Automatic balance history

### Data Layer
- `src/main/java/org/example/axelnyman/main/infrastructure/data/services/DataService.java:94-99` - Soft delete

### Test Utilities
- `src/test/java/org/example/axelnyman/main/TestDateTimeMatchers.java` - Precision-tolerant matcher

### Exceptions
- `src/main/java/org/example/axelnyman/main/shared/exceptions/FutureDateException.java`
- `src/main/java/org/example/axelnyman/main/shared/exceptions/DateBeforeAccountCreationException.java`

---

## Architecture Documentation

### Date/Time Type Hierarchy
```
All timestamps use LocalDateTime (no timezone info)
├── Entity audit fields (@CreatedDate, @LastModifiedDate)
├── Business date fields (changeDate, deletedAt)
├── DTO date fields (request.date(), response timestamps)
└── Validation comparisons (LocalDateTime.now())
```

### Timestamp Flow: Manual Balance Update
```
Client Request (ISO-8601 string)
    │
    ▼
Jackson Deserialization → LocalDateTime
    │
    ▼
DTO Validation (@NotNull)
    │
    ▼
Business Validation
    ├── isAfter(LocalDateTime.now()) → FutureDateException
    └── isBefore(account.getCreatedAt()) → DateBeforeAccountCreationException
    │
    ▼
BalanceHistory Constructor (explicit changeDate)
    │
    ▼
JPA Persist (no @CreatedDate override)
    │
    ▼
PostgreSQL (stored with microsecond precision)
```

### Timestamp Flow: Automatic Balance Update (Budget Lock)
```
Budget Lock Operation
    │
    ▼
LocalDateTime.now()
    │
    ▼
BalanceHistory Constructor (system timestamp)
    │
    ▼
JPA Persist → PostgreSQL
```

---

## Historical Context (from thoughts/)

### Related Implementation Plans
- `.claude/thoughts/plans/2025-12-28-timezone-fix.md` - Documents timezone configuration issue and JVM fix
- `.claude/thoughts/plans/2025-12-27-fix-balance-history-changedate-bug.md` - Documents the `@CreatedDate` bug fix

### Key Historical Decisions
1. **`@CreatedDate` Removal**: The `changeDate` field previously used `@CreatedDate` which overwrote user-provided dates. Fixed by removing annotation and using explicit constructor parameter.
2. **Timezone Approach**: Decision to use JVM-level timezone configuration (`-Duser.timezone=Europe/Stockholm`) rather than migrating to `ZonedDateTime` or `Instant`.
3. **Precision Handling**: Using `truncatedTo(ChronoUnit.SECONDS)` for business comparisons to avoid precision-related validation failures.

---

## Related Research

- `.claude/thoughts/research/2025-12-26-bank-account-business-rules.md` - Bank account creation/deletion rules

---

## Open Questions

1. **Timezone Migration**: Will existing UTC timestamps in database be migrated after timezone fix?
2. **DST Handling**: How are daylight saving time transitions handled for date comparisons?
3. **API Timezone Contract**: Should API explicitly document expected timezone for date inputs?
