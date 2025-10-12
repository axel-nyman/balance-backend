package org.example.axelnyman.main.domain.abstracts;

import java.util.Optional;

import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.User;

/**
 * Data Access Service - Responsible for direct database operations
 * This service provides a clean abstraction over repository operations
 * and should not contain business logic.
 */
public interface IDataService {
    User saveUser(User user);

    Optional<User> getUserById(Long id);

    boolean deleteUserById(Long id);

    boolean userExistsByEmailIncludingDeleted(String email);

    Optional<User> findActiveUserByEmail(String email);

    // Bank Account operations
    BankAccount saveBankAccount(BankAccount bankAccount);

    boolean existsByBankAccountName(String name);

    // Balance History operations
    BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory);
}
