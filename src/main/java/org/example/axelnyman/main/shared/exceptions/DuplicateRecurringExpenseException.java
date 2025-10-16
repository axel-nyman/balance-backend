package org.example.axelnyman.main.shared.exceptions;

public class DuplicateRecurringExpenseException extends RuntimeException {
    public DuplicateRecurringExpenseException(String message) {
        super(message);
    }
}
