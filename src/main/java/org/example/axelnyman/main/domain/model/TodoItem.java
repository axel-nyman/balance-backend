package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a single action item in a todo list.
 * Can be either a PAYMENT (manual expense) or TRANSFER (money movement between accounts).
 */
@Entity
@Table(name = "todo_items")
@EntityListeners(AuditingEntityListener.class)
public final class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID todoListId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoItemStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TodoItemType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column
    private UUID fromAccountId;

    @Column
    private UUID toAccountId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todoListId", insertable = false, updatable = false)
    private TodoList todoList;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fromAccountId", insertable = false, updatable = false)
    private BankAccount fromAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toAccountId", insertable = false, updatable = false)
    private BankAccount toAccount;

    // Default constructor
    public TodoItem() {
    }

    // Constructor for TRANSFER items
    public TodoItem(UUID todoListId, String name, TodoItemType type, BigDecimal amount,
                    UUID fromAccountId, UUID toAccountId) {
        this.todoListId = todoListId;
        this.name = name;
        this.status = TodoItemStatus.PENDING;
        this.type = type;
        this.amount = amount;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
    }

    // Constructor for PAYMENT items (no toAccountId)
    public TodoItem(UUID todoListId, String name, TodoItemType type, BigDecimal amount,
                    UUID fromAccountId) {
        this.todoListId = todoListId;
        this.name = name;
        this.status = TodoItemStatus.PENDING;
        this.type = type;
        this.amount = amount;
        this.fromAccountId = fromAccountId;
        this.toAccountId = null;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTodoListId() {
        return todoListId;
    }

    public void setTodoListId(UUID todoListId) {
        this.todoListId = todoListId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TodoItemStatus getStatus() {
        return status;
    }

    public void setStatus(TodoItemStatus status) {
        this.status = status;
    }

    public TodoItemType getType() {
        return type;
    }

    public void setType(TodoItemType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public void setFromAccountId(UUID fromAccountId) {
        this.fromAccountId = fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public void setToAccountId(UUID toAccountId) {
        this.toAccountId = toAccountId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public TodoList getTodoList() {
        return todoList;
    }

    public void setTodoList(TodoList todoList) {
        this.todoList = todoList;
    }

    public BankAccount getFromAccount() {
        return fromAccount;
    }

    public void setFromAccount(BankAccount fromAccount) {
        this.fromAccount = fromAccount;
    }

    public BankAccount getToAccount() {
        return toAccount;
    }

    public void setToAccount(BankAccount toAccount) {
        this.toAccount = toAccount;
    }
}
