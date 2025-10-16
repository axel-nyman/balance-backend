package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
    boolean existsByNameAndDeletedAtIsNull(String name);
}
