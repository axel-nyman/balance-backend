package org.example.axelnyman.main.shared.exceptions;

public class BudgetAlreadyLockedException extends RuntimeException {
    public BudgetAlreadyLockedException(String message) {
        super(message);
    }
}
