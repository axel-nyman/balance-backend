package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.model.*;

import java.math.BigDecimal;
import java.util.List;

public final class BudgetExtensions {

    private BudgetExtensions() {
        // Prevent instantiation
    }

    public static BudgetResponse toResponse(Budget budget) {
        BudgetTotalsResponse totals = new BudgetTotalsResponse(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
        );

        return new BudgetResponse(
                budget.getId(),
                budget.getMonth(),
                budget.getYear(),
                budget.getStatus(),
                budget.getCreatedAt(),
                budget.getLockedAt(),
                totals
        );
    }

    public static BudgetResponse toResponse(Budget budget, BudgetTotalsResponse totals) {
        return new BudgetResponse(
                budget.getId(),
                budget.getMonth(),
                budget.getYear(),
                budget.getStatus(),
                budget.getCreatedAt(),
                budget.getLockedAt(),
                totals
        );
    }

    public static Budget toEntity(CreateBudgetRequest request) {
        return new Budget(request.month(), request.year());
    }

    // Budget Detail View mappings (Story 21)
    public static BankAccountSummarySimple toBankAccountSummarySimple(BankAccount bankAccount) {
        return new BankAccountSummarySimple(
                bankAccount.getId(),
                bankAccount.getName()
        );
    }

    public static BudgetDetailIncomeResponse toDetailIncomeResponse(BudgetIncome income) {
        return new BudgetDetailIncomeResponse(
                income.getId(),
                income.getName(),
                income.getAmount(),
                toBankAccountSummarySimple(income.getBankAccount())
        );
    }

    public static BudgetDetailExpenseResponse toDetailExpenseResponse(BudgetExpense expense) {
        return new BudgetDetailExpenseResponse(
                expense.getId(),
                expense.getName(),
                expense.getAmount(),
                toBankAccountSummarySimple(expense.getBankAccount()),
                expense.getRecurringExpenseId(),
                expense.getDeductedAt(),
                expense.getIsManual()
        );
    }

    public static BudgetDetailSavingsResponse toDetailSavingsResponse(BudgetSavings savings) {
        return new BudgetDetailSavingsResponse(
                savings.getId(),
                savings.getName(),
                savings.getAmount(),
                toBankAccountSummarySimple(savings.getBankAccount())
        );
    }

    public static BudgetDetailResponse toDetailResponse(
            Budget budget,
            List<BudgetIncome> incomeList,
            List<BudgetExpense> expensesList,
            List<BudgetSavings> savingsList
    ) {
        BigDecimal totalIncome = incomeList.stream()
                .map(BudgetIncome::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalExpenses = expensesList.stream()
                .map(BudgetExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalSavings = savingsList.stream()
                .map(BudgetSavings::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal balance = totalIncome.subtract(totalExpenses).subtract(totalSavings);

        BudgetTotalsResponse totals = new BudgetTotalsResponse(
                totalIncome,
                totalExpenses,
                totalSavings,
                balance
        );

        return new BudgetDetailResponse(
                budget.getId(),
                budget.getMonth(),
                budget.getYear(),
                budget.getStatus(),
                budget.getCreatedAt(),
                budget.getLockedAt(),
                incomeList.stream().map(BudgetExtensions::toDetailIncomeResponse).toList(),
                expensesList.stream().map(BudgetExtensions::toDetailExpenseResponse).toList(),
                savingsList.stream().map(BudgetExtensions::toDetailSavingsResponse).toList(),
                totals
        );
    }
}
