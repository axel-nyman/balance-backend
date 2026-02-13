package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.BankAccountSummary;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.RecurrenceInterval;
import org.example.axelnyman.main.domain.model.RecurringExpense;

public final class RecurringExpenseExtensions {

    private RecurringExpenseExtensions() {
        // Prevent instantiation
    }

    public static RecurringExpenseResponse toResponse(RecurringExpense recurringExpense, BankAccount bankAccount) {
        BankAccountSummary bankAccountSummary = bankAccount != null
                ? new BankAccountSummary(bankAccount.getId(), bankAccount.getName(), bankAccount.getCurrentBalance())
                : null;

        return new RecurringExpenseResponse(
                recurringExpense.getId(),
                recurringExpense.getName(),
                recurringExpense.getAmount(),
                recurringExpense.getRecurrenceInterval().name(),
                recurringExpense.getIsManual(),
                bankAccountSummary,
                recurringExpense.getLastUsedDate(),
                recurringExpense.getCreatedAt(),
                recurringExpense.getUpdatedAt()
        );
    }

    public static RecurringExpenseListItemResponse toListItemResponse(
            RecurringExpense recurringExpense,
            BankAccount bankAccount,
            Integer dueMonth,
            Integer dueYear,
            String dueDisplay,
            Boolean isDue) {
        BankAccountSummary bankAccountSummary = bankAccount != null
                ? new BankAccountSummary(bankAccount.getId(), bankAccount.getName(), bankAccount.getCurrentBalance())
                : null;

        return new RecurringExpenseListItemResponse(
                recurringExpense.getId(),
                recurringExpense.getName(),
                recurringExpense.getAmount(),
                recurringExpense.getRecurrenceInterval().name(),
                recurringExpense.getIsManual(),
                bankAccountSummary,
                recurringExpense.getLastUsedDate(),
                dueMonth,
                dueYear,
                dueDisplay,
                isDue,
                recurringExpense.getCreatedAt()
        );
    }

    public static RecurringExpense toEntity(CreateRecurringExpenseRequest request) {
        // Parse enum - will throw IllegalArgumentException if invalid
        RecurrenceInterval interval = RecurrenceInterval.valueOf(request.recurrenceInterval().toUpperCase());

        return new RecurringExpense(
                request.name(),
                request.amount(),
                interval,
                request.isManual(),
                request.bankAccountId()
        );
    }
}
