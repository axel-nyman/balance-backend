---
date: 2026-02-13T12:00:00+01:00
researcher: Claude
git_commit: aff107e7e21c07e745a0f08e8481eb84d051fbd2
branch: main
repository: balance-backend
topic: "How recurring expense due dates are calculated vs desired month-based model"
tags: [research, recurring-expenses, due-dates, budget-integration]
status: complete
last_updated: 2026-02-13
last_updated_by: Claude
---

# Research: Recurring Expense Due Date Calculation - Current vs Desired

**Date**: 2026-02-13
**Git Commit**: aff107e
**Branch**: main

## Research Question

How are recurring expense due dates currently calculated, and how does that differ from a month-based mental model where expenses are "due in February" regardless of when you create the budget?

## Summary

The current system calculates due dates based on **timestamp arithmetic from the last lock date**, not based on **calendar months**. This creates a fundamental mismatch with the user's mental model where recurring expenses should be tied to specific budget months (e.g., "Due February", "Due March 2027").

### Current Model: Timestamp-Based
- `nextDueDate = lastUsedDate + interval` (e.g., lock date + 1 month for MONTHLY)
- `isDue = nextDueDate <= now()`
- Depends on *when* you lock, not *which month* the budget represents

### Desired Model: Calendar Month-Based
- Due date should be tied to the **budget's month/year**, not to when it was locked
- A monthly expense used in January's budget should show as "Due February" always
- A yearly expense paid in January should show "Due January 2027"
- `isDue` for a budget should be: "will this expense occur during this budget's month?"

## Detailed Findings

### Current Implementation

#### How `lastUsedDate` Gets Set (`DomainService.java:843-864`)

When a budget is **locked**, the system:
1. Finds all budget expenses linked to recurring expense templates
2. Sets `lastUsedDate = lockedAt` (the timestamp of when you locked the budget)
3. Sets `lastUsedBudgetId = budgetId`

**Key issue**: `lastUsedDate` is set to `LocalDateTime.now()` at lock time — it's a timestamp, not a month reference. If you lock January's budget on Jan 15 vs Jan 31, you get different `lastUsedDate` values.

#### How `nextDueDate` Is Calculated (`DomainService.java:383-398`)

```java
private LocalDateTime calculateNextDueDate(RecurringExpense expense) {
    LocalDateTime lastUsedDate = expense.getLastUsedDate();
    if (lastUsedDate == null) {
        return null;
    }
    return switch (expense.getRecurrenceInterval()) {
        case MONTHLY -> lastUsedDate.plusMonths(1);
        case QUARTERLY -> lastUsedDate.plusMonths(3);
        case BIANNUALLY -> lastUsedDate.plusMonths(6);
        case YEARLY -> lastUsedDate.plusYears(1);
    };
}
```

Example: Lock January budget on Jan 15 → `lastUsedDate = 2026-01-15T10:30:00` → `nextDueDate = 2026-02-15T10:30:00` for MONTHLY.

#### How `isDue` Is Calculated (`DomainService.java:407-420`)

```java
private Boolean calculateIsDue(LocalDateTime lastUsedDate, LocalDateTime nextDueDate) {
    if (lastUsedDate == null) return true;    // Never used = always due
    if (nextDueDate == null) return false;    // Defensive
    return nextDueDate.isBefore(LocalDateTime.now()) || nextDueDate.isEqual(LocalDateTime.now());
}
```

This compares `nextDueDate` to `LocalDateTime.now()` — a point-in-time comparison.

### Problems With Current Approach

#### Problem 1: Lock Timing Affects Due Dates
- Lock January budget on Jan 15 → nextDueDate = Feb 15
- Lock January budget on Jan 31 → nextDueDate = Feb 28/Mar 3
- Same budget month, different due dates depending on *when* you lock

#### Problem 2: `isDue` Is Time-Sensitive, Not Month-Sensitive
- If you check on Feb 1 and nextDueDate is Feb 15, `isDue = false`
- If you check on Feb 16, `isDue = true`
- But conceptually, a MONTHLY expense from January *is* due in February's budget regardless of the day

#### Problem 3: No Month Context in Due Dates
- The API returns `nextDueDate` as a `LocalDateTime` (e.g., `2026-02-15T10:30:00`)
- There's no "Due February" or "Due March 2027" concept
- The frontend would have to derive the month from the timestamp, but that timestamp is arbitrary

#### Problem 4: No Budget-Aware Due Check
- There's no method like `isDueForBudget(expense, budgetMonth, budgetYear)`
- The current `isDue` only answers "is it due right now?" not "is it due for this specific budget month?"

### Desired Model (User's Mental Model)

The user wants due dates based on **which month the expense falls in**, using the budget's `month` and `year` fields (which exist on the `Budget` entity).

#### For the Recurring Expense List Page
- Instead of `nextDueDate: "2026-02-15T10:30:00"` and `isDue: true/false`
- Show: `"Due February"` or `"Due March 2027"` (if far in the future)
- This should be based on which month the next occurrence falls in, derived from the last budget month it was used in + the recurrence interval

#### For Budget Creation/Population
- When creating February's budget, MONTHLY expenses from January should be flagged as due
- When creating February's budget, QUARTERLY expenses from December should NOT be due (next: March)
- This should work regardless of *when* in February you create the budget

### Key Data Available But Not Used

The `Budget` entity has `month` (Integer) and `year` (Integer) fields. The system could:
1. Track `lastUsedMonth`/`lastUsedYear` instead of (or in addition to) `lastUsedDate`
2. Calculate next due month/year using interval arithmetic on months
3. Compare against a target budget's month/year for `isDue`

### What Would Need to Change (Conceptual)

1. **Due date calculation**: Should produce a month/year, not a timestamp
   - MONTHLY from January 2026 → February 2026
   - QUARTERLY from January 2026 → April 2026
   - YEARLY from January 2026 → January 2027

2. **isDue check**: Should compare against a target month/year
   - "Is this expense due in February 2026?" rather than "Is this expense due right now?"

3. **API response**: Should include human-readable due month
   - `"dueMonth": "February"` or `"dueMonth": "March 2027"` (if different year)

4. **Tracking**: Could use budget month/year instead of lock timestamp
   - `lastUsedMonth = budget.getMonth()`, `lastUsedYear = budget.getYear()`

## Code References

- `DomainService.java:383-398` - `calculateNextDueDate()` - current timestamp-based calculation
- `DomainService.java:407-420` - `calculateIsDue()` - current point-in-time comparison
- `DomainService.java:335-353` - `getAllRecurringExpenses()` - list operation with calculations
- `DomainService.java:843-864` - `updateRecurringExpensesForBudget()` - sets `lastUsedDate` to lock timestamp
- `RecurringExpense.java:42` - `lastUsedDate` field (LocalDateTime)
- `RecurringExpenseDtos.java:61-72` - `RecurringExpenseListItemResponse` with `nextDueDate` and `isDue`
- `Budget.java:21-24` - `month` and `year` fields on Budget entity
- `RecurrenceInterval.java:3-8` - MONTHLY, QUARTERLY, BIANNUALLY, YEARLY enum

## Historical Context

- `research/2026-02-06-recurring-expenses-data-flow.md` - Documents the full data flow for recurring expenses
- `plans/2026-02-09-link-bank-account-to-recurring-expenses.md` - Recent bank account linking feature

## Open Questions

1. Should `lastUsedDate` (LocalDateTime) be replaced by `lastUsedMonth`/`lastUsedYear` (Integer fields), or should both coexist?
2. For the "Due February" display: should the API return a month/year pair, a formatted string, or both?
3. For expenses never used (`lastUsedDate = null`): should they show "Due [current month]" or remain as "Due now"?
4. Should the `isDue` flag on the list endpoint be relative to a specific budget month (passed as query param) or always relative to "the next unfinished budget month"?
