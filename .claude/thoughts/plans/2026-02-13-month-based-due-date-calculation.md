# Month-Based Due Date Calculation - Implementation Plan

## Overview

Replace the timestamp-based recurring expense due date system with a calendar month-based model. Instead of calculating due dates from lock timestamps (`lastUsedDate + interval`), due dates will be derived from the budget's month/year (`lastUsedMonth + interval`). The API will return human-readable due month strings like "Due February" or "Due March 2027".

## Current State Analysis

- `lastUsedDate` (LocalDateTime) is set to `LocalDateTime.now()` when a budget is locked
- `calculateNextDueDate()` adds interval to that timestamp → produces a `LocalDateTime`
- `calculateIsDue()` compares `nextDueDate <= LocalDateTime.now()` → time-sensitive
- Budget entity has `month`/`year` fields but they're unused in due date logic
- Production DB has 20+ recurring expenses and several budgets with existing data

### Key Discoveries:

- `updateRecurringExpensesForBudget()` at `DomainService.java:843-864` — sets `lastUsedDate = lockedAt`
- `restoreRecurringExpenses()` at `DomainService.java:976-1018` — restores from previous budget's `lockedAt`
- `calculateNextDueDate()` at `DomainService.java:383-398` — timestamp arithmetic
- `calculateIsDue()` at `DomainService.java:407-420` — point-in-time comparison
- `RecurringExpenseListItemResponse` at `RecurringExpenseDtos.java:61-72` — has `nextDueDate` (LocalDateTime) and `isDue` (Boolean)
- `RecurringExpenseExtensions.toListItemResponse()` at `RecurringExpenseExtensions.java:35-56` — maps calculated values

## Desired End State

- `RecurringExpense` entity has `lastUsedMonth` (Integer) and `lastUsedYear` (Integer) fields
- Due dates calculated as month/year pairs: lastUsedMonth + interval → dueMonth/dueYear
- `isDue` is month-based: compares due month/year against current month/year
- API returns `dueMonth` (Integer), `dueYear` (Integer), `dueDisplay` (String like "Due February" or "Due March 2027")
- `nextDueDate` (LocalDateTime) removed from API response
- Existing production data migrated correctly via Flyway

## What We're NOT Doing

- Not adding manual "last used" setting on recurring expenses (future feature)
- Not changing how recurring expenses are added to budgets (still manual)
- Not changing the lock/unlock flow beyond updating which fields get set
- Not removing `lastUsedDate` from the entity (keep for now, just stop using it for calculations)

## Implementation Approach

Work in 3 phases: database migration with backfill → entity and domain logic → DTO and API response.

---

## Phase 1: Database Migration & Entity Changes

### Overview

Add `last_used_month` and `last_used_year` columns, backfill from existing budget data, and update the entity.

### Changes Required:

#### 1. Flyway Migration

**File**: `src/main/resources/db/migration/V3__add_month_year_to_recurring_expenses.sql`
**Action**: Create new file

```sql
-- Add month/year tracking columns to recurring_expenses
ALTER TABLE recurring_expenses ADD COLUMN last_used_month INTEGER;
ALTER TABLE recurring_expenses ADD COLUMN last_used_year INTEGER;

-- Backfill from linked budgets: where last_used_budget_id is set,
-- copy month/year from that budget
UPDATE recurring_expenses re
SET last_used_month = b.month,
    last_used_year = b.year
FROM budgets b
WHERE re.last_used_budget_id = b.id
  AND re.last_used_budget_id IS NOT NULL;
```

This handles all cases:

- Expenses with `last_used_budget_id` set → gets month/year from that budget
- Expenses never used (`last_used_budget_id = NULL`) → columns stay NULL (correct)

#### 2. RecurringExpense Entity

**File**: `src/main/java/org/example/axelnyman/main/domain/model/RecurringExpense.java`
**Changes**: Add `lastUsedMonth` and `lastUsedYear` fields with getters/setters

Add after `lastUsedDate` field (line 42):

```java
@Column(name = "last_used_month")
private Integer lastUsedMonth;

@Column(name = "last_used_year")
private Integer lastUsedYear;
```

Add getters and setters for both fields.

### Success Criteria:

#### Automated Verification:

- [ ] Migration applies cleanly: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` starts without errors
- [ ] All existing tests pass: `./mvnw test`

#### Manual Verification:

- [ ] Check database: existing recurring expenses with `last_used_budget_id` have correct `last_used_month`/`last_used_year` values matching their budget

---

## Phase 2: Domain Logic Changes

### Overview

Rewrite the due date calculation to use month/year arithmetic instead of timestamp arithmetic. Update lock/unlock flows to set the new fields.

### Changes Required:

#### 1. New Helper Record for Due Date

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Add a private record and rewrite calculation methods

Add a private record at the top of the class (inside DomainService):

```java
private record DueDate(int month, int year) {}
```

#### 2. Rewrite `calculateNextDueDate()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Replace the method (lines 383-398)

Replace with:

```java
private DueDate calculateNextDueDate(RecurringExpense expense) {
    Integer lastMonth = expense.getLastUsedMonth();
    Integer lastYear = expense.getLastUsedYear();

    if (lastMonth == null || lastYear == null) {
        return null;
    }

    int monthsToAdd = switch (expense.getRecurrenceInterval()) {
        case MONTHLY -> 1;
        case QUARTERLY -> 3;
        case BIANNUALLY -> 6;
        case YEARLY -> 12;
    };

    int totalMonths = (lastYear * 12 + lastMonth - 1) + monthsToAdd;
    int dueMonth = (totalMonths % 12) + 1;
    int dueYear = totalMonths / 12;

    return new DueDate(dueMonth, dueYear);
}
```

#### 3. Rewrite `calculateIsDue()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Replace the method (lines 407-420)

Replace with:

```java
private Boolean calculateIsDue(RecurringExpense expense, DueDate dueDate) {
    if (expense.getLastUsedMonth() == null) {
        return true; // Never used = always due
    }
    if (dueDate == null) {
        return false;
    }

    LocalDate now = LocalDate.now();
    int currentMonth = now.getMonthValue();
    int currentYear = now.getYear();

    // Due if the due month/year is the current month or earlier
    return (dueDate.year() < currentYear) ||
           (dueDate.year() == currentYear && dueDate.month() <= currentMonth);
}
```

#### 4. Add `formatDueDisplay()` helper

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`

```java
private String formatDueDisplay(DueDate dueDate) {
    if (dueDate == null) {
        return null;
    }

    String monthName = java.time.Month.of(dueDate.month())
            .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

    int currentYear = LocalDate.now().getYear();
    if (dueDate.year() == currentYear) {
        return monthName;
    }
    return monthName + " " + dueDate.year();
}
```

#### 5. Update `getAllRecurringExpenses()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Update the map lambda (lines 335-353)

```java
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
```

#### 6. Update `updateRecurringExpensesForBudget()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Update to also set month/year (lines 843-864)

The method needs access to the budget's month/year. Change the signature and the caller in `lockBudget()`:

In `lockBudget()` (line 837), change:

```java
updateRecurringExpensesForBudget(budgetId, lockedAt);
```

to:

```java
updateRecurringExpensesForBudget(budgetId, lockedAt, savedBudget.getMonth(), savedBudget.getYear());
```

Update the method signature and body:

```java
private void updateRecurringExpensesForBudget(UUID budgetId, LocalDateTime lockedAt, Integer budgetMonth, Integer budgetYear) {
    List<BudgetExpense> budgetExpenses = dataService.getBudgetExpensesByBudgetId(budgetId);

    List<UUID> recurringExpenseIds = budgetExpenses.stream()
            .filter(expense -> expense.getRecurringExpenseId() != null)
            .map(BudgetExpense::getRecurringExpenseId)
            .distinct()
            .toList();

    for (UUID recurringExpenseId : recurringExpenseIds) {
        RecurringExpense recurringExpense = dataService.getRecurringExpenseById(recurringExpenseId)
                .orElseThrow(() -> new IllegalStateException(
                        "Recurring expense not found with id: " + recurringExpenseId));

        recurringExpense.setLastUsedDate(lockedAt);
        recurringExpense.setLastUsedBudgetId(budgetId);
        recurringExpense.setLastUsedMonth(budgetMonth);
        recurringExpense.setLastUsedYear(budgetYear);
        dataService.saveRecurringExpense(recurringExpense);
    }
}
```

#### 7. Update `restoreRecurringExpenses()`

**File**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java`
**Changes**: Also restore month/year (lines 976-1018)

In the `if (!previousBudgets.isEmpty())` block:

```java
Budget previousBudget = previousBudgets.get(0);
recurringExpense.setLastUsedDate(previousBudget.getLockedAt());
recurringExpense.setLastUsedBudgetId(previousBudget.getId());
recurringExpense.setLastUsedMonth(previousBudget.getMonth());
recurringExpense.setLastUsedYear(previousBudget.getYear());
```

In the `else` block:

```java
recurringExpense.setLastUsedDate(null);
recurringExpense.setLastUsedBudgetId(null);
recurringExpense.setLastUsedMonth(null);
recurringExpense.setLastUsedYear(null);
```

### Success Criteria:

#### Automated Verification:

- [ ] Application compiles: `./mvnw compile`
- [ ] All existing tests still pass: `./mvnw test` (tests will need updates first — see Phase 3)

---

## Phase 3: DTO, Extensions, and Test Updates

### Overview

Update the API response DTOs, the extensions mapping, and all affected tests.

### Changes Required:

#### 1. Update `RecurringExpenseListItemResponse`

**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java`
**Changes**: Replace `nextDueDate` (LocalDateTime) with `dueMonth`, `dueYear`, `dueDisplay`

```java
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
```

#### 2. Update `RecurringExpenseExtensions.toListItemResponse()`

**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java`
**Changes**: Update signature and mapping (lines 35-56)

```java
public static RecurringExpenseListItemResponse toListItemResponse(
        RecurringExpense recurringExpense,
        BankAccount bankAccount,
        Integer dueMonth,
        Integer dueYear,
        String dueDisplay,
        Boolean isDue) {
    BankAccountSummary bankAccountSummary = bankAccount != null
            ? new BankAccountSummary(bankAccount.getId(), bankAccount.getName(), bankAccount.getCurrentBalance())
            : null;

    return new RecurringExpenseListItemResponse(
            recurringExpense.getId(),
            recurringExpense.getName(),
            recurringExpense.getAmount(),
            recurringExpense.getRecurrenceInterval().name(),
            recurringExpense.getIsManual(),
            bankAccountSummary,
            recurringExpense.getLastUsedDate(),
            dueMonth,
            dueYear,
            dueDisplay,
            isDue,
            recurringExpense.getCreatedAt()
    );
}
```

#### 3. Update Integration Tests

**File**: `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java`
**Changes**: Update all tests that assert on `nextDueDate` and `isDue` to use the new fields

Tests to update:

- `shouldReturnNullNextDueDateWhenNeverUsed()` → assert `dueMonth`/`dueYear` are null, `isDue` is true, `dueDisplay` is null
- `shouldCalculateNextDueDateForMonthlyInterval()` → assert `dueMonth`/`dueYear` match expected month+1, check `dueDisplay`
- `shouldCalculateNextDueDateForQuarterlyInterval()` → assert `dueMonth`/`dueYear` match expected month+3
- `shouldCalculateNextDueDateForBiannuallyInterval()` → assert `dueMonth`/`dueYear` match expected month+6
- `shouldCalculateNextDueDateForYearlyInterval()` → assert `dueMonth`/`dueYear` match expected month+12
- `shouldMarkExpenseAsDueWhenLastUsedDateIsInPast()` → update to set `lastUsedMonth`/`lastUsedYear` and verify `isDue`
- `shouldMarkExpenseAsNotDueWhenNextDueDateIsFuture()` → update similarly

Key change in test setup: Tests that set `lastUsedDate` directly on the entity also need to set `lastUsedMonth`/`lastUsedYear`. The calculation now depends on month/year fields, not the timestamp.

#### 4. Update BudgetIntegrationTest helper

**File**: `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java`
**Changes**: If `createRecurringExpenseEntity()` sets `lastUsedDate`, also set `lastUsedMonth`/`lastUsedYear`

### Success Criteria:

#### Automated Verification:

- [ ] All tests pass: `./mvnw test`
- [ ] Application starts cleanly: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`

#### Manual Verification:

- [ ] `GET /api/recurring-expenses` returns `dueMonth`, `dueYear`, `dueDisplay`, `isDue` with correct values
- [ ] `dueDisplay` shows "February" (no year) for current year, "March 2027" for different year
- [ ] Locking a budget correctly updates `lastUsedMonth`/`lastUsedYear` on the recurring expenses
- [ ] Unlocking a budget correctly restores `lastUsedMonth`/`lastUsedYear`
- [ ] Never-used expenses show `isDue: true` with null due fields

---

## Testing Strategy

### Integration Tests (Update Existing):

- All 7 due-date-related tests in `RecurringExpenseIntegrationTest` need updating
- Lock/unlock tests in `BudgetIntegrationTest` that verify recurring expense state need updating to check month/year fields

### Key Test Cases:

1. Never-used expense: `isDue=true`, `dueMonth=null`, `dueYear=null`, `dueDisplay=null`
2. Monthly used in Jan 2026: `dueMonth=2`, `dueYear=2026`, `dueDisplay="February"`, `isDue` depends on current month
3. Quarterly used in Jan 2026: `dueMonth=4`, `dueYear=2026`, `dueDisplay="April"`
4. Yearly used in Jan 2026: `dueMonth=1`, `dueYear=2027`, `dueDisplay="January 2027"`
5. Lock sets `lastUsedMonth`/`lastUsedYear` from budget
6. Unlock restores previous budget's month/year (or null)

## Migration Notes

- The V3 migration backfills `last_used_month`/`last_used_year` from the budget referenced by `last_used_budget_id`
- This is a JOIN-based UPDATE, so it handles all existing data in one pass
- Expenses with NULL `last_used_budget_id` correctly get NULL month/year columns
- No data loss — `last_used_date` column is preserved but no longer used for calculations

## References

- Research: `.claude/thoughts/research/2026-02-13-recurring-expense-due-date-calculation.md`
- `DomainService.java:383-420` — current calculation methods
- `DomainService.java:843-864` — lock flow
- `DomainService.java:976-1018` — unlock/restore flow
- `RecurringExpenseDtos.java:61-72` — current list item DTO
- `RecurringExpenseExtensions.java:35-56` — current mapping
