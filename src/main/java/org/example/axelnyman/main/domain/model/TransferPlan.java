package org.example.axelnyman.main.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a planned money transfer between two bank accounts.
 * Used by the transfer calculation utility to generate a list of transfers
 * needed to balance all accounts according to a budget.
 */
public final class TransferPlan {

    private final UUID fromAccountId;
    private final UUID toAccountId;
    private final BigDecimal amount;

    public TransferPlan(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId cannot be null");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId cannot be null");
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransferPlan that = (TransferPlan) o;
        return Objects.equals(fromAccountId, that.fromAccountId) &&
                Objects.equals(toAccountId, that.toAccountId) &&
                Objects.equals(amount, that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromAccountId, toAccountId, amount);
    }

    @Override
    public String toString() {
        return "TransferPlan{" +
                "fromAccountId=" + fromAccountId +
                ", toAccountId=" + toAccountId +
                ", amount=" + amount +
                '}';
    }
}
