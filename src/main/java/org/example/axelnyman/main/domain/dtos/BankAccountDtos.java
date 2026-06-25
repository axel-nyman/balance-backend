package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BankAccountDtos {

    public record CreateBankAccountRequest(
            @NotBlank(message = "Name is required")
            String name,

            @Size(max = 500, message = "Description must be less than 500 characters")
            String description,

            @PositiveOrZero(message = "Initial balance must be zero or positive")
            BigDecimal initialBalance
    ) {}

    public record BankAccountResponse(
            UUID id,
            String name,
            String description,
            BigDecimal currentBalance,
            BigDecimal allocatedAmount,
            BigDecimal unallocatedAmount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record BankAccountListResponse(
            BigDecimal totalBalance,
            int accountCount,
            List<BankAccountResponse> accounts
    ) {}

    public record UpdateBalanceRequest(
            @NotNull(message = "New balance is required")
            BigDecimal newBalance,

            @NotNull(message = "Date is required")
            LocalDate date,

            @Size(max = 500, message = "Comment must be less than 500 characters")
            String comment,

            // Optional (item 070d). Signed per-goal allocation changes resolving a
            // balance update against this account's goal earmarks: negative reduces
            // (splits a deficit), positive adds (earmarks an increase). Absent on the
            // legacy request shape, which keeps its previous behaviour.
            @Valid
            List<ReallocationEntry> reallocation
    ) {}

    public record ReallocationEntry(
            @NotNull(message = "Savings goal id is required")
            UUID savingsGoalId,

            @NotNull(message = "Change amount is required")
            BigDecimal changeBy
    ) {}

    public record BalanceUpdateResponse(
            UUID id,
            String name,
            BigDecimal currentBalance,
            BigDecimal previousBalance,
            BigDecimal changeAmount,
            LocalDate lastUpdated,
            // Allocation earmarks adjusted as part of this balance update (item 070d):
            // the auto single-goal deficit reduction, or the reductions/additions the
            // caller requested. Empty when nothing was reallocated.
            List<AllocationAdjustment> allocationAdjustments
    ) {}

    public record AllocationAdjustment(
            UUID savingsGoalId,
            String goalName,
            BigDecimal changeAmount,
            BigDecimal resultingAmount
    ) {}

    // Machine-readable 409 body when a decrease over-allocates an account backed by
    // two or more goals and the caller supplied no split to resolve it (item 070d).
    public record ReallocationConflictResponse(
            String error,
            UUID accountId,
            String accountName,
            BigDecimal newBalance,
            BigDecimal totalAllocated,
            BigDecimal requiredReduction,
            List<ReallocationConflictGoal> goals
    ) {}

    public record ReallocationConflictGoal(
            UUID savingsGoalId,
            String goalName,
            BigDecimal currentAllocation
    ) {}

    public record UpdateBankAccountRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 255, message = "Name must be less than 255 characters")
            String name,

            @Size(max = 500, message = "Description must be less than 500 characters")
            String description
    ) {}
}
