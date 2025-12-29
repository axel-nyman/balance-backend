---
date: 2025-12-28T12:00:00+01:00
researcher: Claude
git_commit: faeed1c6c755ad06f732e8954eec2e339bdef9e4
branch: main
repository: balance-backend
topic: "Balance History Updates and changeAmount Calculation"
tags: [research, codebase, balance-history, bank-account, changeAmount]
status: complete
last_updated: 2025-12-28
last_updated_by: Claude
---

# Research: Balance History Updates and changeAmount Calculation

**Date**: 2025-12-28T12:00:00+01:00
**Researcher**: Claude
**Git Commit**: faeed1c6c755ad06f732e8954eec2e339bdef9e4
**Branch**: main
**Repository**: balance-backend

## Research Question

How are bank account balance updates handled, specifically how is changeAmount calculated? What happens when adding entries on separate dates, then adding a third entry with a date between the first two?

## Summary

The balance history system uses an **append-only audit pattern** where:

1. `changeAmount` is always calculated as `newBalance - currentBalance` at the time of the API call
2. The bank account's `currentBalance` is updated directly, not derived from history
3. History entries are ordered by `changeDate DESC, createdAt DESC` for display
4. **Critical finding**: When adding entries out of chronological order, the `changeAmount` is calculated against the CURRENT balance at time of update, not the historical balance at the specified date

This means adding a backdated entry creates a historically inaccurate `changeAmount` value, both for the created entry but also the following entry.

## Detailed Findings

### changeAmount Calculation Logic

**Location**: `DomainService.java:163-167`

```java
// Store previous balance
BigDecimal previousBalance = account.getCurrentBalance();

// Calculate change amount (new - previous)
BigDecimal changeAmount = request.newBalance().subtract(previousBalance);
```

The calculation always uses `account.getCurrentBalance()` - the current balance at the moment of the API call, regardless of what date is specified in the request.

### Scenario: Adding Entries Out of Chronological Order

Given an account created with initial balance of 1000.00:

| Order | Action | Date               | New Balance | Current Balance (before) | changeAmount |
| ----- | ------ | ------------------ | ----------- | ------------------------ | ------------ |
| 1     | Update | Jan 1              | 1500.00     | 1000.00                  | +500.00      |
| 2     | Update | Jan 15             | 2000.00     | 1500.00                  | +500.00      |
| 3     | Update | Jan 10 (backdated) | 2200.00     | 2000.00                  | +200.00      |

**Result**: Entry #3 has `changeAmount = +200.00` but this is calculated from the current balance (2000.00), not from what the balance would have been on Jan 10 historically.

### Display Order

**Location**: `BalanceHistoryRepository.java:20`

```java
Page<BalanceHistory> findAllByBankAccountIdOrderByChangeDateDescCreatedAtDesc(UUID bankAccountId, Pageable pageable)
```

When viewing balance history, entries are ordered:

1. Primary: `changeDate DESC` (most recent date first)
2. Secondary: `createdAt DESC` (most recently created first, for same-date entries)

Using the scenario above, the display order would be:

| Display Position | changeDate | balance | changeAmount | comment           |
| ---------------- | ---------- | ------- | ------------ | ----------------- |
| 1                | Jan 15     | 2000.00 | +500.00      | -                 |
| 2                | Jan 10     | 2200.00 | +200.00      | (backdated entry) |
| 3                | Jan 1      | 1500.00 | +500.00      | -                 |

**Observation**: The balance column shows 2200.00 for Jan 10, but the account's actual balance at that point in real history was 1500.00.

### Key Architectural Decisions

1. **Balance is Source of Truth**: `BankAccount.currentBalance` is the authoritative value, updated directly on each call. Balance history is an audit log, not a ledger.

2. **No Recalculation**: The system never recalculates balance from history entries. Each update is applied to the current state.

3. **Immutable History Entries**: Once created, balance history entries cannot be modified (except AUTOMATIC entries which are deleted on budget unlock).

4. **User-Provided Dates**: The `changeDate` field stores the user-specified date, not the system timestamp. The `createdAt` field records when the entry was actually created.

### Validation Rules

**Location**: `DomainService.java:145-161`

1. Date cannot be in the future (`FutureDateException`)
2. Date cannot be before account creation (`DateBeforeAccountCreationException`)
3. Account must exist and not be soft-deleted

No validation exists to prevent backdated entries that fall between existing entries.

## Code References

- `DomainService.java:143-197` - Manual balance update implementation
- `DomainService.java:163-167` - changeAmount calculation
- `BalanceHistory.java:28-29` - changeAmount field definition (BigDecimal, precision 19, scale 2)
- `BalanceHistory.java:31-32` - changeDate field (LocalDate, immutable)
- `BalanceHistoryRepository.java:20` - Query with date ordering
- `BalanceHistoryDtos.java:12-20` - BalanceHistoryResponse DTO
- `BankAccountDtos.java:53-60` - BalanceUpdateResponse DTO

## Architecture Documentation

### Balance Update Flow

```
API Request (newBalance, date, comment)
        ↓
DomainService.updateBankAccountBalance()
        ↓
    [Validate date constraints]
        ↓
    [Get current balance from BankAccount]
        ↓
    [Calculate: changeAmount = newBalance - currentBalance]
        ↓
    [Update BankAccount.currentBalance = newBalance]
        ↓
    [Create BalanceHistory entry with changeDate from request]
        ↓
Response (BalanceUpdateResponse)
```

### Data Model Relationships

```
BankAccount (1) ←→ (N) BalanceHistory
    └── currentBalance          └── balance (snapshot)
                                └── changeAmount
                                └── changeDate (user-provided)
                                └── createdAt (auto-generated)
```

## Historical Context (from thoughts/)

- `.claude/thoughts/plans/2025-12-28-localdate-balance-updates.md` - Documents migration of changeDate from LocalDateTime to LocalDate
- `.claude/thoughts/plans/2025-12-27-fix-balance-history-changedate-bug.md` - Documents fix for changeDate using user-provided date instead of createdAt
- `.claude/thoughts/research/2025-12-28-timestamp-date-handling.md` - Research on date handling patterns
- `.claude/thoughts/research/2025-12-26-bank-account-business-rules.md` - Business rules for balance history creation

## Open Questions

1. **Is the current behavior intentional?** The system allows backdated entries but calculates changeAmount from current state, which may create confusing audit trails.

2. **Should there be validation?** Consider whether the system should warn or prevent adding entries that would create non-sequential balance snapshots.

3. **Recalculation option?** If backdated entries need to be historically accurate, the system would need to recalculate all subsequent entries (breaking the append-only pattern).
