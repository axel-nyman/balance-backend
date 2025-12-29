package org.example.axelnyman.main.shared.exceptions;

public class BackdatedBalanceUpdateException extends RuntimeException {
    public BackdatedBalanceUpdateException(String message) {
        super(message);
    }
}
