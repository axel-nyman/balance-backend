package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.dtos.TodoDtos.*;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetExpenseExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetIncomeExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetSavingsExtensions;
import org.example.axelnyman.main.domain.extensions.RecurringExpenseExtensions;
import org.example.axelnyman.main.domain.extensions.TodoExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.domain.model.TodoItem;
import org.example.axelnyman.main.domain.model.TodoItemType;
import org.example.axelnyman.main.domain.model.TodoList;
import org.example.axelnyman.main.domain.model.TransferPlan;
import org.example.axelnyman.main.domain.utils.TransferCalculationUtils;
import org.example.axelnyman.main.shared.exceptions.AccountLinkedToBudgetException;
import org.example.axelnyman.main.shared.exceptions.BankAccountNotFoundException;
import org.example.axelnyman.main.shared.exceptions.BudgetAlreadyLockedException;
import org.example.axelnyman.main.shared.exceptions.BudgetLockedException;
import org.example.axelnyman.main.shared.exceptions.BudgetNotBalancedException;
import org.example.axelnyman.main.shared.exceptions.BudgetNotFoundException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBankAccountNameException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBudgetException;
import org.example.axelnyman.main.shared.exceptions.DuplicateRecurringExpenseException;
import org.example.axelnyman.main.shared.exceptions.FutureDateException;
import org.example.axelnyman.main.shared.exceptions.InvalidYearException;
import org.example.axelnyman.main.shared.exceptions.TodoListNotFoundException;
import org.example.axelnyman.main.shared.exceptions.UnlockedBudgetExistsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DomainService implements IDomainService {

    private final IDataService dataService;

    public DomainService(IDataService dataService) {
        this.dataService = dataService;
    }

    @Override
    @Transactional
    public BankAccountResponse createBankAccount(CreateBankAccountRequest request) {
        if (dataService.existsByBankAccountName(request.name())) {
            throw new DuplicateBankAccountNameException("Bank account name already exists");
        }

        BankAccount savedAccount = dataService.saveBankAccount(BankAccountExtensions.toEntity(request));

        dataService.saveBalanceHistory(new BalanceHistory(
                savedAccount.getId(),
                savedAccount.getCurrentBalance(),
                savedAccount.getCurrentBalance(),
                "Initial balance",
                BalanceHistorySource.MANUAL,
                null
        ));

        return BankAccountExtensions.toResponse(savedAccount);
    }

    @Override
    public BankAccountListResponse getAllBankAccounts() {
        List<BankAccount> accounts = dataService.getAllActiveBankAccounts();

        BigDecimal totalBalance = accounts.stream()
                .map(BankAccount::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BankAccountResponse> accountResponses = accounts.stream()
                .sorted(Comparator.comparing(BankAccount::getName))
                .map(BankAccountExtensions::toResponse)
                .toList();

        return new BankAccountListResponse(totalBalance, accounts.size(), accountResponses);
    }

    @Override
    @Transactional
    public BankAccountResponse updateBankAccountDetails(UUID id, UpdateBankAccountRequest request) {
        // Get bank account by ID
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

        // Check if account is soft-deleted
        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Cannot update deleted bank account");
        }

        // If name is changing, check uniqueness (excluding current account)
        if (!account.getName().equals(request.name())) {
            if (dataService.existsByNameExcludingId(request.name(), id)) {
                throw new DuplicateBankAccountNameException("Bank account name already exists");
            }
        }

        // Update fields (balance is NOT updated)
        account.setName(request.name());
        account.setDescription(request.description());

        // Save (updatedAt auto-updated by JPA auditing)
        BankAccount updatedAccount = dataService.saveBankAccount(account);

        return BankAccountExtensions.toResponse(updatedAccount);
    }

    @Override
    @Transactional
    public BalanceUpdateResponse updateBankAccountBalance(UUID id, UpdateBalanceRequest request) {
        // Validate date is not in the future
        if (request.date().isAfter(LocalDateTime.now())) {
            throw new FutureDateException("Date cannot be in the future");
        }

        // Get bank account by ID
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

        // Check if account is soft-deleted
        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Cannot update balance of deleted bank account");
        }

        // Store previous balance
        BigDecimal previousBalance = account.getCurrentBalance();

        // Calculate change amount (new - previous)
        BigDecimal changeAmount = request.newBalance().subtract(previousBalance);

        // Update account's current balance
        account.setCurrentBalance(request.newBalance());

        // Save updated account
        BankAccount updatedAccount = dataService.saveBankAccount(account);

        // Create balance history entry with MANUAL source
        BalanceHistory historyEntry = new BalanceHistory(
                updatedAccount.getId(),
                request.newBalance(),
                changeAmount,
                request.comment(),
                BalanceHistorySource.MANUAL,
                null  // budgetId is null for manual updates
        );

        // Override the changeDate with the request date instead of auto-generated
        historyEntry.setChangeDate(request.date());

        dataService.saveBalanceHistory(historyEntry);

        // Return response with previous and new balance info
        return new BalanceUpdateResponse(
                updatedAccount.getId(),
                updatedAccount.getName(),
                updatedAccount.getCurrentBalance(),
                previousBalance,
                changeAmount,
                request.date()
        );
    }

    @Override
    @Transactional
    public void deleteBankAccount(UUID id) {
        // Check if account exists and is not already deleted
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

        // Check if account is already soft-deleted
        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + id);
        }

        // Check if account is linked to an unlocked budget
        if (dataService.isAccountLinkedToUnlockedBudget(id)) {
            throw new AccountLinkedToBudgetException("Cannot delete account used in unlocked budget");
        }

        // Perform soft delete
        dataService.deleteBankAccount(id);
    }

    @Override
    @Transactional
    public RecurringExpenseResponse createRecurringExpense(CreateRecurringExpenseRequest request) {
        // Check name uniqueness
        if (dataService.existsByRecurringExpenseName(request.name())) {
            throw new DuplicateRecurringExpenseException("Recurring expense with this name already exists");
        }

        // Convert to entity (will throw IllegalArgumentException if invalid enum)
        RecurringExpense expense = RecurringExpenseExtensions.toEntity(request);

        // Save entity
        RecurringExpense savedExpense = dataService.saveRecurringExpense(expense);

        // Return DTO
        return RecurringExpenseExtensions.toResponse(savedExpense);
    }

    @Override
    @Transactional
    public RecurringExpenseResponse updateRecurringExpense(UUID id, UpdateRecurringExpenseRequest request) {
        // Get recurring expense by ID (will filter out soft-deleted)
        RecurringExpense expense = dataService.getRecurringExpenseById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.RecurringExpenseNotFoundException(
                        "Recurring expense not found with id: " + id));

        // If name is changing, check uniqueness (excluding current expense)
        if (!expense.getName().equals(request.name())) {
            if (dataService.existsByRecurringExpenseNameExcludingId(request.name(), id)) {
                throw new DuplicateRecurringExpenseException("Recurring expense with this name already exists");
            }
        }

        // Parse and validate recurrence interval (will throw IllegalArgumentException if invalid)
        org.example.axelnyman.main.domain.model.RecurrenceInterval interval =
                org.example.axelnyman.main.domain.model.RecurrenceInterval.valueOf(request.recurrenceInterval().toUpperCase());

        // Update fields (DO NOT update lastUsedDate - that's only updated when used in a budget)
        expense.setName(request.name());
        expense.setAmount(request.amount());
        expense.setRecurrenceInterval(interval);
        expense.setIsManual(request.isManual());

        // Save (updatedAt will be auto-updated by JPA auditing)
        RecurringExpense updatedExpense = dataService.saveRecurringExpense(expense);

        return RecurringExpenseExtensions.toResponse(updatedExpense);
    }

    @Override
    public RecurringExpenseListResponse getAllRecurringExpenses() {
        // Fetch all active recurring expenses
        List<RecurringExpense> expenses = dataService.getAllActiveRecurringExpenses();

        // Map to list item responses with due date calculation
        List<RecurringExpenseListItemResponse> expenseResponses = expenses.stream()
                .map(expense -> {
                    // Calculate next due date and isDue flag
                    LocalDateTime nextDueDate = calculateNextDueDate(expense);
                    Boolean isDue = calculateIsDue(expense.getLastUsedDate(), nextDueDate);

                    return RecurringExpenseExtensions.toListItemResponse(expense, nextDueDate, isDue);
                })
                .sorted(Comparator.comparing(RecurringExpenseListItemResponse::name))
                .toList();

        return new RecurringExpenseListResponse(expenseResponses);
    }

    @Override
    @Transactional
    public void deleteRecurringExpense(UUID id) {
        // Validate expense exists and is not already soft-deleted
        dataService.getRecurringExpenseById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.RecurringExpenseNotFoundException(
                        "Recurring expense not found with id: " + id));

        // Perform soft delete
        dataService.deleteRecurringExpense(id);
    }

    /**
     * Calculate the next due date for a recurring expense based on its recurrence interval
     * and last used date.
     *
     * @param expense The recurring expense
     * @return The next due date, or null if the expense has never been used
     */
    private LocalDateTime calculateNextDueDate(RecurringExpense expense) {
        LocalDateTime lastUsedDate = expense.getLastUsedDate();

        // If never used, next due date is null
        if (lastUsedDate == null) {
            return null;
        }

        // Calculate next due date based on interval
        return switch (expense.getRecurrenceInterval()) {
            case MONTHLY -> lastUsedDate.plusMonths(1);
            case QUARTERLY -> lastUsedDate.plusMonths(3);
            case BIANNUALLY -> lastUsedDate.plusMonths(6);
            case YEARLY -> lastUsedDate.plusYears(1);
        };
    }

    /**
     * Determine if a recurring expense is currently due.
     *
     * @param lastUsedDate The last used date
     * @param nextDueDate The calculated next due date
     * @return true if the expense is due, false otherwise
     */
    private Boolean calculateIsDue(LocalDateTime lastUsedDate, LocalDateTime nextDueDate) {
        // If never used, always due
        if (lastUsedDate == null) {
            return true;
        }

        // If next due date is null (shouldn't happen if lastUsedDate is not null), consider not due
        if (nextDueDate == null) {
            return false;
        }

        // Due if next due date is today or in the past
        return nextDueDate.isBefore(LocalDateTime.now()) || nextDueDate.isEqual(LocalDateTime.now());
    }

    @Override
    @Transactional
    public BudgetResponse createBudget(CreateBudgetRequest request) {
        // Validate year range (2000-2100)
        if (request.year() < 2000 || request.year() > 2100) {
            throw new InvalidYearException("Invalid year value. Must be between 2000 and 2100");
        }

        // Check for duplicate budget (same month/year) BEFORE unlocked check
        if (dataService.existsByMonthAndYear(request.month(), request.year())) {
            throw new DuplicateBudgetException("Budget already exists for this month");
        }

        // Check if another unlocked budget exists
        if (dataService.existsByStatus(BudgetStatus.UNLOCKED)) {
            throw new UnlockedBudgetExistsException("Another budget is currently unlocked. Lock or delete it before creating a new budget.");
        }

        // Create and save budget
        Budget budget = BudgetExtensions.toEntity(request);
        Budget savedBudget = dataService.saveBudget(budget);

        return BudgetExtensions.toResponse(savedBudget);
    }

    @Override
    public BudgetListResponse getAllBudgets() {
        List<Budget> budgets = dataService.getAllBudgetsSorted();

        List<BudgetResponse> budgetResponses = budgets.stream()
                .map(BudgetExtensions::toResponse)
                .toList();

        return new BudgetListResponse(budgetResponses);
    }

    @Override
    public BudgetDetailResponse getBudgetDetails(UUID id) {
        Budget budget = dataService.getBudgetById(id)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + id));

        List<BudgetIncome> incomeList = dataService.getBudgetIncomeByBudgetId(id);
        List<BudgetExpense> expensesList = dataService.getBudgetExpensesByBudgetId(id);
        List<BudgetSavings> savingsList = dataService.getBudgetSavingsByBudgetId(id);

        return BudgetExtensions.toDetailResponse(budget, incomeList, expensesList, savingsList);
    }

    @Override
    @Transactional
    public BudgetIncomeResponse addIncomeToBudget(UUID budgetId, CreateBudgetIncomeRequest request) {
        // Get budget and verify it exists and is not deleted
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        // Check if budget is unlocked
        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // Create and save budget income
        BudgetIncome budgetIncome = BudgetIncomeExtensions.toEntity(request, budgetId);
        BudgetIncome savedIncome = dataService.saveBudgetIncome(budgetIncome);

        return BudgetIncomeExtensions.toResponse(savedIncome, bankAccount);
    }

    @Override
    @Transactional
    public BudgetIncomeResponse updateBudgetIncome(UUID budgetId, UUID id, UpdateBudgetIncomeRequest request) {
        // Get budget income and verify it exists
        BudgetIncome income = dataService.getBudgetIncomeById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetIncomeNotFoundException(
                        "Budget income not found with id: " + id));

        // Verify income belongs to the specified budget
        if (!income.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetIncomeNotFoundException(
                    "Budget income not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // Update fields
        income.setName(request.name());
        income.setAmount(request.amount());
        income.setBankAccountId(request.bankAccountId());

        // Save (updatedAt will be auto-updated by JPA auditing)
        BudgetIncome updatedIncome = dataService.saveBudgetIncome(income);

        return BudgetIncomeExtensions.toResponse(updatedIncome, bankAccount);
    }

    @Override
    @Transactional
    public void deleteBudgetIncome(UUID budgetId, UUID id) {
        // Get budget income and verify it exists
        BudgetIncome income = dataService.getBudgetIncomeById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetIncomeNotFoundException(
                        "Budget income not found with id: " + id));

        // Verify income belongs to the specified budget
        if (!income.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetIncomeNotFoundException(
                    "Budget income not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Perform hard delete
        dataService.deleteBudgetIncome(id);
    }

    @Override
    @Transactional
    public BudgetExpenseResponse addExpenseToBudget(UUID budgetId, CreateBudgetExpenseRequest request) {
        // Get budget and verify it exists and is not deleted
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        // Check if budget is unlocked
        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // If recurring expense ID is provided, validate it exists
        if (request.recurringExpenseId() != null) {
            dataService.getRecurringExpenseById(request.recurringExpenseId())
                    .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.RecurringExpenseNotFoundException(
                            "Recurring expense not found with id: " + request.recurringExpenseId()));
        }

        // Create and save budget expense
        BudgetExpense budgetExpense = BudgetExpenseExtensions.toEntity(request, budgetId);
        BudgetExpense savedExpense = dataService.saveBudgetExpense(budgetExpense);

        return BudgetExpenseExtensions.toResponse(savedExpense, bankAccount);
    }

    @Override
    @Transactional
    public BudgetExpenseResponse updateBudgetExpense(UUID budgetId, UUID id, UpdateBudgetExpenseRequest request) {
        // Get budget expense and verify it exists
        BudgetExpense expense = dataService.getBudgetExpenseById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetExpenseNotFoundException(
                        "Budget expense not found with id: " + id));

        // Verify expense belongs to the specified budget
        if (!expense.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetExpenseNotFoundException(
                    "Budget expense not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // Update fields
        expense.setName(request.name());
        expense.setAmount(request.amount());
        expense.setBankAccountId(request.bankAccountId());
        expense.setDeductedAt(request.deductedAt());
        expense.setIsManual(request.isManual());

        // Save (updatedAt will be auto-updated by JPA auditing)
        BudgetExpense updatedExpense = dataService.saveBudgetExpense(expense);

        return BudgetExpenseExtensions.toResponse(updatedExpense, bankAccount);
    }

    @Override
    @Transactional
    public void deleteBudgetExpense(UUID budgetId, UUID id) {
        // Get budget expense and verify it exists
        BudgetExpense expense = dataService.getBudgetExpenseById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetExpenseNotFoundException(
                        "Budget expense not found with id: " + id));

        // Verify expense belongs to the specified budget
        if (!expense.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetExpenseNotFoundException(
                    "Budget expense not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Perform hard delete
        dataService.deleteBudgetExpense(id);
    }

    @Override
    @Transactional
    public BudgetSavingsResponse addSavingsToBudget(UUID budgetId, CreateBudgetSavingsRequest request) {
        // Get budget and verify it exists and is not deleted
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        // Check if budget is unlocked
        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // Create and save budget savings
        BudgetSavings budgetSavings = BudgetSavingsExtensions.toEntity(request, budgetId);
        BudgetSavings savedSavings = dataService.saveBudgetSavings(budgetSavings);

        return BudgetSavingsExtensions.toResponse(savedSavings, bankAccount);
    }

    @Override
    @Transactional
    public BudgetSavingsResponse updateBudgetSavings(UUID budgetId, UUID id, UpdateBudgetSavingsRequest request) {
        // Get budget savings and verify it exists
        BudgetSavings savings = dataService.getBudgetSavingsById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetSavingsNotFoundException(
                        "Budget savings not found with id: " + id));

        // Verify savings belongs to the specified budget
        if (!savings.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetSavingsNotFoundException(
                    "Budget savings not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Get bank account and verify it exists and is not deleted
        BankAccount bankAccount = dataService.getBankAccountById(request.bankAccountId())
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId()));

        if (bankAccount.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + request.bankAccountId());
        }

        // Update fields
        savings.setName(request.name());
        savings.setAmount(request.amount());
        savings.setBankAccountId(request.bankAccountId());

        // Save (updatedAt will be auto-updated by JPA auditing)
        BudgetSavings updatedSavings = dataService.saveBudgetSavings(savings);

        return BudgetSavingsExtensions.toResponse(updatedSavings, bankAccount);
    }

    @Override
    @Transactional
    public void deleteBudgetSavings(UUID budgetId, UUID id) {
        // Get budget savings and verify it exists
        BudgetSavings savings = dataService.getBudgetSavingsById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.BudgetSavingsNotFoundException(
                        "Budget savings not found with id: " + id));

        // Verify savings belongs to the specified budget
        if (!savings.getBudgetId().equals(budgetId)) {
            throw new org.example.axelnyman.main.shared.exceptions.BudgetSavingsNotFoundException(
                    "Budget savings not found with id: " + id);
        }

        // Get budget and verify it's unlocked
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));

        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetLockedException("Cannot modify items in locked budget");
        }

        // Perform hard delete
        dataService.deleteBudgetSavings(id);
    }

    @Override
    @Transactional
    public void deleteBudget(UUID id) {
        // Get budget and verify it exists
        Budget budget = dataService.getBudgetById(id)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found"));

        // Verify budget is unlocked
        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new org.example.axelnyman.main.shared.exceptions.CannotDeleteLockedBudgetException(
                    "Cannot delete locked budget. Unlock it first.");
        }

        // Delete all budget items (cascade delete)
        dataService.deleteBudgetIncomeByBudgetId(id);
        dataService.deleteBudgetExpensesByBudgetId(id);
        dataService.deleteBudgetSavingsByBudgetId(id);

        // Delete budget
        dataService.deleteBudget(id);
    }

    // Budget locking operations (Story 24)
    @Override
    @Transactional
    public BudgetResponse lockBudget(UUID budgetId) {
        // Fetch budget and verify it exists
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found"));

        // Check if already locked
        if (budget.getStatus() == BudgetStatus.LOCKED) {
            throw new BudgetAlreadyLockedException("Budget is already locked");
        }

        // Calculate total balance (income - expenses - savings)
        BigDecimal totalIncome = dataService.calculateTotalIncome(budgetId);
        BigDecimal totalExpenses = dataService.calculateTotalExpenses(budgetId);
        BigDecimal totalSavings = dataService.calculateTotalSavings(budgetId);
        BigDecimal balance = totalIncome.subtract(totalExpenses).subtract(totalSavings);

        // Verify balance equals zero
        if (balance.compareTo(BigDecimal.ZERO) != 0) {
            throw new BudgetNotBalancedException(
                    "Budget must have zero balance. Current balance: " + balance.toString());
        }

        // Set status to LOCKED and set lockedAt timestamp
        budget.setStatus(BudgetStatus.LOCKED);
        LocalDateTime lockedAt = LocalDateTime.now();
        budget.setLockedAt(lockedAt);

        // Save budget
        Budget savedBudget = dataService.saveBudget(budget);

        // Generate todo list for this budget (Story 25)
        generateTodoList(budgetId);

        // Update account balances based on savings (Story 26)
        updateBalancesForBudget(budgetId, savedBudget);

        // Update recurring expenses for this budget
        updateRecurringExpensesForBudget(budgetId, lockedAt);

        // Return DTO
        return BudgetExtensions.toResponse(savedBudget);
    }

    private void updateRecurringExpensesForBudget(UUID budgetId, LocalDateTime lockedAt) {
        // Get all budget expenses for this budget
        List<BudgetExpense> budgetExpenses = dataService.getBudgetExpensesByBudgetId(budgetId);

        // Filter to only those with non-null recurringExpenseId
        List<UUID> recurringExpenseIds = budgetExpenses.stream()
                .filter(expense -> expense.getRecurringExpenseId() != null)
                .map(BudgetExpense::getRecurringExpenseId)
                .distinct()
                .toList();

        // For each unique recurring expense, update lastUsedDate and lastUsedBudgetId
        for (UUID recurringExpenseId : recurringExpenseIds) {
            RecurringExpense recurringExpense = dataService.getRecurringExpenseById(recurringExpenseId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Recurring expense not found with id: " + recurringExpenseId));

            recurringExpense.setLastUsedDate(lockedAt);
            recurringExpense.setLastUsedBudgetId(budgetId);
            dataService.saveRecurringExpense(recurringExpense);
        }
    }

    // Story 26: Update account balances on budget lock
    private void updateBalancesForBudget(UUID budgetId, Budget budget) {
        // Get all savings for this budget
        List<BudgetSavings> allSavings = dataService.getBudgetSavingsByBudgetId(budgetId);

        // Group savings by bankAccountId and sum amounts
        Map<UUID, BigDecimal> savingsByAccount = allSavings.stream()
                .collect(Collectors.groupingBy(
                        BudgetSavings::getBankAccountId,
                        Collectors.reducing(
                                BigDecimal.ZERO,
                                BudgetSavings::getAmount,
                                BigDecimal::add
                        )
                ));

        // Update each account that has savings
        for (Map.Entry<UUID, BigDecimal> entry : savingsByAccount.entrySet()) {
            UUID accountId = entry.getKey();
            BigDecimal totalSavings = entry.getValue();

            // Load bank account
            BankAccount account = dataService.getBankAccountById(accountId)
                    .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + accountId));

            // Calculate new balance
            BigDecimal oldBalance = account.getCurrentBalance();
            BigDecimal newBalance = oldBalance.add(totalSavings);

            // Update account balance
            account.setCurrentBalance(newBalance);
            dataService.saveBankAccount(account);

            // Create BalanceHistory entry
            String comment = "Budget lock for " + budget.getMonth() + "/" + budget.getYear();
            BalanceHistory history = new BalanceHistory(
                    accountId,
                    newBalance,
                    totalSavings,
                    comment,
                    BalanceHistorySource.AUTOMATIC,
                    budgetId
            );
            dataService.saveBalanceHistory(history);
        }
    }

    // Todo List operations (Story 25)
    @Override
    @Transactional
    public void generateTodoList(UUID budgetId) {
        // Delete existing todo list if it exists (for relock scenario)
        dataService.deleteTodoListByBudgetId(budgetId);

        // Create new TodoList
        TodoList todoList = new TodoList(budgetId);
        TodoList savedTodoList = dataService.saveTodoList(todoList);

        // Load budget data
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found with id: " + budgetId));
        List<BudgetIncome> income = dataService.getBudgetIncomeByBudgetId(budgetId);
        List<BudgetExpense> expenses = dataService.getBudgetExpensesByBudgetId(budgetId);
        List<BudgetSavings> savings = dataService.getBudgetSavingsByBudgetId(budgetId);

        // Calculate transfers using TransferCalculationUtils
        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(budget, income, expenses, savings);

        // Create TRANSFER todo items
        for (TransferPlan transfer : transfers) {
            BankAccount fromAccount = dataService.getBankAccountById(transfer.getFromAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException("Account not found: " + transfer.getFromAccountId()));
            BankAccount toAccount = dataService.getBankAccountById(transfer.getToAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException("Account not found: " + transfer.getToAccountId()));

            String itemName = "Transfer " + transfer.getAmount() + " from " + fromAccount.getName() +
                              " to " + toAccount.getName();

            TodoItem transferItem = new TodoItem(
                    savedTodoList.getId(),
                    itemName,
                    TodoItemType.TRANSFER,
                    transfer.getAmount(),
                    transfer.getFromAccountId(),
                    transfer.getToAccountId()
            );
            dataService.saveTodoItem(transferItem);
        }

        // Create PAYMENT todo items for manual expenses
        List<BudgetExpense> manualExpenses = expenses.stream()
                .filter(expense -> Boolean.TRUE.equals(expense.getIsManual()))
                .toList();

        for (BudgetExpense expense : manualExpenses) {
            BankAccount account = dataService.getBankAccountById(expense.getBankAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException("Account not found: " + expense.getBankAccountId()));

            String itemName = "Pay " + expense.getName() + " (" + expense.getAmount() + ") from " + account.getName();

            TodoItem paymentItem = new TodoItem(
                    savedTodoList.getId(),
                    itemName,
                    TodoItemType.PAYMENT,
                    expense.getAmount(),
                    expense.getBankAccountId()
            );
            dataService.saveTodoItem(paymentItem);
        }
    }

    @Override
    public TodoListResponse getTodoList(UUID budgetId) {
        // Fetch todo list by budget ID
        TodoList todoList = dataService.getTodoListByBudgetId(budgetId)
                .orElseThrow(() -> new TodoListNotFoundException("Todo list not found for this budget"));

        // Fetch all items for this todo list
        List<TodoItem> items = dataService.getTodoItemsByTodoListId(todoList.getId());

        // Map items to DTOs with account details
        List<TodoItemResponse> itemResponses = items.stream()
                .map(item -> {
                    BankAccount fromAccount = item.getFromAccountId() != null
                            ? dataService.getBankAccountById(item.getFromAccountId()).orElse(null)
                            : null;
                    BankAccount toAccount = item.getToAccountId() != null
                            ? dataService.getBankAccountById(item.getToAccountId()).orElse(null)
                            : null;
                    return TodoExtensions.toItemResponse(item, fromAccount, toAccount);
                })
                .toList();

        // Return mapped response with summary
        return TodoExtensions.toResponse(todoList, itemResponses);
    }
}
