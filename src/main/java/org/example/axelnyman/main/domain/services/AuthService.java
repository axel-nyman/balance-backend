package org.example.axelnyman.main.domain.services;

import org.example.axelnyman.main.domain.abstracts.IAuthService;
import org.example.axelnyman.main.domain.abstracts.IDataService;
import org.example.axelnyman.main.domain.dtos.UserDtos.*;
import org.example.axelnyman.main.domain.extensions.UserExtensions;
import org.example.axelnyman.main.domain.model.User;
import org.example.axelnyman.main.shared.exceptions.DuplicateEmailException;
import org.example.axelnyman.main.shared.exceptions.InvalidCredentialsException;
import org.example.axelnyman.main.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthService implements IAuthService {

    private final IDataService dataService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(IDataService dataService, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.dataService = dataService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public AuthResponse registerUser(RegisterRequest request) {
        boolean exists = dataService.userExistsByEmailIncludingDeleted(request.email());
        if (exists) {
            throw new DuplicateEmailException("User with email " + request.email() + " already exists");
        }

        // Create user entity (password hashing handled by User constructor)
        User user = UserExtensions.toEntity(request);

        User savedUser = dataService.saveUser(user);

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(
                savedUser.getId(),
                savedUser.getEmail());

        return UserExtensions.toAuthResponse(token, savedUser);
    }

    @Override
    public AuthResponse login(LoginRequest loginRequest) {
        Optional<User> userOptional = dataService.findActiveUserByEmail(loginRequest.email());

        if (userOptional.isEmpty()) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        User user = userOptional.get();

        if (!passwordEncoder.matches(loginRequest.password(), user.getHashedPassword())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Generate JWT token
        String token = jwtTokenProvider.generateToken(
                user.getId(),
                user.getEmail());

        return UserExtensions.toAuthResponse(token, user);
    }
}