package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetIncomeRepository extends JpaRepository<BudgetIncome, UUID> {

    List<BudgetIncome> findAllByBudgetId(UUID budgetId);

    boolean existsByBankAccountIdAndBudget_Status(UUID bankAccountId, BudgetStatus status);

    @Query("SELECT bi FROM BudgetIncome bi LEFT JOIN FETCH bi.bankAccount WHERE bi.budgetId = :budgetId")
    List<BudgetIncome> findAllByBudgetIdWithBankAccount(@Param("budgetId") UUID budgetId);
}
