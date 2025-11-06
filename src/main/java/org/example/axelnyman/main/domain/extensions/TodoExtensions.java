package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.TodoDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.TodoItem;
import org.example.axelnyman.main.domain.model.TodoItemStatus;
import org.example.axelnyman.main.domain.model.TodoList;

import java.util.List;

/**
 * Extension methods for mapping Todo entities to DTOs.
 */
public final class TodoExtensions {

    private TodoExtensions() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Maps a BankAccount entity to an AccountSummary DTO.
     *
     * @param account The bank account entity
     * @return AccountSummary DTO
     */
    public static AccountSummary toAccountSummary(BankAccount account) {
        if (account == null) {
            return null;
        }
        return new AccountSummary(account.getId(), account.getName());
    }

    /**
     * Maps a TodoItem entity to a TodoItemResponse DTO.
     * Requires BankAccount entities to be loaded for proper mapping.
     *
     * @param item TodoItem entity
     * @param fromAccount From account entity (required for TRANSFER, can be null for PAYMENT)
     * @param toAccount To account entity (nullable)
     * @return TodoItemResponse DTO
     */
    public static TodoItemResponse toItemResponse(TodoItem item, BankAccount fromAccount, BankAccount toAccount) {
        return new TodoItemResponse(
                item.getId(),
                item.getName(),
                item.getStatus(),
                item.getType(),
                item.getAmount(),
                toAccountSummary(fromAccount),
                toAccountSummary(toAccount),
                item.getCreatedAt(),
                item.getCompletedAt()
        );
    }

    /**
     * Maps a TodoList entity with items to a TodoListResponse DTO.
     *
     * @param todoList TodoList entity
     * @param items List of mapped TodoItemResponse DTOs
     * @return TodoListResponse DTO with summary
     */
    public static TodoListResponse toResponse(TodoList todoList, List<TodoItemResponse> items) {
        TodoSummaryResponse summary = calculateSummary(items);
        return new TodoListResponse(
                todoList.getId(),
                todoList.getBudgetId(),
                todoList.getCreatedAt(),
                items,
                summary
        );
    }

    /**
     * Calculates summary statistics for a list of todo items.
     *
     * @param items List of TodoItemResponse DTOs
     * @return TodoSummaryResponse with counts
     */
    private static TodoSummaryResponse calculateSummary(List<TodoItemResponse> items) {
        int totalItems = items.size();
        int completedItems = (int) items.stream()
                .filter(item -> item.status() == TodoItemStatus.COMPLETED)
                .count();
        int pendingItems = totalItems - completedItems;

        return new TodoSummaryResponse(totalItems, pendingItems, completedItems);
    }
}
