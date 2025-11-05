package org.example.axelnyman.main.domain.abstracts;

import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;

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

    // Recurring Expense operations
    RecurringExpenseResponse createRecurringExpense(CreateRecurringExpenseRequest request);

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
}
