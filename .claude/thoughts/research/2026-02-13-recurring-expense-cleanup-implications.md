---
date: 2026-02-13T12:00:00+01:00
researcher: Claude
git_commit: f935164060145e4e652952061e51ecbb3e9a55bd
branch: main
repository: balance-backend
topic: "Implications of removing isDue flag and lastUsedDate field from recurring expenses"
tags: [research, codebase, recurring-expenses, cleanup, isDue, lastUsedDate]
status: complete
last_updated: 2026-02-13
last_updated_by: Claude
---

# Research: Recurring Expense Cleanup Implications

**Date**: 2026-02-13
**Git Commit**: f935164060145e4e652952061e51ecbb3e9a55bd
**Branch**: main
**Repository**: balance-backend

## Research Question

What files and components would be affected by removing `isDue` and `lastUsedDate` from the recurring expense system, as proposed in `.claude/thoughts/notes/2026-02-13-recurring-expense-cleanup-candidates.md`?

## Summary

Both `isDue` and `lastUsedDate` are present across the full stack — DTOs, extensions, service logic, and tests — but they differ in scope. `isDue` is a **runtime-calculated boolean** with no database column, touching fewer files but requiring removal of dedicated calculation methods. `lastUsedDate` is a **persisted database column** that would require a Flyway migration to drop and touches both recurring expense and budget integration code.

---

## Detailed Findings

### 1. `isDue` Flag — Impact Analysis

`isDue` is **not stored in the database**. It is calculated at runtime in `DomainService` and only appears in the list endpoint response.

#### Files to Modify

| File | Lines | Change |
|------|-------|--------|
| `domain/dtos/RecurringExpenseDtos.java` | 72 | Remove `Boolean isDue` from `RecurringExpenseListItemResponse` |
| `domain/extensions/RecurringExpenseExtensions.java` | 39, 55 | Remove `Boolean isDue` parameter from `toListItemResponse()` and its usage |
| `domain/services/DomainService.java` | 70 | Remove private `DueDate` record (if `dueMonth`/`dueYear` calculation is also removed) |
| `domain/services/DomainService.java` | 345 | Remove `calculateIsDue()` call in `getAllRecurringExpenses()` |
| `domain/services/DomainService.java` | 353 | Remove `isDue` argument from `toListItemResponse()` call |
| `domain/services/DomainService.java` | 404–418 | **Delete** entire `calculateIsDue()` method |

#### Tests to Update

| File | Lines | Description |
|------|-------|-------------|
| `RecurringExpenseIntegrationTest.java` | 367 | Remove `isDue: true` assertion (never-used expense) |
| `RecurringExpenseIntegrationTest.java` | 392 | Remove `isDue: true` assertion (monthly, 2 months ago) |
| `RecurringExpenseIntegrationTest.java` | 416 | Remove `isDue: false` assertion (quarterly, 2 months ago) |
| `RecurringExpenseIntegrationTest.java` | 440 | Remove `isDue: true` assertion (biannual, 7 months ago) |
| `RecurringExpenseIntegrationTest.java` | 464 | Remove `isDue: false` assertion (yearly, 11 months ago) |
| `RecurringExpenseIntegrationTest.java` | 533 | Remove `isDue: true` assertion (overdue monthly) |
| `RecurringExpenseIntegrationTest.java` | 551 | Remove `isDue: false` assertion (recent monthly) |

**Total: 4 source files, 7 test assertions**

#### No Database Migration Required
`isDue` has no database column — removal is purely a code change.

#### Note on `dueMonth`/`dueYear`/`dueDisplay`
These calculated fields share the same `calculateNextDueDate()` and `formatDueDisplay()` helper methods. If only `isDue` is removed (keeping `dueMonth`, `dueYear`, `dueDisplay` in the list response), the `DueDate` record and `calculateNextDueDate()` must stay. Only `calculateIsDue()` is deleted.

---

### 2. `lastUsedDate` Field — Impact Analysis

`lastUsedDate` is a **database column** (`last_used_date TIMESTAMP`) on `recurring_expenses`, set during budget lock and restored during budget unlock. It appears across more layers than `isDue`.

#### Files to Modify

**Entity:**

| File | Lines | Change |
|------|-------|--------|
| `domain/model/RecurringExpense.java` | 41–42 | Remove `@Column` and `lastUsedDate` field |
| `domain/model/RecurringExpense.java` | 75 | Remove `this.lastUsedDate = null` from constructor |
| `domain/model/RecurringExpense.java` | 131–137 | Remove getter and setter |

**DTOs:**

| File | Lines | Change |
|------|-------|--------|
| `domain/dtos/RecurringExpenseDtos.java` | 56 | Remove `LocalDateTime lastUsedDate` from `RecurringExpenseResponse` |
| `domain/dtos/RecurringExpenseDtos.java` | 68 | Remove `LocalDateTime lastUsedDate` from `RecurringExpenseListItemResponse` |

**Extensions:**

| File | Lines | Change |
|------|-------|--------|
| `domain/extensions/RecurringExpenseExtensions.java` | 27 | Remove `recurringExpense.getLastUsedDate()` from `toResponse()` |
| `domain/extensions/RecurringExpenseExtensions.java` | 51 | Remove `recurringExpense.getLastUsedDate()` from `toListItemResponse()` |

**Service:**

| File | Lines | Change |
|------|-------|--------|
| `domain/services/DomainService.java` | 323 | Remove comment about not updating lastUsedDate |
| `domain/services/DomainService.java` | 870 | Remove `recurringExpense.setLastUsedDate(lockedAt)` in budget lock flow |
| `domain/services/DomainService.java` | 1018 | Remove `recurringExpense.setLastUsedDate(previousBudget.getLockedAt())` in budget unlock restore |
| `domain/services/DomainService.java` | 1023 | Remove `recurringExpense.setLastUsedDate(null)` in budget unlock reset |

#### Tests to Update

| File | Lines | Description |
|------|-------|-------------|
| `RecurringExpenseIntegrationTest.java` | 101 | Remove `$.lastUsedDate` null assertion on creation |
| `RecurringExpenseIntegrationTest.java` | 313–330 | Update/simplify `shouldSetLastUsedDateToNullOnCreation` test |
| `RecurringExpenseIntegrationTest.java` | 363 | Remove `$.expenses[0].lastUsedDate` null assertion in list |
| `RecurringExpenseIntegrationTest.java` | 664–690 | Remove/rewrite `shouldNotModifyLastUsedDateDuringUpdate` test |
| `BudgetIntegrationTest.java` | 4790 | Update `shouldUpdateRecurringExpenseLastUsedDateOnLock` test |
| `BudgetIntegrationTest.java` | 4837, 4917, 4919 | Remove `lastUsedDate` assertions in lock tests |
| `BudgetIntegrationTest.java` | 6577–6633 | Remove `lastUsedDate` assertions in unlock/restore flow |
| `BudgetIntegrationTest.java` | 6714–6727 | Remove `lastUsedDate` comparison with previous budget's `lockedAt` |

**Total: 5 source files, 8+ test locations across 2 test classes**

#### Database Migration Required

A new Flyway migration (`V4__drop_last_used_date.sql`) would be needed:

```sql
-- Rollback: ALTER TABLE recurring_expenses ADD COLUMN last_used_date TIMESTAMP;
ALTER TABLE recurring_expenses DROP COLUMN last_used_date;
```

#### Backlog Story References

The following backlog stories reference `lastUsedDate` and may need updating:
- `todo/backlog/sprint-5/34-recurring-expense-tracking-e2e-tests.md` (lines 5, 39, 42, 46, 96, 99–113, 137–143, 170)
- `todo/backlog/sprint-5/36-multi-budget-temporal-e2e-tests.md` (line 33)
- `todo/backlog/sprint-5/37-transaction-atomicity-e2e-tests.md` (line 37)

---

## Combined Impact Summary

| Aspect | `isDue` Removal | `lastUsedDate` Removal |
|--------|-----------------|------------------------|
| Database migration | No | Yes (V4) |
| Entity changes | None | Remove field + getter/setter |
| DTO changes | 1 record | 2 records |
| Extension changes | 1 method signature | 2 mapping calls |
| Service changes | Delete 1 method + 2 call sites | Remove 4 set/get calls |
| Test changes | 7 assertions | 8+ locations in 2 test classes |
| Backlog stories | None | 3 stories need updating |
| Risk level | Low (no persistence) | Medium (schema change + budget lock/unlock flow) |

---

## Code References

### isDue
- `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java:72`
- `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java:39,55`
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:70,345,353,404-418`
- `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java:367,392,416,440,464,533,551`

### lastUsedDate
- `src/main/java/org/example/axelnyman/main/domain/model/RecurringExpense.java:41-42,75,131-137`
- `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java:56,68`
- `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java:27,51`
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:323,870,1018,1023`
- `src/test/java/org/example/axelnyman/main/integration/RecurringExpenseIntegrationTest.java:101,313-330,363,664-690`
- `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java:4790,4837,4917,4919,6577-6633,6714-6727`
- `src/main/resources/db/migration/V1__baseline_schema.sql:32`

## Historical Context (from thoughts/)

- `.claude/thoughts/research/2026-02-13-recurring-expense-due-date-calculation.md` — Research on timestamp-based vs. month-based due date calculation that led to the V3 migration
- `.claude/thoughts/plans/2026-02-13-month-based-due-date-calculation.md` — Implementation plan for month-based system; `lastUsedDate` was deliberately kept during this change to preserve data
- `.claude/thoughts/research/2026-02-06-recurring-expenses-data-flow.md` — Full data flow documentation of the recurring expense feature
- `.claude/thoughts/notes/2026-02-13-recurring-expense-cleanup-candidates.md` — The originating note proposing these cleanups

## Related Research

- `.claude/thoughts/plans/2026-02-09-link-bank-account-to-recurring-expenses.md` — V2 migration context

## Open Questions

1. **Should `isDue` be replaced or just removed?** The note suggests budget-context-aware logic as an alternative. If replaced, the `calculateIsDue()` method would be rewritten (taking a budget month/year parameter) rather than deleted.
2. **Should `lastUsedDate` column be dropped immediately or in a later migration?** Dropping it now is clean but irreversible. Keeping the column but removing it from DTOs/code is a safer intermediate step.
3. **What about the 3 backlog sprint-5 stories that reference `lastUsedDate`?** These need to be updated before or during implementation to avoid writing tests for a removed field.
