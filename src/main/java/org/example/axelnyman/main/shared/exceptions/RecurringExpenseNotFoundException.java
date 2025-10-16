package org.example.axelnyman.main.shared.exceptions;

public class RecurringExpenseNotFoundException extends RuntimeException {
    public RecurringExpenseNotFoundException(String message) {
        super(message);
    }
}
