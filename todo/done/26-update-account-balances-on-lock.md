# Story 26: Update Account Balances on Lock

**As a** system process
**I want to** automatically update bank account balances when a budget is locked
**So that** account balances reflect savings allocations

## Acceptance Criteria

- Triggered automatically during budget lock (part of same transaction)
- Updates `currentBalance` on each affected bank account
- Creates `BalanceHistory` entries with source='AUTOMATIC' and budgetId set
- Account balances increase by savings allocated to that account
- Updates are atomic (all succeed or all fail)
- Can be reversed when budget is unlocked
- Income and expenses do NOT affect account balances (they cancel out in the budget)

## Balance Update Logic

```
For each bank account that has savings allocated:
  1. Get current balance
  2. Calculate total savings for this account: SUM(savings where bankAccountId = account.id)
  3. new_balance = current_balance + total_savings
  4. Update account's currentBalance
  5. Create BalanceHistory entry with:
     - source = AUTOMATIC
     - budgetId = {budgetId}
     - changeAmount = total_savings
     - balance = new_balance
     - comment = "Budget lock for {Month Year}"
```

## Example

```
Starting State:
- Account A: balance = $500
- Account B: balance = $300
- Account C: balance = $1000

Budget (balanced):
Income:
- Account A: $500
Total Income: $500

Expenses:
- Account B: $100
- Account C: $100
Total Expenses: $200

Savings:
- Account A: $100
- Account B: $100
- Account C: $100
Total Savings: $300

Budget Balance Check: $500 - $200 - $300 = $0 ✓

Balance Updates (ONLY savings affect balances):
- Account A: $500 + $100 = $600
- Account B: $300 + $100 = $400
- Account C: $1000 + $100 = $1100
```

## Technical Implementation

1. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update balances for budget (called during lock)
   - Implement in `DomainService`:
     - **updateBalancesForBudget(UUID budgetId)**:
       - Load budget from IDataService
       - Load all savings items for this budget from IDataService
       - Group savings by bankAccountId and sum amounts
       - For each account with savings:
         - Load bank account from IDataService
         - Calculate change amount: SUM(savings for this account)
         - Calculate new balance: current_balance + change_amount
         - Update account.currentBalance
         - Create BalanceHistory entry:
           - source = AUTOMATIC
           - budgetId = {budgetId}
           - changeAmount = total savings for account
           - balance = new balance after savings
           - changeDate = now
           - comment = "Budget lock for {Month}/{Year}"
         - Save account and history via IDataService

2. **Data Service Layer**

   - Methods already exist from previous stories:
     - Get budget by id
     - Get all savings for budget
     - Get bank account by id
     - Save bank account
     - Save balance history

3. **Integration with Budget Lock**

   - Modify `DomainService.lockBudget()` method:
     - After todo list generation (Story 25)
     - Before committing transaction
     - Call internal `updateBalancesForBudget(budgetId)` method
     - If balance update fails, rollback entire transaction

4. **Integration Tests**
   - Test balance updates on budget lock
   - Test BalanceHistory entries created with correct source and budgetId
   - Test final balances match: old_balance + savings
   - Test accounts without savings are not affected
   - Test transaction rollback on failure
   - Test the example scenario above
   - Test with multiple savings items to same account (should sum correctly)

## Definition of Done

- All acceptance criteria met
- Balance calculation logic is simple: current + savings only
- Integration tests passing with example scenario
- Transaction handling verified
- budgetId properly set on history entries
- Code reviewed and approved
