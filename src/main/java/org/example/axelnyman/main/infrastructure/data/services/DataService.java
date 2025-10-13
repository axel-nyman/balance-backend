package org.example.axelnyman.main.infrastructure.data.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.springframework.stereotype.Service;

@Service
public class DataService implements IDataService {

    private final BankAccountRepository bankAccountRepository;
    private final BalanceHistoryRepository balanceHistoryRepository;

    public DataService(BankAccountRepository bankAccountRepository,
                      BalanceHistoryRepository balanceHistoryRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.balanceHistoryRepository = balanceHistoryRepository;
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
    public java.util.List<BankAccount> getAllActiveBankAccounts() {
        return bankAccountRepository.findAllByDeletedAtIsNull();
    }

    @Override
    public BalanceHistory saveBalanceHistory(BalanceHistory balanceHistory) {
        return balanceHistoryRepository.save(balanceHistory);
    }
}
