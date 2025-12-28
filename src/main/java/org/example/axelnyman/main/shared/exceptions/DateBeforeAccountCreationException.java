package org.example.axelnyman.main.shared.exceptions;

public class DateBeforeAccountCreationException extends RuntimeException {
    public DateBeforeAccountCreationException(String message) {
        super(message);
    }
}
