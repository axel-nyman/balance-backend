package org.example.axelnyman.main.domain.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "budget_expenses")
@EntityListeners(AuditingEntityListener.class)
public final class BudgetExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID budgetId;

    @Column(nullable = false)
    private UUID bankAccountId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column
    private UUID recurringExpenseId;

    @Column
    private LocalDate deductedAt;

    @Column(nullable = false)
    private Boolean isManual;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budgetId", insertable = false, updatable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bankAccountId", insertable = false, updatable = false)
    private BankAccount bankAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recurringExpenseId", insertable = false, updatable = false)
    private RecurringExpense recurringExpense;

    // Default constructor
    public BudgetExpense() {
    }

    // Constructor for budget expense creation
    public BudgetExpense(UUID budgetId, UUID bankAccountId, String name, BigDecimal amount,
                         UUID recurringExpenseId, LocalDate deductedAt, Boolean isManual) {
        this.budgetId = budgetId;
        this.bankAccountId = bankAccountId;
        this.name = name;
        this.amount = amount;
        this.recurringExpenseId = recurringExpenseId;
        this.deductedAt = deductedAt;
        this.isManual = isManual;
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getBudgetId() {
        return budgetId;
    }

    public void setBudgetId(UUID budgetId) {
        this.budgetId = budgetId;
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
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

    public UUID getRecurringExpenseId() {
        return recurringExpenseId;
    }

    public void setRecurringExpenseId(UUID recurringExpenseId) {
        this.recurringExpenseId = recurringExpenseId;
    }

    public LocalDate getDeductedAt() {
        return deductedAt;
    }

    public void setDeductedAt(LocalDate deductedAt) {
        this.deductedAt = deductedAt;
    }

    public Boolean getIsManual() {
        return isManual;
    }

    public void setIsManual(Boolean isManual) {
        this.isManual = isManual;
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

    public Budget getBudget() {
        return budget;
    }

    public void setBudget(Budget budget) {
        this.budget = budget;
    }

    public BankAccount getBankAccount() {
        return bankAccount;
    }

    public void setBankAccount(BankAccount bankAccount) {
        this.bankAccount = bankAccount;
    }

    public RecurringExpense getRecurringExpense() {
        return recurringExpense;
    }

    public void setRecurringExpense(RecurringExpense recurringExpense) {
        this.recurringExpense = recurringExpense;
    }
}
