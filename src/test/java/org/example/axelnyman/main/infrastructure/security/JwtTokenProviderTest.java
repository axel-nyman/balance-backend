package org.example.axelnyman.main.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private final String testSecret = "testSecretKey123456789012345678901234567890";
    private final long testExpiration = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(testSecret, testExpiration);
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        // Arrange
        Long userId = 1L;
        String email = "test@example.com";

        // Act
        String token = jwtTokenProvider.generateToken(userId, email);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        // Arrange
        Long userId = 1L;
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(userId, email);

        // Act
        boolean isValid = jwtTokenProvider.validateToken(token);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void validateToken_WithInvalidToken_ShouldReturnFalse() {
        // Arrange
        String invalidToken = "invalid.jwt.token";

        // Act
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        // Arrange - Create provider with very short expiration
        JwtTokenProvider shortExpirationProvider = new JwtTokenProvider(testSecret, 1); // 1ms expiration
        String token = shortExpirationProvider.generateToken(1L, "test@example.com");

        // Wait for token to expire
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Act
        boolean isValid = shortExpirationProvider.validateToken(token);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void getUserIdFromToken_ShouldReturnCorrectUserId() {
        // Arrange
        Long expectedUserId = 42L;
        String email = "test@example.com";
        String token = jwtTokenProvider.generateToken(expectedUserId, email);

        // Act
        String actualUserId = jwtTokenProvider.getUserIdFromToken(token);

        // Assert
        assertEquals(expectedUserId.toString(), actualUserId);
    }

    @Test
    void getEmailFromToken_ShouldReturnCorrectEmail() {
        // Arrange
        Long userId = 1L;
        String expectedEmail = "user@domain.com";
        String token = jwtTokenProvider.generateToken(userId, expectedEmail);

        // Act
        String actualEmail = jwtTokenProvider.getEmailFromToken(token);

        // Assert
        assertEquals(expectedEmail, actualEmail);
    }

    @Test
    void extractClaims_WithInvalidToken_ShouldThrowException() {
        // Arrange
        String invalidToken = "invalid.jwt.token";

        // Act & Assert
        assertThrows(Exception.class, () -> {
            jwtTokenProvider.getUserIdFromToken(invalidToken);
        });
    }

    @Test
    void generateToken_WithDifferentValues_ShouldCreateDifferentTokens() {
        // Arrange
        String token1 = jwtTokenProvider.generateToken(1L, "user1@example.com");
        String token2 = jwtTokenProvider.generateToken(2L, "user2@example.com");

        // Act & Assert
        assertNotEquals(token1, token2);
    }

    @Test
    void tokenRoundTrip_ShouldPreserveAllClaims() {
        // Arrange
        Long userId = 123L;
        String email = "roundtrip@test.com";

        // Act
        String token = jwtTokenProvider.generateToken(userId, email);

        // Assert
        assertTrue(jwtTokenProvider.validateToken(token));
        assertEquals(userId.toString(), jwtTokenProvider.getUserIdFromToken(token));
        assertEquals(email, jwtTokenProvider.getEmailFromToken(token));
    }
}
