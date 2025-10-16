package org.example.axelnyman.main.shared.exceptions;

public class DuplicateBudgetException extends RuntimeException {
    public DuplicateBudgetException(String message) {
        super(message);
    }
}
