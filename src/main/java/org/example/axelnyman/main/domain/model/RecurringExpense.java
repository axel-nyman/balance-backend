package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "recurring_expenses")
@EntityListeners(AuditingEntityListener.class)
public final class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurrenceInterval recurrenceInterval;

    @Column(nullable = false)
    private Boolean isManual;

    @Column(name = "last_used_date")
    private LocalDateTime lastUsedDate;

    @Column(name = "last_used_budget_id")
    private UUID lastUsedBudgetId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Default constructor
    public RecurringExpense() {
    }

    // Constructor for recurring expense creation
    public RecurringExpense(String name, BigDecimal amount, RecurrenceInterval recurrenceInterval, Boolean isManual) {
        this.name = name;
        this.amount = amount;
        this.recurrenceInterval = recurrenceInterval;
        this.isManual = isManual != null ? isManual : false;
        this.lastUsedDate = null; // Initially null, set when used in budget
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public RecurrenceInterval getRecurrenceInterval() {
        return recurrenceInterval;
    }

    public void setRecurrenceInterval(RecurrenceInterval recurrenceInterval) {
        this.recurrenceInterval = recurrenceInterval;
    }

    public Boolean getIsManual() {
        return isManual;
    }

    public void setIsManual(Boolean isManual) {
        this.isManual = isManual;
    }

    public LocalDateTime getLastUsedDate() {
        return lastUsedDate;
    }

    public void setLastUsedDate(LocalDateTime lastUsedDate) {
        this.lastUsedDate = lastUsedDate;
    }

    public UUID getLastUsedBudgetId() {
        return lastUsedBudgetId;
    }

    public void setLastUsedBudgetId(UUID lastUsedBudgetId) {
        this.lastUsedBudgetId = lastUsedBudgetId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
