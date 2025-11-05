package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetSavingsRepository extends JpaRepository<BudgetSavings, UUID> {

    List<BudgetSavings> findAllByBudgetId(UUID budgetId);

    boolean existsByBankAccountIdAndBudget_Status(UUID bankAccountId, BudgetStatus status);

    @Query("SELECT bs FROM BudgetSavings bs LEFT JOIN FETCH bs.bankAccount WHERE bs.budgetId = :budgetId")
    List<BudgetSavings> findAllByBudgetIdWithBankAccount(@Param("budgetId") UUID budgetId);
}
