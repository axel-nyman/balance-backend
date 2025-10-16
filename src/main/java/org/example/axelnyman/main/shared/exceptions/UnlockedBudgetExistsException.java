package org.example.axelnyman.main.shared.exceptions;

public class UnlockedBudgetExistsException extends RuntimeException {
    public UnlockedBudgetExistsException(String message) {
        super(message);
    }
}
