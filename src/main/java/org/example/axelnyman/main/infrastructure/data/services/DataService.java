package org.example.axelnyman.main.infrastructure.data.services;

import java.util.Optional;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.User;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DataService implements IDataService {

    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    public DataService(UserRepository userRepository,
                      BankAccountRepository bankAccountRepository,
                      BalanceHistoryRepository balanceHistoryRepository) {
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
    }

    @Override
    public User saveUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public boolean deleteUserById(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Override
    public boolean userExistsByEmailIncludingDeleted(String email) {
        return userRepository.existsByEmailIncludingDeleted(email);
    }

    @Override
    public Optional<User> findActiveUserByEmail(String email) {
        return userRepository.findActiveByEmail(email);
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
    public BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory) {
        return balanceHistoryRepository.save(balanceHistory);
    }
}
