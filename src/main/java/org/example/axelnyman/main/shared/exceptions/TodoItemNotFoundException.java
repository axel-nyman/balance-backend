package org.example.axelnyman.main.shared.exceptions;

public class TodoItemNotFoundException extends RuntimeException {
    public TodoItemNotFoundException(String message) {
        super(message);
    }
}
