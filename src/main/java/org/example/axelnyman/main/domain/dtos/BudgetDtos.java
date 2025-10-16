package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.axelnyman.main.domain.model.BudgetStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class BudgetDtos {

    public record CreateBudgetRequest(
            @NotNull(message = "Month is required")
            @Min(value = 1, message = "Month must be between 1 and 12")
            @Max(value = 12, message = "Month must be between 1 and 12")
            Integer month,

            @NotNull(message = "Year is required")
            Integer year
    ) {}

    public record BudgetTotalsResponse(
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            BigDecimal balance
    ) {}

    public record BudgetResponse(
            UUID id,
            Integer month,
            Integer year,
            BudgetStatus status,
            LocalDateTime createdAt,
            LocalDateTime lockedAt,
            BudgetTotalsResponse totals
    ) {}

    public record BudgetListResponse(
            java.util.List<BudgetResponse> budgets
    ) {}
}
