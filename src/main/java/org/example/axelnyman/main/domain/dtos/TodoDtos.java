package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.constraints.NotNull;
import org.example.axelnyman.main.domain.model.TodoItemStatus;
import org.example.axelnyman.main.domain.model.TodoItemType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TodoDtos {

    /**
     * Nested record representing basic bank account information.
     * Used in todo items to show account details.
     */
    public record AccountSummary(
            UUID id,
            String name
    ) {}

    /**
     * Response record for a single todo item.
     * Includes nested account details for from/to accounts.
     */
    public record TodoItemResponse(
            UUID id,
            String name,
            TodoItemStatus status,
            TodoItemType type,
            BigDecimal amount,
            AccountSummary fromAccount,
            AccountSummary toAccount,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {}

    /**
     * Summary statistics for a todo list.
     * Shows counts of total, pending, and completed items.
     */
    public record TodoSummaryResponse(
            int totalItems,
            int pendingItems,
            int completedItems
    ) {}

    /**
     * Complete todo list response.
     * Includes all items and summary statistics.
     */
    public record TodoListResponse(
            UUID id,
            UUID budgetId,
            LocalDateTime createdAt,
            List<TodoItemResponse> items,
            TodoSummaryResponse summary
    ) {}

    /**
     * Request record for updating a todo item status.
     */
    public record UpdateTodoItemRequest(
            @NotNull(message = "Status is required")
            TodoItemStatus status
    ) {}
}
