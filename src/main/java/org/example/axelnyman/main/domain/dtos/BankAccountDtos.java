package org.example.axelnyman.main.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class BankAccountDtos {

    public record CreateBankAccountRequest(
            @NotBlank(message = "Name is required")
            String name,

            @Size(max = 500, message = "Description must be less than 500 characters")
            String description,

            @PositiveOrZero(message = "Initial balance must be zero or positive")
            BigDecimal initialBalance
    ) {}

    public record BankAccountResponse(
            UUID id,
            String name,
            String description,
            BigDecimal currentBalance,
            LocalDateTime createdAt
    ) {}

    public record BankAccountListResponse(
            BigDecimal totalBalance,
            int accountCount,
            List<BankAccountResponse> accounts
    ) {}

    public record UpdateBalanceRequest(
            @NotNull(message = "New balance is required")
            BigDecimal newBalance,

            @NotNull(message = "Date is required")
            LocalDateTime date,

            @Size(max = 500, message = "Comment must be less than 500 characters")
            String comment
    ) {}

    public record BalanceUpdateResponse(
            UUID id,
            String name,
            BigDecimal currentBalance,
            BigDecimal previousBalance,
            BigDecimal changeAmount,
            LocalDateTime lastUpdated
    ) {}
}
