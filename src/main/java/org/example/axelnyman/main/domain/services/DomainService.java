package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
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
    public BalanceUpdateResponse updateBankAccountBalance(UUID id, UpdateBalanceRequest request) {
        // Validate date is not in the future
        if (request.date().isAfter(LocalDateTime.now())) {
            throw new FutureDateException("Date cannot be in the future");
        }

        // Get bank account by ID
        BankAccount account = dataService.getBankAccountById(id)
                .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));

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
}
