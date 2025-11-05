package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    public record CreateBudgetIncomeRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId
    ) {}

    public record BankAccountSummary(
            UUID id,
            String name,
            BigDecimal currentBalance
    ) {}

    public record BudgetIncomeResponse(
            UUID id,
            UUID budgetId,
            String name,
            BigDecimal amount,
            BankAccountSummary bankAccount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record UpdateBudgetIncomeRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId
    ) {}

    public record CreateBudgetExpenseRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId,

            UUID recurringExpenseId,

            java.time.LocalDate deductedAt,

            @NotNull(message = "isManual is required")
            Boolean isManual
    ) {}

    public record UpdateBudgetExpenseRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId,

            java.time.LocalDate deductedAt,

            @NotNull(message = "isManual is required")
            Boolean isManual
    ) {}

    public record BudgetExpenseResponse(
            UUID id,
            UUID budgetId,
            String name,
            BigDecimal amount,
            BankAccountSummary bankAccount,
            UUID recurringExpenseId,
            java.time.LocalDate deductedAt,
            Boolean isManual,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record CreateBudgetSavingsRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId
    ) {}

    public record BudgetSavingsResponse(
            UUID id,
            UUID budgetId,
            String name,
            BigDecimal amount,
            BankAccountSummary bankAccount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record UpdateBudgetSavingsRequest(
            @NotBlank(message = "Name is required")
            String name,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount,

            @NotNull(message = "Bank account ID is required")
            UUID bankAccountId
    ) {}

    // ============================================
    // Budget Detail View DTOs (Story 21)
    // ============================================

    public record BankAccountSummarySimple(
            UUID id,
            String name
    ) {}

    public record BudgetDetailIncomeResponse(
            UUID id,
            String name,
            BigDecimal amount,
            BankAccountSummarySimple bankAccount
    ) {}

    public record BudgetDetailExpenseResponse(
            UUID id,
            String name,
            BigDecimal amount,
            BankAccountSummarySimple bankAccount,
            UUID recurringExpenseId,
            java.time.LocalDate deductedAt,
            Boolean isManual
    ) {}

    public record BudgetDetailSavingsResponse(
            UUID id,
            String name,
            BigDecimal amount,
            BankAccountSummarySimple bankAccount
    ) {}

    public record BudgetDetailResponse(
            UUID id,
            Integer month,
            Integer year,
            BudgetStatus status,
            LocalDateTime createdAt,
            LocalDateTime lockedAt,
            java.util.List<BudgetDetailIncomeResponse> income,
            java.util.List<BudgetDetailExpenseResponse> expenses,
            java.util.List<BudgetDetailSavingsResponse> savings,
            BudgetTotalsResponse totals
    ) {}
}
