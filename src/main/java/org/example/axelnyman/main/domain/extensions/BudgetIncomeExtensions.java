package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.BudgetIncome;

import java.util.UUID;

public final class BudgetIncomeExtensions {

    private BudgetIncomeExtensions() {
        // Prevent instantiation
    }

    public static BudgetIncomeResponse toResponse(BudgetIncome income, BankAccount bankAccount) {
        BankAccountSummary bankAccountSummary = new BankAccountSummary(
                bankAccount.getId(),
                bankAccount.getName(),
                bankAccount.getCurrentBalance()
        );

        return new BudgetIncomeResponse(
                income.getId(),
                income.getBudgetId(),
                income.getName(),
                income.getAmount(),
                bankAccountSummary,
                income.getCreatedAt()
        );
    }

    public static BudgetIncome toEntity(CreateBudgetIncomeRequest request, UUID budgetId) {
        return new BudgetIncome(
                budgetId,
                request.bankAccountId(),
                request.name(),
                request.amount()
        );
    }
}
