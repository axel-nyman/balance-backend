package org.example.axelnyman.main.shared.exceptions;

public class AccountLinkedToBudgetException extends RuntimeException {
    public AccountLinkedToBudgetException(String message) {
        super(message);
    }
}
