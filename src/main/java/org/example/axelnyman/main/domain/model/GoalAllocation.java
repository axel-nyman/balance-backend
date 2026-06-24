package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records how much of a bank account's current balance is earmarked for a
 * savings goal. An earmark over the existing balance, not money movement.
 * At most one active allocation exists per (goal, account) pair — adjusting
 * the amount rather than creating duplicates. Removed (hard-deleted) when the
 * amount reaches zero or the goal is archived; the append-only
 * {@link GoalAllocationChange} ledger preserves the history.
 */
@Entity
@Table(name = "goal_allocations")
@EntityListeners(AuditingEntityListener.class)
public final class GoalAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID savingsGoalId;

    @Column(nullable = false)
    private UUID bankAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public GoalAllocation() {
    }

    public GoalAllocation(UUID savingsGoalId, UUID bankAccountId, BigDecimal amount) {
        this.savingsGoalId = savingsGoalId;
        this.bankAccountId = bankAccountId;
        this.amount = amount;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSavingsGoalId() {
        return savingsGoalId;
    }

    public void setSavingsGoalId(UUID savingsGoalId) {
        this.savingsGoalId = savingsGoalId;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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
}
