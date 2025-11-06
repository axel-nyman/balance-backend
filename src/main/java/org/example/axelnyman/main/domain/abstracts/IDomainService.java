package org.example.axelnyman.main.domain.abstracts;

import org.example.axelnyman.main.domain.dtos.BalanceHistoryDtos.*;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.dtos.TodoDtos.*;

import java.util.UUID;

/**
 * Domain Service - Responsible for general business operations
 * This service handles CRUD operations, data transformations, and business
 * rules
 * that apply across the application domain.
 */
public interface IDomainService {

    // Bank Account operations
    BankAccountResponse createBankAccount(CreateBankAccountRequest request);

    BankAccountListResponse getAllBankAccounts();

    BankAccountResponse updateBankAccountDetails(UUID id, UpdateBankAccountRequest request);

    BalanceUpdateResponse updateBankAccountBalance(UUID id, UpdateBalanceRequest request);

    void deleteBankAccount(UUID id);

    BalanceHistoryPageResponse getBalanceHistory(UUID bankAccountId, int page, int size);

    // Recurring Expense operations
    RecurringExpenseResponse createRecurringExpense(CreateRecurringExpenseRequest request);

    RecurringExpenseResponse getRecurringExpenseById(UUID id);

    RecurringExpenseResponse updateRecurringExpense(UUID id, UpdateRecurringExpenseRequest request);

    RecurringExpenseListResponse getAllRecurringExpenses();

    void deleteRecurringExpense(UUID id);

    // Budget operations
    BudgetResponse createBudget(CreateBudgetRequest request);

    BudgetListResponse getAllBudgets();

    BudgetDetailResponse getBudgetDetails(UUID id);

    BudgetIncomeResponse addIncomeToBudget(UUID budgetId, CreateBudgetIncomeRequest request);

    BudgetIncomeResponse updateBudgetIncome(UUID budgetId, UUID id, UpdateBudgetIncomeRequest request);

    void deleteBudgetIncome(UUID budgetId, UUID id);

    BudgetExpenseResponse addExpenseToBudget(UUID budgetId, CreateBudgetExpenseRequest request);

    BudgetExpenseResponse updateBudgetExpense(UUID budgetId, UUID id, UpdateBudgetExpenseRequest request);

    void deleteBudgetExpense(UUID budgetId, UUID id);

    BudgetSavingsResponse addSavingsToBudget(UUID budgetId, CreateBudgetSavingsRequest request);

    BudgetSavingsResponse updateBudgetSavings(UUID budgetId, UUID id, UpdateBudgetSavingsRequest request);

    void deleteBudgetSavings(UUID budgetId, UUID id);

    void deleteBudget(UUID id);

    // Budget locking operations (Story 24)
    BudgetResponse lockBudget(UUID budgetId);

    // Budget unlocking operations (Story 27)
    BudgetResponse unlockBudget(UUID budgetId);

    // Todo List operations (Story 25)
    void generateTodoList(UUID budgetId);

    TodoListResponse getTodoList(UUID budgetId);

    // Todo Item operations (Story 28)
    TodoItemResponse updateTodoItemStatus(UUID budgetId, UUID todoItemId, UpdateTodoItemRequest request);
}
