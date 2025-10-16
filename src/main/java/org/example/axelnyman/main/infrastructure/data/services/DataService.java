package org.example.axelnyman.main.infrastructure.data.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.RecurringExpenseRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DataService implements IDataService {

    private final BankAccountRepository bankAccountRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;

    public DataService(BankAccountRepository bankAccountRepository,
                      BalanceHistoryRepository balanceHistoryRepository,
                      RecurringExpenseRepository recurringExpenseRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.recurringExpenseRepository = recurringExpenseRepository;
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
        // TODO: Update this method when Budget entity is created in Sprint 2
        // For now, return false since budgets don't exist yet
        return false;
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
}
