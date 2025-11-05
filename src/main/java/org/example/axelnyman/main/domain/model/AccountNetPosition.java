package org.example.axelnyman.main.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the net financial position of a bank account within a budget.
 * A positive netAmount indicates a surplus (account has extra money).
 * A negative netAmount indicates a deficit (account needs money).
 */
public final class AccountNetPosition implements Comparable<AccountNetPosition> {

    private final UUID accountId;
    private BigDecimal netAmount;

    public AccountNetPosition(UUID accountId, BigDecimal netAmount) {
        this.accountId = Objects.requireNonNull(accountId, "accountId cannot be null");
        this.netAmount = Objects.requireNonNull(netAmount, "netAmount cannot be null");
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getNetAmount() {
        return netAmount;
    }

    public void setNetAmount(BigDecimal netAmount) {
        this.netAmount = Objects.requireNonNull(netAmount, "netAmount cannot be null");
    }

    public boolean isSurplus() {
        return netAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean isDeficit() {
        return netAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    public boolean isBalanced() {
        return netAmount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public int compareTo(AccountNetPosition other) {
        // Sort by absolute value of netAmount in descending order
        // This helps the greedy algorithm pick largest surplus/deficit first
        return other.netAmount.abs().compareTo(this.netAmount.abs());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountNetPosition that = (AccountNetPosition) o;
        return Objects.equals(accountId, that.accountId) &&
                Objects.equals(netAmount, that.netAmount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, netAmount);
    }

    @Override
    public String toString() {
        return "AccountNetPosition{" +
                "accountId=" + accountId +
                ", netAmount=" + netAmount +
                '}';
    }
}
