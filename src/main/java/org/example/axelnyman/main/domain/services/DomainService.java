package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.shared.exceptions.AccountLinkedToBudgetException;
import org.example.axelnyman.main.shared.exceptions.BankAccountNotFoundException;
import org.example.axelnyman.main.shared.exceptions.DuplicateBankAccountNameException;
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
}
