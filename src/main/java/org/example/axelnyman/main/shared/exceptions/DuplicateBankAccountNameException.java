package org.example.axelnyman.main.shared.exceptions;

public class DuplicateBankAccountNameException extends RuntimeException {
    public DuplicateBankAccountNameException(String message) {
        super(message);
    }
}
