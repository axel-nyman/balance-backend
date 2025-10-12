package org.example.axelnyman.main.domain.abstracts;

import java.util.Optional;

import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.dtos.UserDtos.*;

/**
 * Domain Service - Responsible for general business operations
 * This service handles CRUD operations, data transformations, and business
 * rules
 * that apply across the application domain.
 */
public interface IDomainService {

    Optional<UserResponse> getUserProfile(Long userId);

    // Bank Account operations
    BankAccountResponse createBankAccount(CreateBankAccountRequest request);
}
