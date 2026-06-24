package org.example.axelnyman.main.shared.exceptions;

public class InsufficientUnallocatedFundsException extends RuntimeException {
    public InsufficientUnallocatedFundsException(String message) {
        super(message);
    }
}
