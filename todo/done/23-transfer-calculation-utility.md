# Story 23: Transfer Calculation Utility (NEW)

**As a** system
**I want to** calculate optimal money transfers between accounts
**So that** todo generation and balance updates use identical logic

## Acceptance Criteria

- Given a budget with income/expenses/savings, calculates net position per account
- Identifies deficit accounts (need money) and surplus accounts (have money)
- Generates minimum number of transfers to balance all accounts
- Returns structured transfer plan (from_account, to_account, amount)
- Pure utility functions with no side effects or database access
- Handles edge cases: single account, zero transfers, negative balances

## Data Structure

```java
public class TransferPlan {
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;

    // constructor, getters
}

public class AccountNetPosition {
    private UUID accountId;
    private BigDecimal netAmount; // positive = surplus, negative = deficit

    // constructor, getters
}
```

## Algorithm Overview

```
1. Calculate net position per account:
   For each account:
     netPosition = income_to_account
                   - expenses_from_account
                   - savings_from_account

2. Separate accounts into:
   - Surplus accounts (netPosition > 0)
   - Deficit accounts (netPosition < 0)

3. Generate transfers:
   While deficit accounts exist:
     - Pick largest deficit account
     - Pick largest surplus account
     - Transfer min(surplus, deficit) between them
     - Update both accounts' positions
     - Remove accounts with 0 position

4. Return List<TransferPlan>
```

## Technical Implementation

1. **Utility Class**

   - Create `TransferCalculationUtils` final class in `domain/utils/` package
   - Make constructor private to prevent instantiation
   - Implement static methods:
     - `calculateTransfers(Budget budget, List<BudgetIncome> income, List<BudgetExpense> expenses, List<BudgetSavings> savings)` returns `List<TransferPlan>`
     - `calculateAccountNetPositions(...)` returns `List<AccountNetPosition>` (helper method)
   - Pure functions - take all needed data as parameters, no database access

2. **Unit Tests (CRITICAL)**

   - Test Case 1: Simple two-account transfer
     - Account A: +$1000, Account B: -$1000
     - Expected: 1 transfer, A→B, $1000

   - Test Case 2: Three accounts with multiple transfers
     - Account A: +$1500, Account B: -$800, Account C: -$700
     - Expected: 2 transfers (A→B $800, A→C $700)

   - Test Case 3: Complex example
     - Account A: $500 income, $100 expenses = +$400 net
     - Account B: $0 income, $200 savings = -$200 net
     - Account C: $0 income, $200 expenses = -$200 net
     - Expected: A→B $200, A→C $200

   - Test Case 4: Zero transfers needed
     - Account A: +$1000 income, -$1000 expenses = $0 net
     - Expected: Empty transfer list

   - Test Case 5: Single account
     - Account A: +$1000, -$1000 (balanced on same account)
     - Expected: Empty transfer list

   - Test Case 6: Multiple accounts with complex balancing
     - Test with 5+ accounts to ensure algorithm scales

3. **Domain Service Integration**
   - DomainService will call `TransferCalculationUtils.calculateTransfers(...)` when needed
   - Pass in budget data loaded from IDataService
   - Use results for todo generation and balance updates

## Definition of Done

- All acceptance criteria met
- Minimum 6 unit test cases passing
- Algorithm documented with comments explaining logic
- Pure functions (no side effects, no database access)
- Code reviewed and approved
- **THIS MUST BE COMPLETED BEFORE STORIES 24-26**
