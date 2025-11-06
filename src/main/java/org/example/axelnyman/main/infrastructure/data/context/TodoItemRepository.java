package org.example.axelnyman.main.infrastructure.data.context;

import org.example.axelnyman.main.domain.model.TodoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for TodoItem entity.
 * Provides database access for individual todo items within a todo list.
 */
@Repository
public interface TodoItemRepository extends JpaRepository<TodoItem, UUID> {

    /**
     * Find all todo items belonging to a todo list.
     *
     * @param todoListId The todo list ID
     * @return List of todo items
     */
    List<TodoItem> findAllByTodoListId(UUID todoListId);
}
