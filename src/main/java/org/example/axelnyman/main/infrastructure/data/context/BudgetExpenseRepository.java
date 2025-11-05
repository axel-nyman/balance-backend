package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetExpenseRepository extends JpaRepository<BudgetExpense, UUID> {
    List<BudgetExpense> findAllByBudgetId(UUID budgetId);

    boolean existsByBankAccountIdAndBudget_Status(UUID bankAccountId, BudgetStatus status);

    @Query("SELECT be FROM BudgetExpense be LEFT JOIN FETCH be.bankAccount WHERE be.budgetId = :budgetId")
    List<BudgetExpense> findAllByBudgetIdWithBankAccount(@Param("budgetId") UUID budgetId);

    void deleteByBudgetId(UUID budgetId);
}
