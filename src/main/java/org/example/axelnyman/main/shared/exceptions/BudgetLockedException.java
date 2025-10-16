package org.example.axelnyman.main.shared.exceptions;

public class BudgetLockedException extends RuntimeException {
    public BudgetLockedException(String message) {
        super(message);
    }
}
