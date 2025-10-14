package org.example.axelnyman.main.domain.abstracts;

import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;

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

    // Balance History operations
    BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory);
}
