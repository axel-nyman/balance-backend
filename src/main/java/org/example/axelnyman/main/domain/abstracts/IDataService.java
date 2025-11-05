package org.example.axelnyman.main.domain.abstracts;

import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.example.axelnyman.main.domain.model.RecurringExpense;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Access Service - Responsible for direct database operations
 * This service provides a clean abstraction over repository operations
 * and should not contain business logic.
 */
public interface IDataService {
    // Bank Account operations
    BankAccount saveBankAccount(BankAccount bankAccount);

    boolean existsByBankAccountName(String name);

    boolean existsByNameExcludingId(String name, UUID excludeId);

    List<BankAccount> getAllActiveBankAccounts();

    Optional<BankAccount> getBankAccountById(UUID id);

    boolean isAccountLinkedToUnlockedBudget(UUID accountId);

    void deleteBankAccount(UUID accountId);

    // Recurring Expense operations
    RecurringExpense saveRecurringExpense(RecurringExpense recurringExpense);

    boolean existsByRecurringExpenseName(String name);

    boolean existsByRecurringExpenseNameExcludingId(String name, UUID excludeId);

    Optional<RecurringExpense> getRecurringExpenseById(UUID id);

    List<RecurringExpense> getAllActiveRecurringExpenses();

    void deleteRecurringExpense(UUID id);

    // Balance History operations
    BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory);

    // Budget operations
    Budget saveBudget(Budget budget);

    boolean existsByMonthAndYear(Integer month, Integer year);

    boolean existsByStatus(BudgetStatus status);

    List<Budget> getAllBudgetsSorted();

    Optional<Budget> getBudgetById(UUID id);

    // Budget Income operations
    BudgetIncome saveBudgetIncome(BudgetIncome budgetIncome);

    Optional<BudgetIncome> getBudgetIncomeById(UUID id);

    void deleteBudgetIncome(UUID id);

    // Budget Expense operations
    BudgetExpense saveBudgetExpense(BudgetExpense budgetExpense);

    Optional<BudgetExpense> getBudgetExpenseById(UUID id);

    void deleteBudgetExpense(UUID id);

    // Budget Savings operations
    BudgetSavings saveBudgetSavings(BudgetSavings budgetSavings);

    Optional<BudgetSavings> getBudgetSavingsById(UUID id);

    void deleteBudgetSavings(UUID id);

    // Budget Detail View operations (Story 21)
    List<BudgetIncome> getBudgetIncomeByBudgetId(UUID budgetId);

    List<BudgetExpense> getBudgetExpensesByBudgetId(UUID budgetId);

    List<BudgetSavings> getBudgetSavingsByBudgetId(UUID budgetId);

    // Budget Delete operations (Story 22)
    void deleteBudget(UUID id);

    void deleteBudgetIncomeByBudgetId(UUID budgetId);

    void deleteBudgetExpensesByBudgetId(UUID budgetId);

    void deleteBudgetSavingsByBudgetId(UUID budgetId);
}
