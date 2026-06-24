package org.example.axelnyman.main.shared.exceptions;

public class SavingsGoalNotFoundException extends RuntimeException {
    public SavingsGoalNotFoundException(String message) {
        super(message);
    }
}
