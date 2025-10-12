package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.domain.model.BankAccount;

import java.math.BigDecimal;

public final class BankAccountExtensions {

    private BankAccountExtensions() {
        // Prevent instantiation
    }

    public static BankAccountResponse toResponse(BankAccount bankAccount) {
        return new BankAccountResponse(
                bankAccount.getId(),
                bankAccount.getName(),
                bankAccount.getDescription(),
                bankAccount.getCurrentBalance(),
                bankAccount.getCreatedAt()
        );
    }

    public static BankAccount toEntity(CreateBankAccountRequest request) {
        BigDecimal initialBalance = request.initialBalance() != null
                ? request.initialBalance()
                : BigDecimal.ZERO;

        return new BankAccount(
                request.name(),
                request.description(),
                initialBalance
        );
    }
}
