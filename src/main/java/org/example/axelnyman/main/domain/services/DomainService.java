package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.RecurringExpenseDtos.*;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.extensions.RecurringExpenseExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.RecurringExpense;
import org.example.axelnyman.main.shared.exceptions.AccountLinkedToBudgetException;
import org.example.axelnyman.main.shared.exceptions.BankAccountNotFoundException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBankAccountNameException;
import org.example.axelnyman.main.shared.exceptions.DuplicateRecurringExpenseException;
import org.example.axelnyman.main.shared.exceptions.FutureDateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

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
}
