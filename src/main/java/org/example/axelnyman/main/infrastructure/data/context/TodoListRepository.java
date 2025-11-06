package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.TodoList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TodoList entity.
 * Provides database access for todo lists linked to budgets.
 */
@Repository
public interface TodoListRepository extends JpaRepository<TodoList, UUID> {

    /**
     * Find todo list by budget ID.
     *
     * @param budgetId The budget ID
     * @return Optional containing the todo list if found
     */
    Optional<TodoList> findByBudgetId(UUID budgetId);

    /**
     * Delete todo list by budget ID.
     * Used when relocking a budget to remove old todo list.
     *
     * @param budgetId The budget ID
     */
    void deleteByBudgetId(UUID budgetId);
}
