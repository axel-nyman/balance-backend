package org.example.axelnyman.main.shared.exceptions;

public class CannotDeleteLockedBudgetException extends RuntimeException {
    public CannotDeleteLockedBudgetException(String message) {
        super(message);
    }
}
