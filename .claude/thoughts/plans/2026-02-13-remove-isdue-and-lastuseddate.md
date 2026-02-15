# Remove `isDue` and `lastUsedDate` from Recurring Expenses

## Overview

Remove two unused fields from the recurring expense API responses: the `isDue` runtime-calculated boolean (not useful without budget context) and the `lastUsedDate` timestamp (superseded by `lastUsedMonth`/`lastUsedYear`). This is a cleanup following the month-based due date migration (V3).

## Current State Analysis

- `isDue` is calculated at runtime in `DomainService.calculateIsDue()` using wall-clock time — not tied to any budget context, making it unreliable
- `lastUsedDate` is a `TIMESTAMP` column set during budget lock/unlock but no longer drives any logic — `lastUsedMonth`/`lastUsedYear` handle everything
- Both fields appear in API response DTOs and are tested in integration tests

### Key Discoveries:
- `calculateNextDueDate()` and `DueDate` record must stay — they feed `dueMonth`/`dueYear`/`dueDisplay` which ARE still used
- Only `calculateIsDue()` is deleted
- `lastUsedDate` is set in 3 places in DomainService: budget lock (line 870), unlock restore (line 1018), unlock reset (line 1023)
- Constructor explicitly sets `this.lastUsedDate = null` (line 75) — needs removal
- Comment on line 323 references not updating `lastUsedDate` — needs removal

## What We're NOT Doing

- Replacing `isDue` with budget-context-aware logic (that's a future feature if needed)
- Updating backlog story files that reference `lastUsedDate`
- Removing `lastUsedMonth`/`lastUsedYear` (those are actively used)

## Implementation Approach

Two sequential phases: first remove `isDue` (code-only, no DB change), then remove `lastUsedDate` (code + DB migration). This ordering means Phase 1 is safely deployable on its own if needed.

---

## Phase 1: Remove `isDue`

### Overview
Remove the `isDue` boolean from the list endpoint response. No database changes required.

### Changes Required:

#### 1. DTO — Remove `isDue` field
**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java`

Remove `Boolean isDue` from `RecurringExpenseListItemResponse` (line 72):

```java
// BEFORE (lines 61-74)
public record RecurringExpenseListItemResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String recurrenceInterval,
        Boolean isManual,
        BankAccountSummary bankAccount,
        LocalDateTime lastUsedDate,
        Integer dueMonth,
        Integer dueYear,
        String dueDisplay,
        Boolean isDue,
        LocalDateTime createdAt
) {}

// AFTER
public record RecurringExpenseListItemResponse(
        UUID id,
        String name,
        BigDecimal amount,
        String recurrenceInterval,
        Boolean isManual,
        BankAccountSummary bankAccount,
        LocalDateTime lastUsedDate,
        Integer dueMonth,
        Integer dueYear,
        String dueDisplay,
        LocalDateTime createdAt
) {}
```

#### 2. Extensions — Remove `isDue` parameter
**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java`

Remove `Boolean isDue` parameter from `toListItemResponse()` method signature (line 39) and from the constructor call (line 55):

```java
// BEFORE (lines 33-57)
public static RecurringExpenseListItemResponse toListItemResponse(
        RecurringExpense recurringExpense,
        BankAccount bankAccount,
        Integer dueMonth,
        Integer dueYear,
        String dueDisplay,
        Boolean isDue) {
    ...
    return new RecurringExpenseListItemResponse(
            ...
            dueDisplay,
            isDue,
            recurringExpense.getCreatedAt()
    );
}

// AFTER
public static RecurringExpenseListItemResponse toListItemResponse(
        RecurringExpense recurringExpense,
        BankAccount bankAccount,
        Integer dueMonth,
        Integer dueYear,
        String dueDisplay) {
    ...
    return new RecurringExpenseListItemResponse(
            ...
            dueDisplay,
            recurringExpense.getCreatedAt()
    );
}
```

#### 3. DomainService — Remove `calculateIsDue()` and its usage
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**3a.** In `getAllRecurringExpenses()` (lines 337-358): Remove `calculateIsDue()` call and `isDue` argument:

```java
// BEFORE (lines 342-353)
List<RecurringExpenseListItemResponse> expenseResponses = expenses.stream()
        .map(expense -> {
            DueDate dueDate = calculateNextDueDate(expense);
            Boolean isDue = calculateIsDue(expense, dueDate);
            String dueDisplay = formatDueDisplay(dueDate);
            BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());

            return RecurringExpenseExtensions.toListItemResponse(
                    expense, bankAccount,
                    dueDate != null ? dueDate.month() : null,
                    dueDate != null ? dueDate.year() : null,
                    dueDisplay, isDue);
        })

// AFTER
List<RecurringExpenseListItemResponse> expenseResponses = expenses.stream()
        .map(expense -> {
            DueDate dueDate = calculateNextDueDate(expense);
            String dueDisplay = formatDueDisplay(dueDate);
            BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());

            return RecurringExpenseExtensions.toListItemResponse(
                    expense, bankAccount,
                    dueDate != null ? dueDate.month() : null,
                    dueDate != null ? dueDate.year() : null,
                    dueDisplay);
        })
```

**3b.** Delete the entire `calculateIsDue()` method (lines 404-418).

#### 4. Tests — Remove `isDue` assertions
**File**: `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java`

Remove all `isDue` assertions from the list endpoint tests. These are `.andExpect(jsonPath("$.expenses[0].isDue", is(...)))` lines at approximately:
- Line 367: `isDue: true` (never-used expense)
- Line 392: `isDue: true` (monthly, 2 months ago)
- Line 416: `isDue: false` (quarterly, 2 months ago)
- Line 440: `isDue: true` (biannual, 7 months ago)
- Line 464: `isDue: false` (yearly, 11 months ago)
- Line 533: `isDue: true` (overdue monthly)
- Line 551: `isDue: false` (recent monthly)

### Success Criteria:

#### Automated Verification:
- [x] `./mvnw test -Dtest=RecurringExpenseIntegrationTest` — all tests pass
- [x] `./mvnw test` — full test suite passes (no compile errors from removed parameter)

---

## Phase 2: Remove `lastUsedDate`

### Overview
Remove the `lastUsedDate` field from the entity, DTOs, extensions, and service logic. Drop the database column via V4 Flyway migration.

### Changes Required:

#### 1. Flyway Migration — Drop column
**File**: `src/main/resources/db/migration/V4__drop_last_used_date.sql` (NEW)

```sql
-- Rollback: ALTER TABLE recurring_expenses ADD COLUMN last_used_date TIMESTAMP;
ALTER TABLE recurring_expenses DROP COLUMN last_used_date;
```

#### 2. Entity — Remove field, getter/setter, constructor reference
**File**: `src/main/java/org/example/axelnyman/main/domain/model/RecurringExpense.java`

**2a.** Remove the field declaration (lines 41-42):
```java
// DELETE these lines
@Column(name = "last_used_date")
private LocalDateTime lastUsedDate;
```

**2b.** Remove `this.lastUsedDate = null;` from constructor (line 75) and its comment.

**2c.** Remove getter and setter (lines 131-137):
```java
// DELETE these methods
public LocalDateTime getLastUsedDate() { return lastUsedDate; }
public void setLastUsedDate(LocalDateTime lastUsedDate) { this.lastUsedDate = lastUsedDate; }
```

#### 3. DTOs — Remove `lastUsedDate` from both response records
**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java`

**3a.** Remove `LocalDateTime lastUsedDate` from `RecurringExpenseResponse` (line 56).

**3b.** Remove `LocalDateTime lastUsedDate` from `RecurringExpenseListItemResponse` (line 68 after Phase 1 edits).

**3c.** If `LocalDateTime` is no longer used in this file after removal, remove the import (line 10).

#### 4. Extensions — Remove `lastUsedDate` from mapping calls
**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java`

**4a.** In `toResponse()` (line 27): Remove `recurringExpense.getLastUsedDate()` argument.

**4b.** In `toListItemResponse()` (line 51 before Phase 1 edits): Remove `recurringExpense.getLastUsedDate()` argument.

#### 5. DomainService — Remove all `setLastUsedDate` calls and comment
**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

**5a.** Remove comment on line 323:
```java
// DELETE this comment
// Update fields (DO NOT update lastUsedDate - that's only updated when used in a budget)
// REPLACE with:
// Update fields
```

**5b.** In budget lock flow (line 870): Remove `recurringExpense.setLastUsedDate(lockedAt);`

**5c.** In budget unlock restore (line 1018): Remove `recurringExpense.setLastUsedDate(previousBudget.getLockedAt());`

**5d.** In budget unlock reset (line 1023): Remove `recurringExpense.setLastUsedDate(null);`

#### 6. Tests — Remove `lastUsedDate` assertions and tests

**File**: `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java`

- Line 101: Remove `$.lastUsedDate` null assertion on creation
- Lines 313-330: Remove or simplify `shouldSetLastUsedDateToNullOnCreation` test (if the entire test only verifies `lastUsedDate`, delete it; if it verifies other creation behavior, keep the rest)
- Line 363: Remove `$.expenses[0].lastUsedDate` assertion in list test
- Lines 664-690: Remove or simplify `shouldNotModifyLastUsedDateDuringUpdate` test

**File**: `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java`

- Line 4790 area: Update `shouldUpdateRecurringExpenseLastUsedDateOnLock` test — remove or rename to only verify `lastUsedMonth`/`lastUsedYear`/`lastUsedBudgetId`
- Lines 4837, 4917, 4919: Remove `lastUsedDate` assertions in lock tests
- Lines 6577-6633: Remove `lastUsedDate` assertions in unlock/restore flow
- Lines 6714-6727: Remove `lastUsedDate` comparison with previous budget's `lockedAt`

### Success Criteria:

#### Automated Verification:
- [x] `./mvnw test -Dtest=RecurringExpenseIntegrationTest` — all tests pass
- [x] `./mvnw test -Dtest=BudgetIntegrationTest` — all tests pass
- [x] `./mvnw test` — full test suite passes (326 tests, 0 failures)
- [ ] Application starts cleanly with Flyway migration applied

---

## Combined Summary

| Aspect | Phase 1: `isDue` | Phase 2: `lastUsedDate` |
|--------|-------------------|--------------------------|
| Migration | None | V4 (drop column) |
| Entity | None | Remove field + getter/setter |
| DTOs | 1 field removed | 2 fields removed |
| Extensions | 1 parameter removed | 2 mapping args removed |
| Service | Delete 1 method + 1 call | Remove 3 setter calls + 1 comment |
| Tests | 7 assertions removed | 8+ locations across 2 test files |
| Risk | Low | Medium (schema change) |

## References

- Research: `.claude/thoughts/research/2026-02-13-recurring-expense-cleanup-implications.md`
- Cleanup note: `.claude/thoughts/notes/2026-02-13-recurring-expense-cleanup-candidates.md`
- V3 migration context: `.claude/thoughts/plans/2026-02-13-month-based-due-date-calculation.md`
