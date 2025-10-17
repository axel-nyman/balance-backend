package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.BudgetExpense;

import java.util.UUID;

public final class BudgetExpenseExtensions {

    private BudgetExpenseExtensions() {
        // Prevent instantiation
    }

    public static BudgetExpenseResponse toResponse(BudgetExpense expense, BankAccount bankAccount) {
        BankAccountSummary bankAccountSummary = new BankAccountSummary(
                bankAccount.getId(),
                bankAccount.getName(),
                bankAccount.getCurrentBalance()
        );

        return new BudgetExpenseResponse(
                expense.getId(),
                expense.getBudgetId(),
                expense.getName(),
                expense.getAmount(),
                bankAccountSummary,
                expense.getRecurringExpenseId(),
                expense.getDeductedAt(),
                expense.getIsManual(),
                expense.getCreatedAt()
        );
    }

    public static BudgetExpense toEntity(CreateBudgetExpenseRequest request, UUID budgetId) {
        return new BudgetExpense(
                budgetId,
                request.bankAccountId(),
                request.name(),
                request.amount(),
                request.recurringExpenseId(),
                request.deductedAt(),
                request.isManual()
        );
    }
}
