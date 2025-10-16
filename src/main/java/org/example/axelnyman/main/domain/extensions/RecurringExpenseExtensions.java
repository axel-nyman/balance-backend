package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.model.RecurrenceInterval;
import org.example.axelnyman.main.domain.model.RecurringExpense;

import java.time.LocalDateTime;

public final class RecurringExpenseExtensions {

    private RecurringExpenseExtensions() {
        // Prevent instantiation
    }

    public static RecurringExpenseResponse toResponse(RecurringExpense recurringExpense) {
        return new RecurringExpenseResponse(
                recurringExpense.getId(),
                recurringExpense.getName(),
                recurringExpense.getAmount(),
                recurringExpense.getRecurrenceInterval().name(),
                recurringExpense.getIsManual(),
                recurringExpense.getLastUsedDate(),
                recurringExpense.getCreatedAt(),
                recurringExpense.getUpdatedAt()
        );
    }

    public static RecurringExpenseListItemResponse toListItemResponse(
            RecurringExpense recurringExpense,
            LocalDateTime nextDueDate,
            Boolean isDue) {
        return new RecurringExpenseListItemResponse(
                recurringExpense.getId(),
                recurringExpense.getName(),
                recurringExpense.getAmount(),
                recurringExpense.getRecurrenceInterval().name(),
                recurringExpense.getIsManual(),
                recurringExpense.getLastUsedDate(),
                nextDueDate,
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
                request.isManual()
        );
    }
}
