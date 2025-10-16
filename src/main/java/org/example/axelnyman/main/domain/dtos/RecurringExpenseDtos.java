package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class RecurringExpenseDtos {

    public record CreateRecurringExpenseRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Recurrence interval is required")
            String recurrenceInterval,

            @NotNull(message = "isManual is required")
            Boolean isManual
    ) {}

    public record RecurringExpenseResponse(
            UUID id,
            String name,
            BigDecimal amount,
            String recurrenceInterval,
            Boolean isManual,
            LocalDateTime lastUsedDate,
            LocalDateTime createdAt
    ) {}
}
