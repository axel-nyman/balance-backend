package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "balance_history")
@EntityListeners(AuditingEntityListener.class)
public final class BalanceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID bankAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal changeAmount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime changeDate;

    @Column(length = 500)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BalanceHistorySource source;

    @Column
    private UUID budgetId;

    // Default constructor
    public BalanceHistory() {
    }

    // Constructor for balance history creation
    public BalanceHistory(UUID bankAccountId, BigDecimal balance, BigDecimal changeAmount,
                         String comment, BalanceHistorySource source, UUID budgetId,
                         LocalDateTime changeDate) {
        this.bankAccountId = bankAccountId;
        this.balance = balance;
        this.changeAmount = changeAmount;
        this.comment = comment;
        this.source = source;
        this.budgetId = budgetId;
        this.changeDate = changeDate;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getChangeAmount() {
        return changeAmount;
    }

    public void setChangeAmount(BigDecimal changeAmount) {
        this.changeAmount = changeAmount;
    }

    public LocalDateTime getChangeDate() {
        return changeDate;
    }

    public void setChangeDate(LocalDateTime changeDate) {
        this.changeDate = changeDate;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public BalanceHistorySource getSource() {
        return source;
    }

    public void setSource(BalanceHistorySource source) {
        this.source = source;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(UUID budgetId) {
        this.budgetId = budgetId;
    }
}
