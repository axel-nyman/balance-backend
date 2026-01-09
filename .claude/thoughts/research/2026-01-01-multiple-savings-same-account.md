---
date: 2026-01-01T00:00:00+01:00
researcher: Claude
git_commit: 67081917df23a844b23bf4e962593a5d1f9258df
branch: main
repository: balance-backend
topic: "Multiple savings items to same bank account and transfer calculation handling"
tags: [research, codebase, budget-savings, transfer-calculation, domain-model]
status: complete
last_updated: 2026-01-01
last_updated_by: Claude
---

# Research: Multiple Savings Items to Same Bank Account

**Date**: 2026-01-01
**Researcher**: Claude
**Git Commit**: 67081917df23a844b23bf4e962593a5d1f9258df
**Branch**: main
**Repository**: balance-backend

## Research Question
Can multiple savings items in a single budget be allocated to a single bank account? Can the transfer calculation script handle such a scenario?

## Summary

**Yes to both questions.**

1. **Multiple savings items CAN be allocated to the same bank account** - The `BudgetSavings` entity has no unique constraint on `bankAccountId`. Each savings item is a separate entity with its own UUID, allowing multiple items to reference the same account.

2. **The transfer calculation utility correctly handles this** - The algorithm uses `Map.merge()` to accumulate net amounts per account, automatically summing multiple savings entries for the same account.

## Detailed Findings

### BudgetSavings Entity Structure

The entity at `src/main/java/org/example/axelnyman/main/domain/model/BudgetSavings.java` defines:

```java
@Entity
@Table(name = "budget_savings")
public final class BudgetSavings {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID budgetId;

    @Column(nullable = false)
    private UUID bankAccountId;  // No unique constraint

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    // ...
}
```

Key observation: `bankAccountId` has `nullable = false` but **no unique constraint**. Multiple savings items with the same `budgetId` + `bankAccountId` combination are permitted.

### Transfer Calculation Algorithm

Located at `src/main/java/org/example/axelnyman/main/domain/utils/TransferCalculationUtils.java:101-131`

The `calculateAccountNetPositions` method handles multiple savings to the same account:

```java
public static List<AccountNetPosition> calculateAccountNetPositions(
        List<BudgetIncome> income,
        List<BudgetExpense> expenses,
        List<BudgetSavings> savings
) {
    Map<UUID, BigDecimal> netAmounts = new HashMap<>();

    // Add income (positive contribution)
    for (BudgetIncome inc : income) {
        netAmounts.merge(inc.getBankAccountId(), inc.getAmount(), BigDecimal::add);
    }

    // Subtract expenses (negative contribution)
    for (BudgetExpense exp : expenses) {
        netAmounts.merge(exp.getBankAccountId(), exp.getAmount().negate(), BigDecimal::add);
    }

    // Subtract savings (negative contribution)
    for (BudgetSavings sav : savings) {
        netAmounts.merge(sav.getBankAccountId(), sav.getAmount().negate(), BigDecimal::add);
    }

    return netAmounts.entrySet().stream()
            .map(entry -> new AccountNetPosition(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
}
```

The `Map.merge()` operation automatically accumulates amounts when the same `bankAccountId` appears multiple times. If Account A has three savings entries of $200, $250, and $150, the map will contain Account A → -$600 (negated because savings reduce available balance).

### Budget Lock Balance Update

The `DomainService.updateBalancesForBudget()` method at `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:829-858` explicitly groups savings by account:

```java
// Group savings by bankAccountId and sum amounts
Map<UUID, BigDecimal> savingsByAccount = allSavings.stream()
        .collect(Collectors.groupingBy(
                BudgetSavings::getBankAccountId,
                Collectors.reducing(
                        BigDecimal.ZERO,
                        BudgetSavings::getAmount,
                        BigDecimal::add
                )
        ));
```

This aggregates all savings for the same account before updating the account balance.

### Explicit Test Coverage

Integration test at `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java:5700-5776`:

```java
@Test
void shouldSumMultipleSavingsItemsToSameAccount() throws Exception {
    // Creates 3 savings items to same account:
    // - Emergency Fund: $200
    // - Vacation Fund: $250
    // - Investment: $150
    // Verifies total of $600 is added to account balance on budget lock
}
```

## Code References

- `src/main/java/org/example/axelnyman/main/domain/model/BudgetSavings.java:1-48` - Entity definition (no unique constraint on bankAccountId)
- `src/main/java/org/example/axelnyman/main/domain/utils/TransferCalculationUtils.java:101-131` - Net position calculation with Map.merge()
- `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:829-858` - Grouping and summing savings by account
- `src/test/java/org/example/axelnyman/main/integration/BudgetIntegrationTest.java:5700-5776` - Integration test for multiple savings to same account

## Architecture Documentation

The system treats savings as allocations that reduce an account's net position:

```
Net Position = Income - Expenses - Savings
```

When multiple savings reference the same account:
1. **Transfer calculation**: Each savings entry is processed individually; `Map.merge()` accumulates the total automatically
2. **Budget lock**: `Collectors.groupingBy()` aggregates all savings per account before updating balances
3. **Business model**: Each savings item maintains its own identity (ID, name, amount) while sharing a destination account

## Open Questions

None - the research question has been fully answered.
