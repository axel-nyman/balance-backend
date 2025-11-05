package org.example.axelnyman.main.shared.exceptions;

public class BudgetNotBalancedException extends RuntimeException {
    public BudgetNotBalancedException(String message) {
        super(message);
    }
}
