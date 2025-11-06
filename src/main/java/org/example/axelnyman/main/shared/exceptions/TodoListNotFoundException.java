package org.example.axelnyman.main.shared.exceptions;

public class TodoListNotFoundException extends RuntimeException {
    public TodoListNotFoundException(String message) {
        super(message);
    }
}
