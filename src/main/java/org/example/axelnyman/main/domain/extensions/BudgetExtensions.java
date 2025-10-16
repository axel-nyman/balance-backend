package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.model.Budget;

import java.math.BigDecimal;

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

    public static Budget toEntity(CreateBudgetRequest request) {
        return new Budget(request.month(), request.year());
    }
}
