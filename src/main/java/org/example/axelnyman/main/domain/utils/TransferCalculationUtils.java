package org.example.axelnyman.main.domain.utils;

import org.example.axelnyman.main.domain.model.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pure utility class for calculating optimal money transfers between bank accounts.
 * Uses a greedy algorithm to minimize the number of transfers needed to balance
 * all accounts according to a budget's income, expenses, and savings.
 *
 * Algorithm Overview:
 * 1. Calculate net position per account (income - expenses - savings)
 * 2. Separate accounts into surplus (positive) and deficit (negative)
 * 3. Match largest surplus with largest deficit repeatedly
 * 4. Generate minimum number of transfers
 *
 * This class contains only pure functions with no side effects or database access.
 */
public final class TransferCalculationUtils {

    private TransferCalculationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Calculates the optimal set of transfers needed to balance all accounts
     * according to the budget's income, expenses, and savings.
     *
     * @param budget The budget (used for context, not for calculation)
     * @param income List of income entries for the budget
     * @param expenses List of expense entries for the budget
     * @param savings List of savings entries for the budget
     * @return List of transfer plans needed to balance all accounts
     */
    public static List<TransferPlan> calculateTransfers(
            Budget budget,
            List<BudgetIncome> income,
            List<BudgetExpense> expenses,
            List<BudgetSavings> savings
    ) {
        // Calculate net position for each account
        List<AccountNetPosition> netPositions = calculateAccountNetPositions(income, expenses, savings);

        // Separate into surplus and deficit accounts
        List<AccountNetPosition> surplusAccounts = netPositions.stream()
                .filter(AccountNetPosition::isSurplus)
                .sorted() // Sort by absolute value descending (largest first)
                .collect(Collectors.toList());

        List<AccountNetPosition> deficitAccounts = netPositions.stream()
                .filter(AccountNetPosition::isDeficit)
                .sorted() // Sort by absolute value descending (largest first)
                .collect(Collectors.toList());

        // Generate transfers using greedy algorithm
        List<TransferPlan> transfers = new ArrayList<>();

        while (!surplusAccounts.isEmpty() && !deficitAccounts.isEmpty()) {
            // Get largest surplus and deficit
            AccountNetPosition surplus = surplusAccounts.get(0);
            AccountNetPosition deficit = deficitAccounts.get(0);

            // Calculate transfer amount (minimum of surplus and absolute deficit)
            BigDecimal transferAmount = surplus.getNetAmount().min(deficit.getNetAmount().abs());

            // Create transfer plan
            transfers.add(new TransferPlan(
                    surplus.getAccountId(),
                    deficit.getAccountId(),
                    transferAmount
            ));

            // Update positions
            surplus.setNetAmount(surplus.getNetAmount().subtract(transferAmount));
            deficit.setNetAmount(deficit.getNetAmount().add(transferAmount));

            // Remove accounts with zero balance
            if (surplus.isBalanced()) {
                surplusAccounts.remove(0);
            }
            if (deficit.isBalanced()) {
                deficitAccounts.remove(0);
            }
        }

        return transfers;
    }

    /**
     * Calculates the net financial position for each bank account.
     * Net position = income - expenses - savings
     *
     * @param income List of income entries
     * @param expenses List of expense entries
     * @param savings List of savings entries
     * @return List of net positions for each account
     */
    public static List<AccountNetPosition> calculateAccountNetPositions(
            List<BudgetIncome> income,
            List<BudgetExpense> expenses,
            List<BudgetSavings> savings
    ) {
        // Use a map to accumulate net amounts per account
        Map<UUID, BigDecimal> netAmounts = new HashMap<>();

        // Add income (positive contribution)
        for (BudgetIncome inc : income) {
            UUID accountId = inc.getBankAccountId();
            netAmounts.merge(accountId, inc.getAmount(), BigDecimal::add);
        }

        // Subtract expenses (negative contribution)
        for (BudgetExpense exp : expenses) {
            UUID accountId = exp.getBankAccountId();
            netAmounts.merge(accountId, exp.getAmount().negate(), BigDecimal::add);
        }

        // Subtract savings (negative contribution)
        for (BudgetSavings sav : savings) {
            UUID accountId = sav.getBankAccountId();
            netAmounts.merge(accountId, sav.getAmount().negate(), BigDecimal::add);
        }

        // Convert map to list of AccountNetPosition objects
        return netAmounts.entrySet().stream()
                .map(entry -> new AccountNetPosition(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }
}
