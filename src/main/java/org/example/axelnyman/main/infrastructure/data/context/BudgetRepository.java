package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    boolean existsByMonthAndYearAndDeletedAtIsNull(Integer month, Integer year);

    boolean existsByStatusAndDeletedAtIsNull(BudgetStatus status);

    List<Budget> findAllByDeletedAtIsNullOrderByYearDescMonthDesc();

    Optional<Budget> findFirstByDeletedAtIsNullOrderByYearDescMonthDesc();

    @Query("SELECT DISTINCT b FROM Budget b " +
           "JOIN BudgetExpense be ON be.budgetId = b.id " +
           "WHERE b.status = 'LOCKED' " +
           "AND b.deletedAt IS NULL " +
           "AND be.recurringExpenseId = :recurringExpenseId " +
           "AND b.id <> :excludeBudgetId " +
           "ORDER BY b.year DESC, b.month DESC")
    List<Budget> findLockedBudgetsUsingRecurringExpense(
        @Param("recurringExpenseId") UUID recurringExpenseId,
        @Param("excludeBudgetId") UUID excludeBudgetId
    );
}
