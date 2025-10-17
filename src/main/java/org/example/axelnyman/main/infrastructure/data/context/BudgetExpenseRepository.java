package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BudgetExpenseRepository extends JpaRepository<BudgetExpense, UUID> {
    List<BudgetExpense> findAllByBudgetId(UUID budgetId);

    boolean existsByBankAccountIdAndBudget_Status(UUID bankAccountId, BudgetStatus status);
}
