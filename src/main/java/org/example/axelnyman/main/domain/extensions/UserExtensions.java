package org.example.axelnyman.main.domain.extensions;

import org.example.axelnyman.main.domain.dtos.UserDtos.*;
import org.example.axelnyman.main.domain.model.User;

public final class UserExtensions {

    private UserExtensions() {
        // Prevent instantiation
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getCreatedAt());
    }

    public static AuthResponse toAuthResponse(String token, User user) {
        return new AuthResponse(token, toResponse(user));
    }

    public static User toEntity(RegisterRequest request) {
        return new User(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.password());
    }
}