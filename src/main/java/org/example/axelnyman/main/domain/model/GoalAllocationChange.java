package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only ledger of every change to a {@link GoalAllocation} over time.
 * Mirrors {@link BalanceHistory}: per-entity, immutable, with a {@code source}
 * enum. Written on every allocation create/increase/reduce/remove and never
 * updated or deleted — it survives goal archiving so historical saving data is
 * preserved for future statistics.
 */
@Entity
@Table(name = "goal_allocation_changes")
@EntityListeners(AuditingEntityListener.class)
public final class GoalAllocationChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID savingsGoalId;

    @Column(nullable = false)
    private UUID bankAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal changeAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal resultingAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GoalAllocationChangeSource source;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public GoalAllocationChange() {
    }

    public GoalAllocationChange(UUID savingsGoalId, UUID bankAccountId, BigDecimal changeAmount,
                                BigDecimal resultingAmount, GoalAllocationChangeSource source) {
        this.savingsGoalId = savingsGoalId;
        this.bankAccountId = bankAccountId;
        this.changeAmount = changeAmount;
        this.resultingAmount = resultingAmount;
        this.source = source;
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

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public BigDecimal getResultingAmount() {
        return resultingAmount;
    }

    public void setResultingAmount(BigDecimal resultingAmount) {
        this.resultingAmount = resultingAmount;
    }

    public GoalAllocationChangeSource getSource() {
        return source;
    }

    public void setSource(GoalAllocationChangeSource source) {
        this.source = source;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
