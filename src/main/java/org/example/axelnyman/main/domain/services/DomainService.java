package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BalanceHistoryDtos.*;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.BudgetDtos.*;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.*;
import org.example.axelnyman.main.domain.dtos.TodoDtos.*;
import org.example.axelnyman.main.domain.extensions.BalanceHistoryExtensions;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetExpenseExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetIncomeExtensions;
import org.example.axelnyman.main.domain.extensions.BudgetSavingsExtensions;
import org.example.axelnyman.main.domain.extensions.RecurringExpenseExtensions;
import org.example.axelnyman.main.domain.extensions.SavingsGoalExtensions;
import org.example.axelnyman.main.domain.extensions.TodoExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.GoalAllocation;
import org.example.axelnyman.main.domain.model.GoalAllocationChange;
import org.example.axelnyman.main.domain.model.GoalAllocationChangeSource;
import org.example.axelnyman.main.domain.model.GoalStatus;
import org.example.axelnyman.main.domain.model.SavingsGoal;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.BudgetStatus;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.domain.model.TodoItem;
import org.example.axelnyman.main.domain.model.TodoItemStatus;
import org.example.axelnyman.main.domain.model.TodoItemType;
import org.example.axelnyman.main.domain.model.TodoList;
import org.example.axelnyman.main.domain.model.TransferPlan;
import org.example.axelnyman.main.domain.utils.TransferCalculationUtils;
import org.example.axelnyman.main.shared.exceptions.AccountLinkedToBudgetException;
import org.example.axelnyman.main.shared.exceptions.AllocationReallocationRequiredException;
import org.example.axelnyman.main.shared.exceptions.BackdatedBalanceUpdateException;
import org.example.axelnyman.main.shared.exceptions.BankAccountNotFoundException;
import org.example.axelnyman.main.shared.exceptions.BudgetAlreadyLockedException;
import org.example.axelnyman.main.shared.exceptions.BudgetLockedException;
import org.example.axelnyman.main.shared.exceptions.DateBeforeAccountCreationException;
import org.example.axelnyman.main.shared.exceptions.BudgetNotBalancedException;
import org.example.axelnyman.main.shared.exceptions.BudgetNotLockedException;
import org.example.axelnyman.main.shared.exceptions.BudgetNotFoundException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBankAccountNameException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBudgetException;
import org.example.axelnyman.main.shared.exceptions.DuplicateRecurringExpenseException;
import org.example.axelnyman.main.shared.exceptions.FutureDateException;
import org.example.axelnyman.main.shared.exceptions.InvalidYearException;
import org.example.axelnyman.main.shared.exceptions.InsufficientUnallocatedFundsException;
import org.example.axelnyman.main.shared.exceptions.NotMostRecentBudgetException;
import org.example.axelnyman.main.shared.exceptions.SavingsGoalArchivedException;
import org.example.axelnyman.main.shared.exceptions.SavingsGoalNotFoundException;
import org.example.axelnyman.main.shared.exceptions.TodoItemNotFoundException;
import org.example.axelnyman.main.shared.exceptions.TodoListNotFoundException;
import org.example.axelnyman.main.shared.exceptions.UnlockedBudgetExistsException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DomainService implements IDomainService {

    private record DueDate(int month, int year) {}

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
                null,
                LocalDate.now()
        ));

        return BankAccountExtensions.toResponse(savedAccount);
    }

    @Override
    public BankAccountListResponse getAllBankAccounts() {
        List<BankAccount> accounts = dataService.getAllActiveBankAccounts();

        BigDecimal totalBalance = accounts.stream()
                .map(BankAccount::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<UUID, BigDecimal> allocatedByAccount = dataService.sumAllocationsGroupedByBankAccount().stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));

        List<BankAccountResponse> accountResponses = accounts.stream()
                .sorted(Comparator.comparing(BankAccount::getName))
                .map(account -> BankAccountExtensions.toResponse(
                        account, allocatedByAccount.getOrDefault(account.getId(), BigDecimal.ZERO)))
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

        return BankAccountExtensions.toResponse(updatedAccount,
                dataService.sumAllocationsByBankAccountId(id));
    }

    @Override
    @Transactional
    public BalanceUpdateResponse updateBankAccountBalance(UUID id, UpdateBalanceRequest request) {
        // Validate date is not in the future
        if (request.date().isAfter(LocalDate.now())) {
            throw new FutureDateException("Date cannot be in the future");
        }

        // Get bank account by ID
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

        // Check if account is soft-deleted
        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Cannot update balance of deleted bank account");
        }

        // Validate date is not before account creation
        if (request.date().isBefore(account.getCreatedAt().toLocalDate())) {
            throw new DateBeforeAccountCreationException("Date cannot be before the account was created");
        }

        // Validate date is not before the most recent balance history entry
        Optional<LocalDate> mostRecentDate = dataService.getMostRecentBalanceHistoryDate(id);
        if (mostRecentDate.isPresent() && request.date().isBefore(mostRecentDate.get())) {
            throw new BackdatedBalanceUpdateException(
                "Date cannot be before the most recent balance history entry (" + mostRecentDate.get() + ")"
            );
        }

        BigDecimal previousBalance = account.getCurrentBalance();
        BigDecimal changeAmount = request.newBalance().subtract(previousBalance);

        // Reconcile the change against any savings-goal earmarks on this account
        // (item 070d). All allocation writes and the balance write happen in this
        // single transaction, so a rejected reallocation leaves nothing changed.
        List<GoalAllocation> allocations = dataService.getGoalAllocationsByBankAccountId(id);
        List<AllocationAdjustment> adjustments;

        if (changeAmount.signum() >= 0) {
            // Increase (or no change): raise the balance first, then earmark some/all
            // of the increase toward goals if requested — validated against the
            // already-raised balance so the invariant (allocations <= balance) holds.
            persistBalanceChange(account, request, changeAmount);
            adjustments = hasReallocation(request)
                    ? applyIncreaseReallocation(account, allocations, changeAmount, request.reallocation())
                    : List.of();
        } else {
            // Decrease: reconcile allocations before lowering the balance, so each
            // reduction is checked against the still-valid higher balance and the
            // account is never transiently over-allocated.
            adjustments = reconcileDeficit(account, allocations, request);
            persistBalanceChange(account, request, changeAmount);
        }

        return new BalanceUpdateResponse(
                account.getId(),
                account.getName(),
                account.getCurrentBalance(),
                previousBalance,
                changeAmount,
                request.date(),
                adjustments
        );
    }

    private boolean hasReallocation(UpdateBalanceRequest request) {
        return request.reallocation() != null && !request.reallocation().isEmpty();
    }

    private void persistBalanceChange(BankAccount account, UpdateBalanceRequest request, BigDecimal changeAmount) {
        account.setCurrentBalance(request.newBalance());
        dataService.saveBankAccount(account);
        dataService.saveBalanceHistory(new BalanceHistory(
                account.getId(), request.newBalance(), changeAmount,
                request.comment(), BalanceHistorySource.MANUAL, null, request.date()));
    }

    /**
     * Decrease path: when the new balance no longer covers the account's total
     * earmarks, bring allocations back under the balance. Within slack does
     * nothing; a single backing goal is auto-reduced by the deficit; two or more
     * goals require an explicit split (a 409 conflict otherwise). A supplied split
     * must use non-positive amounts summing exactly to the deficit, leaving no
     * allocation below zero.
     */
    private List<AllocationAdjustment> reconcileDeficit(BankAccount account,
            List<GoalAllocation> allocations, UpdateBalanceRequest request) {
        if (allocations.isEmpty()) {
            if (hasReallocation(request)) {
                throw new IllegalArgumentException("Reallocation references goals that do not back this account");
            }
            return List.of();
        }

        BigDecimal totalAllocated = allocations.stream()
                .map(GoalAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deficit = totalAllocated.subtract(request.newBalance());

        if (deficit.signum() <= 0) {
            if (hasReallocation(request)) {
                throw new IllegalArgumentException("No goal reallocation is required for this balance change");
            }
            return List.of();
        }

        if (hasReallocation(request)) {
            return applyDeficitSplit(account, allocations, deficit, request.reallocation());
        }

        if (allocations.size() == 1) {
            // Cap the reduction at the allocation amount so it never goes below zero
            // (a negative new balance would otherwise produce a deficit > allocation).
            GoalAllocation only = allocations.get(0);
            return List.of(reduceAllocation(account, only, deficit.min(only.getAmount())));
        }

        throw new AllocationReallocationRequiredException(
                buildConflict(account, request.newBalance(), totalAllocated, deficit, allocations));
    }

    private List<AllocationAdjustment> applyDeficitSplit(BankAccount account, List<GoalAllocation> allocations,
            BigDecimal deficit, List<ReallocationEntry> entries) {
        requireNoDuplicates(entries);
        Map<UUID, GoalAllocation> byGoal = allocations.stream()
                .collect(Collectors.toMap(GoalAllocation::getSavingsGoalId, a -> a));

        BigDecimal totalReduction = BigDecimal.ZERO;
        for (ReallocationEntry entry : entries) {
            if (entry.changeBy().signum() > 0) {
                throw new IllegalArgumentException(
                        "Reallocation for a balance decrease must use non-positive amounts");
            }
            GoalAllocation allocation = byGoal.get(entry.savingsGoalId());
            if (allocation == null) {
                throw new IllegalArgumentException(
                        "Reallocation references a goal that does not back this account: " + entry.savingsGoalId());
            }
            if (allocation.getAmount().add(entry.changeBy()).signum() < 0) {
                throw new IllegalArgumentException("Reallocation would drive a goal allocation below zero");
            }
            totalReduction = totalReduction.add(entry.changeBy().negate());
        }
        if (totalReduction.compareTo(deficit) != 0) {
            throw new IllegalArgumentException(
                    "Reallocation reductions must sum to the required reduction of " + deficit.toPlainString());
        }

        List<AllocationAdjustment> adjustments = new ArrayList<>();
        for (ReallocationEntry entry : entries) {
            if (entry.changeBy().signum() == 0) {
                continue;
            }
            adjustments.add(reduceAllocation(account, byGoal.get(entry.savingsGoalId()), entry.changeBy().negate()));
        }
        return adjustments;
    }

    /**
     * Increase path: earmark requested amounts toward goals. Additions must be
     * non-negative and sum to no more than the increase; each named goal must be
     * active. {@link #applyAllocation} enforces the per-account invariant against
     * the already-raised balance.
     */
    private List<AllocationAdjustment> applyIncreaseReallocation(BankAccount account, List<GoalAllocation> allocations,
            BigDecimal increase, List<ReallocationEntry> entries) {
        requireNoDuplicates(entries);
        Map<UUID, BigDecimal> currentByGoal = allocations.stream()
                .collect(Collectors.toMap(GoalAllocation::getSavingsGoalId, GoalAllocation::getAmount));

        BigDecimal totalAddition = BigDecimal.ZERO;
        for (ReallocationEntry entry : entries) {
            if (entry.changeBy().signum() < 0) {
                throw new IllegalArgumentException(
                        "Reallocation for a balance increase must use non-negative amounts");
            }
            totalAddition = totalAddition.add(entry.changeBy());
        }
        if (totalAddition.compareTo(increase) > 0) {
            throw new IllegalArgumentException(
                    "Reallocation additions may not exceed the balance increase of " + increase.toPlainString());
        }

        List<AllocationAdjustment> adjustments = new ArrayList<>();
        for (ReallocationEntry entry : entries) {
            if (entry.changeBy().signum() == 0) {
                continue;
            }
            SavingsGoal goal = requireActiveGoal(entry.savingsGoalId());
            BigDecimal resulting = currentByGoal.getOrDefault(goal.getId(), BigDecimal.ZERO).add(entry.changeBy());
            applyAllocation(goal.getId(), account, resulting, GoalAllocationChangeSource.BALANCE_REALLOCATION);
            adjustments.add(new AllocationAdjustment(goal.getId(), goal.getName(), entry.changeBy(), resulting));
        }
        return adjustments;
    }

    private AllocationAdjustment reduceAllocation(BankAccount account, GoalAllocation allocation, BigDecimal reduceBy) {
        BigDecimal resulting = allocation.getAmount().subtract(reduceBy);
        applyAllocation(allocation.getSavingsGoalId(), account, resulting,
                GoalAllocationChangeSource.BALANCE_REALLOCATION);
        return new AllocationAdjustment(allocation.getSavingsGoalId(), goalName(allocation.getSavingsGoalId()),
                reduceBy.negate(), resulting);
    }

    private ReallocationConflictResponse buildConflict(BankAccount account, BigDecimal newBalance,
            BigDecimal totalAllocated, BigDecimal deficit, List<GoalAllocation> allocations) {
        List<ReallocationConflictGoal> goals = allocations.stream()
                .map(a -> new ReallocationConflictGoal(
                        a.getSavingsGoalId(), goalName(a.getSavingsGoalId()), a.getAmount()))
                .toList();
        return new ReallocationConflictResponse(
                "Balance decrease leaves the account over-allocated across multiple goals; a split is required",
                account.getId(), account.getName(), newBalance, totalAllocated, deficit, goals);
    }

    private void requireNoDuplicates(List<ReallocationEntry> entries) {
        long distinct = entries.stream().map(ReallocationEntry::savingsGoalId).distinct().count();
        if (distinct != entries.size()) {
            throw new IllegalArgumentException("Reallocation lists the same goal more than once");
        }
    }

    private String goalName(UUID goalId) {
        return dataService.getSavingsGoalById(goalId).map(SavingsGoal::getName).orElse(null);
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
    public BalanceHistoryPageResponse getBalanceHistory(UUID bankAccountId, int page, int size) {
        // Validate bank account exists and is not deleted
        BankAccount account = dataService.getBankAccountById(bankAccountId)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + bankAccountId));

        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + bankAccountId);
        }

        // Create pageable with default size 20 if not specified
        Pageable pageable = PageRequest.of(page, size);

        // Retrieve paginated balance history from data service
        Page<BalanceHistory> historyPage = dataService.getBalanceHistoryByBankAccountId(bankAccountId, pageable);

        // Convert to DTO and return
        return BalanceHistoryExtensions.toPageResponse(historyPage);
    }

    @Override
    @Transactional
    public RecurringExpenseResponse createRecurringExpense(CreateRecurringExpenseRequest request) {
        // Check name uniqueness
        if (dataService.existsByRecurringExpenseName(request.name())) {
            throw new DuplicateRecurringExpenseException("Recurring expense with this name already exists");
        }

        // Resolve bank account if provided
        BankAccount bankAccount = null;
        if (request.bankAccountId() != null) {
            bankAccount = dataService.getBankAccountById(request.bankAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException(
                            "Bank account not found with id: " + request.bankAccountId()));
            if (bankAccount.getDeletedAt() != null) {
                throw new BankAccountNotFoundException(
                        "Bank account not found with id: " + request.bankAccountId());
            }
        }

        // Convert to entity (will throw IllegalArgumentException if invalid enum)
        RecurringExpense expense = RecurringExpenseExtensions.toEntity(request);

        // Save entity
        RecurringExpense savedExpense = dataService.saveRecurringExpense(expense);

        // Return DTO
        return RecurringExpenseExtensions.toResponse(savedExpense, bankAccount);
    }

    @Override
    public RecurringExpenseResponse getRecurringExpenseById(UUID id) {
        RecurringExpense expense = dataService.getRecurringExpenseById(id)
                .orElseThrow(() -> new org.example.axelnyman.main.shared.exceptions.RecurringExpenseNotFoundException(
                        "Recurring expense not found with id: " + id));

        BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());
        return RecurringExpenseExtensions.toResponse(expense, bankAccount);
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

        // Validate and resolve bank account if provided
        BankAccount bankAccount = null;
        if (request.bankAccountId() != null) {
            bankAccount = dataService.getBankAccountById(request.bankAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException(
                            "Bank account not found with id: " + request.bankAccountId()));
            if (bankAccount.getDeletedAt() != null) {
                throw new BankAccountNotFoundException(
                        "Bank account not found with id: " + request.bankAccountId());
            }
        }

        // Update fields
        expense.setName(request.name());
        expense.setAmount(request.amount());
        expense.setRecurrenceInterval(interval);
        expense.setIsManual(request.isManual());
        expense.setBankAccountId(request.bankAccountId());

        // Save (updatedAt will be auto-updated by JPA auditing)
        RecurringExpense updatedExpense = dataService.saveRecurringExpense(expense);

        return RecurringExpenseExtensions.toResponse(updatedExpense, bankAccount);
    }

    @Override
    public RecurringExpenseListResponse getAllRecurringExpenses() {
        // Fetch all active recurring expenses
        List<RecurringExpense> expenses = dataService.getAllActiveRecurringExpenses();

        // Map to list item responses with due date calculation
        List<RecurringExpenseListItemResponse> expenseResponses = expenses.stream()
                .map(expense -> {
                    DueDate dueDate = calculateNextDueDate(expense);
                    String dueDisplay = formatDueDisplay(dueDate);
                    BankAccount bankAccount = resolveBankAccount(expense.getBankAccountId());

                    return RecurringExpenseExtensions.toListItemResponse(
                            expense, bankAccount,
                            dueDate != null ? dueDate.month() : null,
                            dueDate != null ? dueDate.year() : null,
                            dueDisplay);
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

    private BankAccount resolveBankAccount(UUID bankAccountId) {
        if (bankAccountId == null) {
            return null;
        }
        return dataService.getBankAccountById(bankAccountId)
                .filter(account -> account.getDeletedAt() == null)
                .orElse(null);
    }

    private DueDate calculateNextDueDate(RecurringExpense expense) {
        Integer lastMonth = expense.getLastUsedMonth();
        Integer lastYear = expense.getLastUsedYear();

        if (lastMonth == null || lastYear == null) {
            return null;
        }

        int monthsToAdd = switch (expense.getRecurrenceInterval()) {
            case MONTHLY -> 1;
            case QUARTERLY -> 3;
            case BIANNUALLY -> 6;
            case YEARLY -> 12;
        };

        int totalMonths = (lastYear * 12 + lastMonth - 1) + monthsToAdd;
        int dueMonth = (totalMonths % 12) + 1;
        int dueYear = totalMonths / 12;

        return new DueDate(dueMonth, dueYear);
    }

    private String formatDueDisplay(DueDate dueDate) {
        if (dueDate == null) {
            return null;
        }

        String monthName = java.time.Month.of(dueDate.month())
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

        int currentYear = LocalDate.now().getYear();
        if (dueDate.year() == currentYear) {
            return monthName;
        }
        return monthName + " " + dueDate.year();
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
                .map(budget -> {
                    BigDecimal totalIncome = dataService.calculateTotalIncome(budget.getId());
                    BigDecimal totalExpenses = dataService.calculateTotalExpenses(budget.getId());
                    BigDecimal totalSavings = dataService.calculateTotalSavings(budget.getId());
                    BigDecimal balance = totalIncome.subtract(totalExpenses).subtract(totalSavings);

                    BudgetTotalsResponse totals = new BudgetTotalsResponse(
                            totalIncome,
                            totalExpenses,
                            totalSavings,
                            balance
                    );

                    return BudgetExtensions.toResponse(budget, totals);
                })
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

        // Validate the optional savings-goal link (must reference an active goal)
        requireActiveGoalIfPresent(request.savingsGoalId());

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

        // Validate the optional savings-goal link (must reference an active goal)
        requireActiveGoalIfPresent(request.savingsGoalId());

        // Update fields (a null savingsGoalId unlinks the goal)
        savings.setName(request.name());
        savings.setAmount(request.amount());
        savings.setBankAccountId(request.bankAccountId());
        savings.setSavingsGoalId(request.savingsGoalId());

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

        // Earmark goal-linked savings toward their goals (item 070c). Done after
        // the balance update above so the savings money already credits each
        // account, keeping the unallocated invariant satisfied.
        allocateSavingsToGoalsOnLock(budgetId);

        // Update recurring expenses for this budget
        updateRecurringExpensesForBudget(budgetId, lockedAt, savedBudget.getMonth(), savedBudget.getYear());

        // Return DTO
        return BudgetExtensions.toResponse(savedBudget);
    }

    private void updateRecurringExpensesForBudget(UUID budgetId, LocalDateTime lockedAt, Integer budgetMonth, Integer budgetYear) {
        List<BudgetExpense> budgetExpenses = dataService.getBudgetExpensesByBudgetId(budgetId);

        List<UUID> recurringExpenseIds = budgetExpenses.stream()
                .filter(expense -> expense.getRecurringExpenseId() != null)
                .map(BudgetExpense::getRecurringExpenseId)
                .distinct()
                .toList();

        for (UUID recurringExpenseId : recurringExpenseIds) {
            RecurringExpense recurringExpense = dataService.getRecurringExpenseById(recurringExpenseId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Recurring expense not found with id: " + recurringExpenseId));

            recurringExpense.setLastUsedBudgetId(budgetId);
            recurringExpense.setLastUsedMonth(budgetMonth);
            recurringExpense.setLastUsedYear(budgetYear);
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
                    budgetId,
                    LocalDate.now()  // Automatic entries use current date
            );
            dataService.saveBalanceHistory(history);
        }
    }

    // Budget Unlock operations (Story 27)
    @Override
    @Transactional
    public BudgetResponse unlockBudget(UUID budgetId) {
        // Validate budget exists
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found"));

        // Validate budget is locked
        if (budget.getStatus() != BudgetStatus.LOCKED) {
            throw new BudgetNotLockedException("Budget is not locked");
        }

        // Validate this is the most recent budget
        Budget mostRecentBudget = dataService.getMostRecentBudget()
                .orElseThrow(() -> new BudgetNotFoundException("No budgets found"));

        if (!budget.getId().equals(mostRecentBudget.getId())) {
            throw new NotMostRecentBudgetException("Only the most recent budget can be unlocked");
        }

        // Reverse goal allocations made on lock (item 070c) before restoring
        // balances — the inverse order of lock (allocate after balance update).
        reverseSavingsGoalAllocationsOnUnlock(budgetId);

        // Reverse balance changes
        reverseBalanceChanges(budgetId);

        // Restore recurring expenses to previous state
        restoreRecurringExpenses(budgetId);

        // Delete todo list
        dataService.deleteTodoListByBudgetId(budgetId);

        // Update budget status and clear lockedAt
        budget.setStatus(BudgetStatus.UNLOCKED);
        budget.setLockedAt(null);
        Budget savedBudget = dataService.saveBudget(budget);

        return BudgetExtensions.toResponse(savedBudget);
    }

    private void reverseBalanceChanges(UUID budgetId) {
        // Find all AUTOMATIC balance history entries for this budget
        List<BalanceHistory> automaticHistory = dataService.getBalanceHistoryByBudgetIdAndSource(
                budgetId,
                BalanceHistorySource.AUTOMATIC
        );

        // For each history entry, reverse the balance change
        for (BalanceHistory history : automaticHistory) {
            // Load the bank account
            BankAccount account = dataService.getBankAccountById(history.getBankAccountId())
                    .orElseThrow(() -> new BankAccountNotFoundException(
                            "Bank account not found with id: " + history.getBankAccountId()));

            // Reverse the balance change (subtract the amount that was added)
            BigDecimal newBalance = account.getCurrentBalance().subtract(history.getChangeAmount());
            account.setCurrentBalance(newBalance);
            dataService.saveBankAccount(account);
        }

        // Delete all automatic balance history entries for this budget
        dataService.deleteBalanceHistoryByBudgetId(budgetId);
    }

    private void restoreRecurringExpenses(UUID budgetId) {
        // Get all budget expenses for this budget
        List<BudgetExpense> budgetExpenses = dataService.getBudgetExpensesByBudgetId(budgetId);

        // Get unique recurring expense IDs
        List<UUID> recurringExpenseIds = budgetExpenses.stream()
                .filter(expense -> expense.getRecurringExpenseId() != null)
                .map(BudgetExpense::getRecurringExpenseId)
                .distinct()
                .toList();

        // For each recurring expense, restore to previous locked budget state
        for (UUID recurringExpenseId : recurringExpenseIds) {
            RecurringExpense recurringExpense = dataService.getRecurringExpenseById(recurringExpenseId)
                    .orElse(null);

            if (recurringExpense == null) {
                continue;
            }

            // Only restore if this budget was the last one to use it
            if (budgetId.equals(recurringExpense.getLastUsedBudgetId())) {
                // Find previous locked budget that used this recurring expense
                List<Budget> previousBudgets = dataService.findLockedBudgetsUsingRecurringExpense(
                        recurringExpenseId,
                        budgetId
                );

                if (!previousBudgets.isEmpty()) {
                    Budget previousBudget = previousBudgets.get(0);
                    recurringExpense.setLastUsedBudgetId(previousBudget.getId());
                    recurringExpense.setLastUsedMonth(previousBudget.getMonth());
                    recurringExpense.setLastUsedYear(previousBudget.getYear());
                } else {
                    recurringExpense.setLastUsedBudgetId(null);
                    recurringExpense.setLastUsedMonth(null);
                    recurringExpense.setLastUsedYear(null);
                }

                dataService.saveRecurringExpense(recurringExpense);
            }
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

    @Override
    @Transactional
    public TodoItemResponse updateTodoItemStatus(UUID budgetId, UUID todoItemId, UpdateTodoItemRequest request) {
        // Fetch todo item by ID
        TodoItem todoItem = dataService.getTodoItemById(todoItemId)
                .orElseThrow(() -> new TodoItemNotFoundException("Todo item not found"));

        // Fetch budget and validate it exists
        Budget budget = dataService.getBudgetById(budgetId)
                .orElseThrow(() -> new BudgetNotFoundException("Budget not found"));

        // Validate budget is locked
        if (budget.getStatus() != BudgetStatus.LOCKED) {
            throw new BudgetNotLockedException("Budget must be locked");
        }

        // Fetch todo list for this budget and verify item belongs to it
        TodoList todoList = dataService.getTodoListByBudgetId(budgetId)
                .orElseThrow(() -> new TodoListNotFoundException("Todo list not found for this budget"));

        if (!todoItem.getTodoListId().equals(todoList.getId())) {
            throw new IllegalArgumentException("Todo item does not belong to this budget");
        }

        // Update status
        todoItem.setStatus(request.status());

        // Update completedAt timestamp based on status
        if (request.status() == TodoItemStatus.COMPLETED) {
            todoItem.setCompletedAt(LocalDateTime.now());
        } else {
            todoItem.setCompletedAt(null);
        }

        // Save updated todo item
        TodoItem updatedTodoItem = dataService.saveTodoItem(todoItem);

        // Fetch bank accounts for mapping
        BankAccount fromAccount = updatedTodoItem.getFromAccountId() != null
                ? dataService.getBankAccountById(updatedTodoItem.getFromAccountId()).orElse(null)
                : null;
        BankAccount toAccount = updatedTodoItem.getToAccountId() != null
                ? dataService.getBankAccountById(updatedTodoItem.getToAccountId()).orElse(null)
                : null;

        // Return mapped DTO
        return TodoExtensions.toItemResponse(updatedTodoItem, fromAccount, toAccount);
    }

    // ==================== Savings Goals (item 070a) ====================

    @Override
    @Transactional
    public SavingsGoalResponse createSavingsGoal(CreateSavingsGoalRequest request) {
        SavingsGoal goal = dataService.saveSavingsGoal(SavingsGoalExtensions.toEntity(request));

        if (request.allocations() != null) {
            for (SeedAllocationRequest seed : request.allocations()) {
                applyAllocation(goal.getId(), requireActiveAccount(seed.bankAccountId()),
                        seed.amount(), GoalAllocationChangeSource.MANUAL);
            }
        }

        return buildGoalResponse(goal);
    }

    @Override
    public SavingsGoalListResponse getAllSavingsGoals() {
        List<SavingsGoalResponse> goals = dataService.getActiveSavingsGoals().stream()
                .map(this::buildGoalResponse)
                .toList();
        return new SavingsGoalListResponse(goals.size(), goals);
    }

    @Override
    public SavingsGoalResponse getSavingsGoal(UUID id) {
        return buildGoalResponse(requireGoal(id));
    }

    @Override
    public GoalAllocationHistoryResponse getSavingsGoalHistory(UUID id) {
        SavingsGoal goal = requireGoal(id);

        List<GoalAllocationChange> changes = dataService.getGoalAllocationChangesByGoalId(goal.getId());
        Map<UUID, String> accountNames = accountNames(changes.stream()
                .map(GoalAllocationChange::getBankAccountId).distinct().toList());

        return new GoalAllocationHistoryResponse(goal.getId(), changes.stream()
                .map(change -> SavingsGoalExtensions.toResponse(change, accountNames))
                .toList());
    }

    @Override
    @Transactional
    public SavingsGoalResponse updateSavingsGoal(UUID id, UpdateSavingsGoalRequest request) {
        SavingsGoal goal = requireActiveGoal(id);
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setEndDate(request.endDate());
        return buildGoalResponse(dataService.saveSavingsGoal(goal));
    }

    @Override
    @Transactional
    public SavingsGoalResponse allocateToGoal(UUID id, AllocateRequest request) {
        SavingsGoal goal = requireActiveGoal(id);
        applyAllocation(goal.getId(), requireActiveAccount(request.bankAccountId()),
                request.amount(), GoalAllocationChangeSource.MANUAL);
        return buildGoalResponse(goal);
    }

    @Override
    @Transactional
    public SavingsGoalResponse archiveSavingsGoal(UUID id, ArchiveRequest request) {
        SavingsGoal goal = requireActiveGoal(id);

        for (GoalAllocation allocation : dataService.getGoalAllocationsByGoalId(goal.getId())) {
            BigDecimal freed = allocation.getAmount();

            if (request.releaseToBalance()) {
                BankAccount account = requireActiveAccount(allocation.getBankAccountId());
                BigDecimal newBalance = account.getCurrentBalance().subtract(freed);
                account.setCurrentBalance(newBalance);
                dataService.saveBankAccount(account);
                dataService.saveBalanceHistory(new BalanceHistory(
                        account.getId(), newBalance, freed.negate(),
                        "Released to balance on archiving goal: " + goal.getName(),
                        BalanceHistorySource.AUTOMATIC, null, LocalDate.now()));
            }

            dataService.saveGoalAllocationChange(new GoalAllocationChange(
                    goal.getId(), allocation.getBankAccountId(), freed.negate(), BigDecimal.ZERO,
                    GoalAllocationChangeSource.ARCHIVE));
            dataService.deleteGoalAllocation(allocation);
        }

        goal.setStatus(GoalStatus.ARCHIVED);
        goal.setArchivedAt(LocalDateTime.now());
        return buildGoalResponse(dataService.saveSavingsGoal(goal));
    }

    /**
     * Sets an account's allocation to a goal to an absolute {@code newAmount},
     * enforcing the per-account invariant (active allocations may not exceed the
     * account's balance) and appending a {@link GoalAllocationChange} ledger row.
     * Setting the amount to zero removes the allocation. A no-op change writes
     * nothing.
     */
    private void applyAllocation(UUID goalId, BankAccount account, BigDecimal newAmount,
                                 GoalAllocationChangeSource source) {
        Optional<GoalAllocation> existing = dataService.getGoalAllocation(goalId, account.getId());
        BigDecimal oldAmount = existing.map(GoalAllocation::getAmount).orElse(BigDecimal.ZERO);
        BigDecimal changeAmount = newAmount.subtract(oldAmount);

        if (changeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        // sumAllocationsByBankAccountId already includes oldAmount, so the
        // projected total after this change is currentSum + changeAmount.
        BigDecimal projectedTotal = dataService.sumAllocationsByBankAccountId(account.getId()).add(changeAmount);
        if (projectedTotal.compareTo(account.getCurrentBalance()) > 0) {
            throw new InsufficientUnallocatedFundsException(
                    "Allocation would exceed the unallocated balance of account: " + account.getName());
        }

        if (newAmount.compareTo(BigDecimal.ZERO) == 0) {
            existing.ifPresent(dataService::deleteGoalAllocation);
        } else if (existing.isPresent()) {
            existing.get().setAmount(newAmount);
            dataService.saveGoalAllocation(existing.get());
        } else {
            dataService.saveGoalAllocation(new GoalAllocation(goalId, account.getId(), newAmount));
        }

        dataService.saveGoalAllocationChange(new GoalAllocationChange(
                goalId, account.getId(), changeAmount, newAmount, source));
    }

    /**
     * On budget lock, earmark each goal-linked savings line toward its goal:
     * increase the goal's allocation on the savings item's account by the line's
     * amount (summed per goal+account), writing a {@code BUDGET_LOCK} ledger row.
     * Savings whose goal was archived or deleted since linking are skipped — those
     * goals no longer accept allocations. Runs inside the lock transaction; an
     * over-allocation would surface {@link InsufficientUnallocatedFundsException}
     * and roll back the whole lock.
     */
    private void allocateSavingsToGoalsOnLock(UUID budgetId) {
        savingsAmountByGoalAndAccount(budgetId).forEach((key, amount) -> {
            SavingsGoal goal = dataService.getSavingsGoalById(key.goalId()).orElse(null);
            if (goal == null || goal.getStatus() != GoalStatus.ACTIVE) {
                return;
            }
            BankAccount account = requireActiveAccount(key.accountId());
            BigDecimal current = dataService.getGoalAllocation(key.goalId(), key.accountId())
                    .map(GoalAllocation::getAmount).orElse(BigDecimal.ZERO);
            applyAllocation(key.goalId(), account, current.add(amount), GoalAllocationChangeSource.BUDGET_LOCK);
        });
    }

    /**
     * On unlock, undo exactly the allocation each goal-linked savings line added
     * on lock: reduce the goal's allocation on the account by the line's amount
     * (summed per goal+account), writing the reversing ledger row. Tolerant of
     * concurrent changes while the budget was locked — clamps at zero and skips
     * allocations already removed (e.g. by archiving the goal).
     */
    private void reverseSavingsGoalAllocationsOnUnlock(UUID budgetId) {
        savingsAmountByGoalAndAccount(budgetId).forEach((key, amount) -> {
            Optional<GoalAllocation> existing = dataService.getGoalAllocation(key.goalId(), key.accountId());
            BigDecimal current = existing.map(GoalAllocation::getAmount).orElse(BigDecimal.ZERO);
            if (current.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }
            BankAccount account = requireActiveAccount(key.accountId());
            BigDecimal reversed = current.subtract(amount).max(BigDecimal.ZERO);
            applyAllocation(key.goalId(), account, reversed, GoalAllocationChangeSource.BUDGET_LOCK);
        });
    }

    private Map<GoalAccountKey, BigDecimal> savingsAmountByGoalAndAccount(UUID budgetId) {
        return dataService.getBudgetSavingsByBudgetId(budgetId).stream()
                .filter(savings -> savings.getSavingsGoalId() != null)
                .collect(Collectors.groupingBy(
                        savings -> new GoalAccountKey(savings.getSavingsGoalId(), savings.getBankAccountId()),
                        Collectors.reducing(BigDecimal.ZERO, BudgetSavings::getAmount, BigDecimal::add)));
    }

    private record GoalAccountKey(UUID goalId, UUID accountId) {
    }

    private void requireActiveGoalIfPresent(UUID goalId) {
        if (goalId != null) {
            requireActiveGoal(goalId);
        }
    }

    private SavingsGoalResponse buildGoalResponse(SavingsGoal goal) {
        List<GoalAllocation> allocations = dataService.getGoalAllocationsByGoalId(goal.getId());
        Map<UUID, String> accountNames = accountNames(allocations.stream()
                .map(GoalAllocation::getBankAccountId).toList());
        return SavingsGoalExtensions.toResponse(goal, allocations, accountNames);
    }

    private Map<UUID, String> accountNames(List<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return dataService.getBankAccountsByIds(ids).stream()
                .collect(Collectors.toMap(BankAccount::getId, BankAccount::getName));
    }

    private SavingsGoal requireGoal(UUID id) {
        return dataService.getSavingsGoalById(id)
                .orElseThrow(() -> new SavingsGoalNotFoundException("Savings goal not found with id: " + id));
    }

    private SavingsGoal requireActiveGoal(UUID id) {
        SavingsGoal goal = requireGoal(id);
        if (goal.getStatus() == GoalStatus.ARCHIVED) {
            throw new SavingsGoalArchivedException("Cannot modify an archived savings goal");
        }
        return goal;
    }

    private BankAccount requireActiveAccount(UUID id) {
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));
        if (account.getDeletedAt() != null) {
            throw new BankAccountNotFoundException("Bank account not found with id: " + id);
        }
        return account;
    }
}
