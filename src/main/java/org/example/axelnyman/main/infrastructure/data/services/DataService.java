package org.example.axelnyman.main.infrastructure.data.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.domain.model.TodoItem;
import org.example.axelnyman.main.domain.model.TodoList;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetExpenseRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetIncomeRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetSavingsRepository;
import org.example.axelnyman.main.infrastructure.data.context.RecurringExpenseRepository;
import org.example.axelnyman.main.infrastructure.data.context.TodoItemRepository;
import org.example.axelnyman.main.infrastructure.data.context.TodoListRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final BudgetSavingsRepository budgetSavingsRepository;
    private final TodoListRepository todoListRepository;
    private final TodoItemRepository todoItemRepository;

    public DataService(BankAccountRepository bankAccountRepository,
                      BalanceHistoryRepository balanceHistoryRepository,
                      RecurringExpenseRepository recurringExpenseRepository,
                      BudgetRepository budgetRepository,
                      BudgetIncomeRepository budgetIncomeRepository,
                      BudgetExpenseRepository budgetExpenseRepository,
                      BudgetSavingsRepository budgetSavingsRepository,
                      TodoListRepository todoListRepository,
                      TodoItemRepository todoItemRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
        this.recurringExpenseRepository = recurringExpenseRepository;
        this.budgetRepository = budgetRepository;
        this.budgetIncomeRepository = budgetIncomeRepository;
        this.budgetExpenseRepository = budgetExpenseRepository;
        this.budgetSavingsRepository = budgetSavingsRepository;
        this.todoListRepository = todoListRepository;
        this.todoItemRepository = todoItemRepository;
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
               budgetExpenseRepository.existsByBankAccountIdAndBudget_Status(accountId, BudgetStatus.UNLOCKED) ||
               budgetSavingsRepository.existsByBankAccountIdAndBudget_Status(accountId, BudgetStatus.UNLOCKED);
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
    public Page<BalanceHistory> getBalanceHistoryByBankAccountId(java.util.UUID bankAccountId, Pageable pageable) {
        return balanceHistoryRepository.findAllByBankAccountIdOrderByChangeDateDescCreatedAtDesc(bankAccountId, pageable);
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

    @Override
    public java.util.Optional<BudgetExpense> getBudgetExpenseById(java.util.UUID id) {
        return budgetExpenseRepository.findById(id);
    }

    @Override
    public void deleteBudgetExpense(java.util.UUID id) {
        budgetExpenseRepository.deleteById(id);
    }

    @Override
    public BudgetSavings saveBudgetSavings(BudgetSavings budgetSavings) {
        return budgetSavingsRepository.save(budgetSavings);
    }

    @Override
    public java.util.Optional<BudgetSavings> getBudgetSavingsById(java.util.UUID id) {
        return budgetSavingsRepository.findById(id);
    }

    @Override
    public void deleteBudgetSavings(java.util.UUID id) {
        budgetSavingsRepository.deleteById(id);
    }

    // Budget Detail View operations (Story 21)
    @Override
    public java.util.List<BudgetIncome> getBudgetIncomeByBudgetId(java.util.UUID budgetId) {
        return budgetIncomeRepository.findAllByBudgetIdWithBankAccount(budgetId);
    }

    @Override
    public java.util.List<BudgetExpense> getBudgetExpensesByBudgetId(java.util.UUID budgetId) {
        return budgetExpenseRepository.findAllByBudgetIdWithBankAccount(budgetId);
    }

    @Override
    public java.util.List<BudgetSavings> getBudgetSavingsByBudgetId(java.util.UUID budgetId) {
        return budgetSavingsRepository.findAllByBudgetIdWithBankAccount(budgetId);
    }

    // Budget Lock operations (Story 24) - Calculation methods
    @Override
    public java.math.BigDecimal calculateTotalIncome(java.util.UUID budgetId) {
        return budgetIncomeRepository.sumAmountByBudgetId(budgetId);
    }

    @Override
    public java.math.BigDecimal calculateTotalExpenses(java.util.UUID budgetId) {
        return budgetExpenseRepository.sumAmountByBudgetId(budgetId);
    }

    @Override
    public java.math.BigDecimal calculateTotalSavings(java.util.UUID budgetId) {
        return budgetSavingsRepository.sumAmountByBudgetId(budgetId);
    }

    // Budget Delete operations (Story 22)
    @Override
    public void deleteBudget(java.util.UUID id) {
        budgetRepository.deleteById(id);
    }

    @Override
    public void deleteBudgetIncomeByBudgetId(java.util.UUID budgetId) {
        budgetIncomeRepository.deleteByBudgetId(budgetId);
    }

    @Override
    public void deleteBudgetExpensesByBudgetId(java.util.UUID budgetId) {
        budgetExpenseRepository.deleteByBudgetId(budgetId);
    }

    @Override
    public void deleteBudgetSavingsByBudgetId(java.util.UUID budgetId) {
        budgetSavingsRepository.deleteByBudgetId(budgetId);
    }

    // Todo List operations (Story 25)
    @Override
    public TodoList saveTodoList(TodoList todoList) {
        return todoListRepository.save(todoList);
    }

    @Override
    public TodoItem saveTodoItem(TodoItem todoItem) {
        return todoItemRepository.save(todoItem);
    }

    @Override
    public java.util.Optional<TodoList> getTodoListByBudgetId(java.util.UUID budgetId) {
        return todoListRepository.findByBudgetId(budgetId);
    }

    @Override
    public java.util.List<TodoItem> getTodoItemsByTodoListId(java.util.UUID todoListId) {
        return todoItemRepository.findAllByTodoListId(todoListId);
    }

    @Override
    public java.util.Optional<TodoItem> getTodoItemById(java.util.UUID id) {
        return todoItemRepository.findById(id);
    }

    @Override
    public void deleteTodoListByBudgetId(java.util.UUID budgetId) {
        // Find the todo list first
        todoListRepository.findByBudgetId(budgetId).ifPresent(todoList -> {
            // Delete all todo items for this todo list
            java.util.List<TodoItem> todoItems = todoItemRepository.findAllByTodoListId(todoList.getId());
            todoItemRepository.deleteAll(todoItems);
            // Then delete the todo list
            todoListRepository.delete(todoList);
        });
    }

    @Override
    public void deleteTodoList(java.util.UUID todoListId) {
        todoListRepository.deleteById(todoListId);
    }

    // Budget Unlock operations (Story 27)
    @Override
    public java.util.Optional<Budget> getMostRecentBudget() {
        return budgetRepository.findFirstByDeletedAtIsNullOrderByYearDescMonthDesc();
    }

    @Override
    public java.util.List<BalanceHistory> getBalanceHistoryByBudgetIdAndSource(
            java.util.UUID budgetId,
            org.example.axelnyman.main.domain.model.BalanceHistorySource source) {
        return balanceHistoryRepository.findAllByBudgetIdAndSource(budgetId, source);
    }

    @Override
    public void deleteBalanceHistoryByBudgetId(java.util.UUID budgetId) {
        balanceHistoryRepository.deleteAllByBudgetId(budgetId);
    }

    @Override
    public java.util.List<Budget> findLockedBudgetsUsingRecurringExpense(
            java.util.UUID recurringExpenseId,
            java.util.UUID excludeBudgetId) {
        return budgetRepository.findLockedBudgetsUsingRecurringExpense(recurringExpenseId, excludeBudgetId);
    }
}
