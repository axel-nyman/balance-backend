package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.abstracts.IDomainService;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.extensions.BankAccountExtensions;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.shared.exceptions.DuplicateBankAccountNameException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
