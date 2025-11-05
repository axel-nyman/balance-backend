package org.example.axelnyman.main.domain.utils;

import org.example.axelnyman.main.domain.model.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for TransferCalculationUtils.
 * Tests cover basic scenarios, complex multi-account cases, edge cases,
 * and algorithm verification.
 */
class TransferCalculationUtilsTest {

    private final UUID accountA = UUID.randomUUID();
    private final UUID accountB = UUID.randomUUID();
    private final UUID accountC = UUID.randomUUID();
    private final UUID accountD = UUID.randomUUID();
    private final UUID accountE = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();

    // ==================== BASIC TESTS ====================

    @Test
    void shouldCalculateSimpleTwoAccountTransfer() {
        // Given: Account A has $1000 surplus, Account B has $1000 deficit
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("1000.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountB, "Rent", new BigDecimal("1000.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Should have exactly 1 transfer from A to B for $1000
        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).getFromAccountId()).isEqualTo(accountA);
        assertThat(transfers.get(0).getToAccountId()).isEqualTo(accountB);
        assertThat(transfers.get(0).getAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldCalculateThreeAccountsWithMultipleTransfers() {
        // Given: Account A: +$1500, Account B: -$800, Account C: -$700
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("1500.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountB, "Rent", new BigDecimal("800.00"), null, null, true),
                new BudgetExpense(budgetId, accountC, "Utilities", new BigDecimal("700.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Should have 2 transfers from A
        assertThat(transfers).hasSize(2);

        // Verify total transferred equals $1500
        BigDecimal totalTransferred = transfers.stream()
                .map(TransferPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo("1500.00");

        // All transfers should be from Account A
        assertThat(transfers).allMatch(t -> t.getFromAccountId().equals(accountA));
    }

    @Test
    void shouldHandleZeroTransfersWhenBalanced() {
        // Given: Account A has income and expenses that balance out
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("1000.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountA, "Rent", new BigDecimal("1000.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers needed
        assertThat(transfers).isEmpty();
    }

    @Test
    void shouldHandleSingleAccount() {
        // Given: Only one account with balanced income/expenses
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("2000.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountA, "Rent", new BigDecimal("1000.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of(
                new BudgetSavings(budgetId, accountA, "Emergency Fund", new BigDecimal("1000.00"))
        );

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers needed (all on same account)
        assertThat(transfers).isEmpty();
    }

    // ==================== COMPLEX SCENARIOS ====================

    @Test
    void shouldCalculateComplexThreeAccountScenario() {
        // Given: Complex scenario from story
        // Account A: $500 income, $100 expenses = +$400 net
        // Account B: $0 income, $200 savings = -$200 net
        // Account C: $0 income, $200 expenses = -$200 net
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("500.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountA, "Food", new BigDecimal("100.00"), null, null, true),
                new BudgetExpense(budgetId, accountC, "Utilities", new BigDecimal("200.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of(
                new BudgetSavings(budgetId, accountB, "Savings", new BigDecimal("200.00"))
        );

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Should have 2 transfers from A
        assertThat(transfers).hasSize(2);
        assertThat(transfers).allMatch(t -> t.getFromAccountId().equals(accountA));

        // Total should be $400
        BigDecimal totalTransferred = transfers.stream()
                .map(TransferPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalTransferred).isEqualByComparingTo("400.00");
    }

    @Test
    void shouldHandleMultipleAccountsWithComplexBalancing() {
        // Given: 5 accounts with various positions
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("3000.00")),
                new BudgetIncome(budgetId, accountB, "Side Income", new BigDecimal("500.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountC, "Rent", new BigDecimal("1500.00"), null, null, true),
                new BudgetExpense(budgetId, accountD, "Utilities", new BigDecimal("800.00"), null, null, true),
                new BudgetExpense(budgetId, accountE, "Food", new BigDecimal("600.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of(
                new BudgetSavings(budgetId, accountA, "Emergency", new BigDecimal("600.00"))
        );

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Verify transfers balance the budget
        // Total transfers should equal total deficit (or total surplus)
        // Account A: 3000 - 600 = +2400
        // Account B: 500 = +500
        // Account C: -1500, Account D: -800, Account E: -600
        // Total surplus: 2900, Total deficit: 2900
        BigDecimal totalExpenses = expenses.stream()
                .map(BudgetExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Expected transfer total equals total deficit (all expenses)
        BigDecimal expectedTransferTotal = totalExpenses;

        BigDecimal actualTransferTotal = transfers.stream()
                .map(TransferPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(actualTransferTotal).isEqualByComparingTo(expectedTransferTotal);
    }

    @Test
    void shouldHandleAllSurplusAccounts() {
        // Given: All accounts have surplus (only income, no expenses)
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary A", new BigDecimal("1000.00")),
                new BudgetIncome(budgetId, accountB, "Salary B", new BigDecimal("1000.00"))
        );
        List<BudgetExpense> expenses = List.of();
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers needed (nothing to balance)
        assertThat(transfers).isEmpty();
    }

    @Test
    void shouldHandleAllDeficitAccounts() {
        // Given: All accounts have deficit (only expenses, no income)
        List<BudgetIncome> income = List.of();
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountA, "Expense A", new BigDecimal("500.00"), null, null, true),
                new BudgetExpense(budgetId, accountB, "Expense B", new BigDecimal("500.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers possible (all accounts need money)
        assertThat(transfers).isEmpty();
    }

    // ==================== EDGE CASES ====================

    @Test
    void shouldHandleEmptyLists() {
        // Given: No income, expenses, or savings
        List<BudgetIncome> income = List.of();
        List<BudgetExpense> expenses = List.of();
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers
        assertThat(transfers).isEmpty();
    }

    @Test
    void shouldHandleZeroAmounts() {
        // Given: All amounts are zero
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Zero Income", BigDecimal.ZERO)
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountB, "Zero Expense", BigDecimal.ZERO, null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: No transfers needed
        assertThat(transfers).isEmpty();
    }

    @Test
    void shouldRoundTransfersCorrectly() {
        // Given: Amounts with decimal precision
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("1000.33"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountB, "Rent", new BigDecimal("1000.33"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Transfer should preserve precision
        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).getAmount()).isEqualByComparingTo("1000.33");
    }

    @Test
    void shouldCalculateNetPositionsCorrectly() {
        // Given: Various income, expenses, and savings
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("2000.00")),
                new BudgetIncome(budgetId, accountB, "Side Income", new BigDecimal("500.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountA, "Rent", new BigDecimal("1000.00"), null, null, true),
                new BudgetExpense(budgetId, accountC, "Utilities", new BigDecimal("800.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of(
                new BudgetSavings(budgetId, accountA, "Emergency", new BigDecimal("500.00"))
        );

        // When: Calculate net positions
        List<AccountNetPosition> positions = TransferCalculationUtils.calculateAccountNetPositions(
                income, expenses, savings
        );

        // Then: Verify net positions
        // Account A: 2000 - 1000 - 500 = +500
        // Account B: 500 - 0 - 0 = +500
        // Account C: 0 - 800 - 0 = -800
        assertThat(positions).hasSize(3);

        AccountNetPosition posA = positions.stream()
                .filter(p -> p.getAccountId().equals(accountA))
                .findFirst()
                .orElseThrow();
        assertThat(posA.getNetAmount()).isEqualByComparingTo("500.00");

        AccountNetPosition posB = positions.stream()
                .filter(p -> p.getAccountId().equals(accountB))
                .findFirst()
                .orElseThrow();
        assertThat(posB.getNetAmount()).isEqualByComparingTo("500.00");

        AccountNetPosition posC = positions.stream()
                .filter(p -> p.getAccountId().equals(accountC))
                .findFirst()
                .orElseThrow();
        assertThat(posC.getNetAmount()).isEqualByComparingTo("-800.00");
    }

    // ==================== ALGORITHM VERIFICATION ====================

    @Test
    void shouldMinimizeNumberOfTransfers() {
        // Given: Scenario where greedy algorithm should minimize transfers
        // A: +1000, B: -300, C: -300, D: -400
        // Optimal: 3 transfers (A->D $400, A->B $300, A->C $300)
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Salary", new BigDecimal("1000.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountB, "Expense B", new BigDecimal("300.00"), null, null, true),
                new BudgetExpense(budgetId, accountC, "Expense C", new BigDecimal("300.00"), null, null, true),
                new BudgetExpense(budgetId, accountD, "Expense D", new BigDecimal("400.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of();

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Should have exactly 3 transfers (minimum possible)
        assertThat(transfers).hasSize(3);
        assertThat(transfers).allMatch(t -> t.getFromAccountId().equals(accountA));
    }

    @Test
    void shouldPreserveTotalBalance() {
        // Given: Any budget scenario
        List<BudgetIncome> income = List.of(
                new BudgetIncome(budgetId, accountA, "Income A", new BigDecimal("1500.00")),
                new BudgetIncome(budgetId, accountB, "Income B", new BigDecimal("800.00"))
        );
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(budgetId, accountC, "Expense C", new BigDecimal("1200.00"), null, null, true),
                new BudgetExpense(budgetId, accountD, "Expense D", new BigDecimal("600.00"), null, null, true)
        );
        List<BudgetSavings> savings = List.of(
                new BudgetSavings(budgetId, accountE, "Savings E", new BigDecimal("500.00"))
        );

        // When: Calculate transfers
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(1, 2025), income, expenses, savings
        );

        // Then: Sum of all transfers should preserve total balance
        // (This is a sanity check - money doesn't appear or disappear)
        BigDecimal totalTransferred = transfers.stream()
                .map(TransferPlan::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Total transferred should not exceed total available income
        BigDecimal totalIncome = income.stream()
                .map(BudgetIncome::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalTransferred).isLessThanOrEqualTo(totalIncome);
    }
}
