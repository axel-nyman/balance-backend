package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.BudgetSavings;

import java.util.UUID;

public final class BudgetSavingsExtensions {

    private BudgetSavingsExtensions() {
        // Prevent instantiation
    }

    public static BudgetSavingsResponse toResponse(BudgetSavings savings, BankAccount bankAccount) {
        BankAccountSummary bankAccountSummary = new BankAccountSummary(
                bankAccount.getId(),
                bankAccount.getName(),
                bankAccount.getCurrentBalance()
        );

        return new BudgetSavingsResponse(
                savings.getId(),
                savings.getBudgetId(),
                savings.getName(),
                savings.getAmount(),
                bankAccountSummary,
                savings.getCreatedAt(),
                savings.getUpdatedAt()
        );
    }

    public static BudgetSavings toEntity(CreateBudgetSavingsRequest request, UUID budgetId) {
        return new BudgetSavings(
                budgetId,
                request.bankAccountId(),
                request.name(),
                request.amount()
        );
    }
}
