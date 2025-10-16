package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    boolean existsByMonthAndYearAndDeletedAtIsNull(Integer month, Integer year);

    boolean existsByStatusAndDeletedAtIsNull(BudgetStatus status);
}
