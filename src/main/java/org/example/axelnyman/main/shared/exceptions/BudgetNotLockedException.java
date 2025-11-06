package org.example.axelnyman.main.shared.exceptions;

public class BudgetNotLockedException extends RuntimeException {
    public BudgetNotLockedException(String message) {
        super(message);
    }
}
