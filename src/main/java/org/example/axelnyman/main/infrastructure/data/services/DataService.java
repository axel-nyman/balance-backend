package org.example.axelnyman.main.infrastructure.data.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetExpenseRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetIncomeRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetRepository;
import org.example.axelnyman.main.infrastructure.data.context.RecurringExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataService implements IDataService {

    private final BankAccountRepository bankAccountRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetIncomeRepository budgetIncomeRepository;
    private final BudgetExpenseRepository budgetExpenseRepository;

    public DataService(BankAccountRepository bankAccountRepository,
                      BalanceHistoryRepository balanceHistoryRepository,
                      RecurringExpenseRepository recurringExpenseRepository,
                      BudgetRepository budgetRepository,
                      BudgetIncomeRepository budgetIncomeRepository,
                      BudgetExpenseRepository budgetExpenseRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.budgetRepository = budgetRepository;
        this.budgetIncomeRepository = budgetIncomeRepository;
        this.budgetExpenseRepository = budgetExpenseRepository;
    }

    @Override
    public BankAccount saveBankAccount(BankAccount bankAccount) {
        return bankAccountRepository.save(bankAccount);
    }

    @Override
    public boolean existsByBankAccountName(String name) {
        return bankAccountRepository.existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsByNameExcludingId(String name, java.util.UUID excludeId) {
        return bankAccountRepository.existsByNameAndIdNotAndDeletedAtIsNull(name, excludeId);
    }

    @Override
    public java.util.List<BankAccount> getAllActiveBankAccounts() {
        return bankAccountRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public java.util.Optional<BankAccount> getBankAccountById(java.util.UUID id) {
        return bankAccountRepository.findById(id);
    }

    @Override
    public boolean isAccountLinkedToUnlockedBudget(java.util.UUID accountId) {
        return budgetIncomeRepository.existsByBankAccountIdAndBudget_Status(accountId, BudgetStatus.UNLOCKED) ||
               budgetExpenseRepository.existsByBankAccountIdAndBudget_Status(accountId, BudgetStatus.UNLOCKED);
    }

    @Override
    public void deleteBankAccount(java.util.UUID accountId) {
        BankAccount account = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        account.setDeletedAt(LocalDateTime.now());
        bankAccountRepository.save(account);
    }

    @Override
    public RecurringExpense saveRecurringExpense(RecurringExpense recurringExpense) {
        return recurringExpenseRepository.save(recurringExpense);
    }

    @Override
    public boolean existsByRecurringExpenseName(String name) {
        return recurringExpenseRepository.existsByNameAndDeletedAtIsNull(name);
    }

    @Override
    public boolean existsByRecurringExpenseNameExcludingId(String name, java.util.UUID excludeId) {
        return recurringExpenseRepository.existsByNameAndDeletedAtIsNullAndIdNot(name, excludeId);
    }

    @Override
    public java.util.Optional<RecurringExpense> getRecurringExpenseById(java.util.UUID id) {
        return recurringExpenseRepository.findById(id)
                .filter(expense -> expense.getDeletedAt() == null);
    }

    @Override
    public java.util.List<RecurringExpense> getAllActiveRecurringExpenses() {
        return recurringExpenseRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public void deleteRecurringExpense(java.util.UUID id) {
        RecurringExpense expense = recurringExpenseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Recurring expense not found"));
        expense.setDeletedAt(LocalDateTime.now());
        recurringExpenseRepository.save(expense);
    }

    @Override
    public BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory) {
        return balanceHistoryRepository.save(balanceHistory);
    }

    @Override
    public Budget saveBudget(Budget budget) {
        return budgetRepository.save(budget);
    }

    @Override
    public boolean existsByMonthAndYear(Integer month, Integer year) {
        return budgetRepository.existsByMonthAndYearAndDeletedAtIsNull(month, year);
    }

    @Override
    public boolean existsByStatus(BudgetStatus status) {
        return budgetRepository.existsByStatusAndDeletedAtIsNull(status);
    }

    @Override
    public java.util.List<Budget> getAllBudgetsSorted() {
        return budgetRepository.findAllByDeletedAtIsNullOrderByYearDescMonthDesc();
    }

    @Override
    public java.util.Optional<Budget> getBudgetById(java.util.UUID id) {
        return budgetRepository.findById(id)
                .filter(budget -> budget.getDeletedAt() == null);
    }

    @Override
    public BudgetIncome saveBudgetIncome(BudgetIncome budgetIncome) {
        return budgetIncomeRepository.save(budgetIncome);
    }

    @Override
    public java.util.Optional<BudgetIncome> getBudgetIncomeById(java.util.UUID id) {
        return budgetIncomeRepository.findById(id);
    }

    @Override
    public void deleteBudgetIncome(java.util.UUID id) {
        budgetIncomeRepository.deleteById(id);
    }

    @Override
    public BudgetExpense saveBudgetExpense(BudgetExpense budgetExpense) {
        return budgetExpenseRepository.save(budgetExpense);
    }
}
