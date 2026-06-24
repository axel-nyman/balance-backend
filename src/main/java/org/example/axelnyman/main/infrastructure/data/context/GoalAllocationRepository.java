package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.GoalAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalAllocationRepository extends JpaRepository<GoalAllocation, UUID> {

    List<GoalAllocation> findAllBySavingsGoalId(UUID savingsGoalId);

    Optional<GoalAllocation> findBySavingsGoalIdAndBankAccountId(UUID savingsGoalId, UUID bankAccountId);

    @Query("SELECT COALESCE(SUM(ga.amount), 0) FROM GoalAllocation ga WHERE ga.bankAccountId = :bankAccountId")
    BigDecimal sumAmountByBankAccountId(@Param("bankAccountId") UUID bankAccountId);

    @Query("SELECT ga.bankAccountId, COALESCE(SUM(ga.amount), 0) FROM GoalAllocation ga GROUP BY ga.bankAccountId")
    List<Object[]> sumAmountGroupedByBankAccount();
}
