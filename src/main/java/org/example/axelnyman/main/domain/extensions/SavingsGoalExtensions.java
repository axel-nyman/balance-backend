package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.*;
import org.example.axelnyman.main.domain.model.GoalAllocation;
import org.example.axelnyman.main.domain.model.GoalAllocationChange;
import org.example.axelnyman.main.domain.model.SavingsGoal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SavingsGoalExtensions {

    private SavingsGoalExtensions() {
        // Prevent instantiation
    }

    public static SavingsGoal toEntity(CreateSavingsGoalRequest request) {
        return new SavingsGoal(request.name(), request.targetAmount(), request.endDate());
    }

    public static SavingsGoalResponse toResponse(SavingsGoal goal, List<GoalAllocation> allocations,
                                                 Map<UUID, String> accountNames) {
        BigDecimal totalAllocated = allocations.stream()
                .map(GoalAllocation::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<GoalAccountAllocationResponse> allocationResponses = allocations.stream()
                .map(allocation -> new GoalAccountAllocationResponse(
                        allocation.getBankAccountId(),
                        accountNames.get(allocation.getBankAccountId()),
                        allocation.getAmount()))
                .toList();

        return new SavingsGoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getEndDate(),
                goal.getStatus(),
                totalAllocated,
                progressPercentage(totalAllocated, goal.getTargetAmount()),
                isCompleted(totalAllocated, goal.getTargetAmount()),
                allocationResponses,
                goal.getArchivedAt(),
                goal.getCreatedAt(),
                goal.getUpdatedAt());
    }

    public static GoalAllocationChangeResponse toResponse(GoalAllocationChange change,
                                                          Map<UUID, String> accountNames) {
        return new GoalAllocationChangeResponse(
                change.getId(),
                change.getBankAccountId(),
                accountNames.get(change.getBankAccountId()),
                change.getChangeAmount(),
                change.getResultingAmount(),
                change.getSource(),
                change.getCreatedAt());
    }

    private static BigDecimal progressPercentage(BigDecimal totalAllocated, BigDecimal targetAmount) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return totalAllocated
                .multiply(BigDecimal.valueOf(100))
                .divide(targetAmount, 2, RoundingMode.HALF_UP);
    }

    private static boolean isCompleted(BigDecimal totalAllocated, BigDecimal targetAmount) {
        return targetAmount != null && totalAllocated.compareTo(targetAmount) >= 0;
    }
}
