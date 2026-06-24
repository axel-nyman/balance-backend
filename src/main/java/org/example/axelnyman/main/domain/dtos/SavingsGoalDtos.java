package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.example.axelnyman.main.domain.model.GoalAllocationChangeSource;
import org.example.axelnyman.main.domain.model.GoalStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class SavingsGoalDtos {

    public record CreateSavingsGoalRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must be less than 255 characters")
            String name,

            @Positive(message = "Target amount must be positive")
            BigDecimal targetAmount,

            LocalDate endDate,

            @Valid
            List<SeedAllocationRequest> allocations
    ) {}

    public record SeedAllocationRequest(
            @NotNull(message = "Bank account id is required")
            UUID bankAccountId,

            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            BigDecimal amount
    ) {}

    public record UpdateSavingsGoalRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must be less than 255 characters")
            String name,

            @Positive(message = "Target amount must be positive")
            BigDecimal targetAmount,

            LocalDate endDate
    ) {}

    public record AllocateRequest(
            @NotNull(message = "Bank account id is required")
            UUID bankAccountId,

            @NotNull(message = "Amount is required")
            @PositiveOrZero(message = "Amount must be zero or positive")
            BigDecimal amount
    ) {}

    public record ArchiveRequest(
            boolean releaseToBalance
    ) {}

    public record GoalAccountAllocationResponse(
            UUID bankAccountId,
            String bankAccountName,
            BigDecimal amount
    ) {}

    public record SavingsGoalResponse(
            UUID id,
            String name,
            BigDecimal targetAmount,
            LocalDate endDate,
            GoalStatus status,
            BigDecimal totalAllocated,
            BigDecimal progressPercentage,
            boolean completed,
            List<GoalAccountAllocationResponse> allocations,
            LocalDateTime archivedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record SavingsGoalListResponse(
            int goalCount,
            List<SavingsGoalResponse> goals
    ) {}

    public record GoalAllocationChangeResponse(
            UUID id,
            UUID bankAccountId,
            String bankAccountName,
            BigDecimal changeAmount,
            BigDecimal resultingAmount,
            GoalAllocationChangeSource source,
            LocalDateTime createdAt
    ) {}

    public record GoalAllocationHistoryResponse(
            UUID goalId,
            List<GoalAllocationChangeResponse> changes
    ) {}
}
