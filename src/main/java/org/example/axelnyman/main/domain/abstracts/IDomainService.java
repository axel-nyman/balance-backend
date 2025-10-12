package org.example.axelnyman.main.domain.abstracts;

import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;

/**
 * Domain Service - Responsible for general business operations
 * This service handles CRUD operations, data transformations, and business
 * rules
 * that apply across the application domain.
 */
public interface IDomainService {

    // Bank Account operations
    BankAccountResponse createBankAccount(CreateBankAccountRequest request);
}
