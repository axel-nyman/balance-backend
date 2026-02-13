# Recurring Expense Cleanup Candidates

## `isDue` Flag

After the month-based due date calculation change (V3 migration), `isDue` is calculated by comparing `dueMonth`/`dueYear` against the current wall-clock month/year. This isn't particularly useful — whether an expense is "due" should be determined relative to the budget being created (i.e., does the due month/year fall on or before the budget's month/year?). The current implementation means `isDue` can flip based on when you happen to check, rather than being tied to a budget context.

**Recommendation:** Remove `isDue` from the list endpoint response, or replace it with budget-context-aware logic when adding recurring expenses to a budget (e.g., highlight which expenses are due for a specific budget's month/year).

## `lastUsedDate` Field

With the introduction of `lastUsedMonth`/`lastUsedYear`, the `lastUsedDate` (LocalDateTime) field no longer drives any calculations. It was kept during the migration to avoid data loss, but it doesn't communicate anything useful to the frontend that the month/year fields don't already cover.

**Recommendation:** Remove `lastUsedDate` from the API response DTOs. The database column can be dropped in a future migration once confirmed unnecessary.
