package org.example.axelnyman.main.shared.exceptions;

public class BudgetExpenseNotFoundException extends RuntimeException {
    public BudgetExpenseNotFoundException(String message) {
        super(message);
    }
}
