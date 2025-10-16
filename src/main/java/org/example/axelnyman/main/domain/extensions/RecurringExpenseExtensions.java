package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.model.RecurrenceInterval;
import org.example.axelnyman.main.domain.model.RecurringExpense;

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
