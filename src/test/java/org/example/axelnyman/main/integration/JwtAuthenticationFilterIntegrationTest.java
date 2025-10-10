package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.example.axelnyman.main.domain.dtos.UserDtos.*;
import org.example.axelnyman.main.domain.model.User;
import org.example.axelnyman.main.infrastructure.data.context.UserRepository;
import org.example.axelnyman.main.infrastructure.security.JwtTokenProvider;

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class JwtAuthenticationFilterIntegrationTest {

        @Container
        @SuppressWarnings("resource")
        static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
                        .withDatabaseName("testdb")
                        .withUsername("test")
                        .withPassword("test");

        @DynamicPropertySource
        static void configureProperties(DynamicPropertyRegistry registry) {
                registry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
                registry.add("spring.datasource.username", postgreSQLContainer::getUsername);
                registry.add("spring.datasource.password", postgreSQLContainer::getPassword);
                registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }

        @Autowired
        private WebApplicationContext context;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ObjectMapper objectMapper;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders
                                .webAppContextSetup(context)
                                .apply(springSecurity())
                                .build();

                userRepository.deleteAll();
        }

        @AfterAll
        static void cleanup() {
                if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
                        postgreSQLContainer.stop();
                }
        }

        @Test
        void shouldAccessPublicEndpointsWithoutToken() throws Exception {
                RegisterRequest request = new RegisterRequest(
                                "John", "Doe", "john.doe@example.com", "password123");

                mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                LoginRequest loginDto = new LoginRequest("john.doe@example.com", "password123");
                mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginDto)))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldReturn401ForProtectedEndpointsWithoutToken() throws Exception {
                mockMvc.perform(get("/api/users/me"))
                                .andExpect(status().isUnauthorized())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.error", is("Unauthorized")));
        }

        @Test
        void shouldAccessProtectedEndpointsWithValidToken() throws Exception {
                // Create test user and get token
                String token = createUserAndGetToken("alice@example.com", "Alice", "Smith");

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.id", notNullValue()))
                                .andExpect(jsonPath("$.firstName", is("Alice")))
                                .andExpect(jsonPath("$.lastName", is("Smith")))
                                .andExpect(jsonPath("$.email", is("alice@example.com")))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldReturn401ForInvalidToken() throws Exception {
                String invalidToken = "invalid.jwt.token";

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.error", is("Unauthorized")));
        }

        @Test
        void shouldReturn401ForMalformedToken() throws Exception {
                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token"))
                                .andExpect(status().isUnauthorized());

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "NotBearer token"))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldReturn401ForExpiredToken() throws Exception {
                // Create a token provider with very short expiration for testing
                JwtTokenProvider shortExpirationProvider = new JwtTokenProvider(
                                "testSecretForShortExpirationTesting123456789",
                                1 // 1 millisecond expiration
                );

                // Create test user
                User user = new User("Bob", "Jones", "bob@example.com", "hashedPassword");
                User savedUser = userRepository.save(user);

                // Generate token that expires immediately
                String expiredToken = shortExpirationProvider.generateToken(
                                savedUser.getId(), "bob@example.com");

                // Wait for token to expire
                Thread.sleep(10);

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void shouldInjectCurrentUserInControllerMethods() throws Exception {
                String token = createUserAndGetToken("charlie@example.com", "Charlie", "Brown");
                User savedUser = userRepository.findAll().get(0);

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.id", is(savedUser.getId().intValue())))
                                .andExpect(jsonPath("$.firstName", is("Charlie")))
                                .andExpect(jsonPath("$.lastName", is("Brown")))
                                .andExpect(jsonPath("$.email", is("charlie@example.com")))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldClearSecurityContextOnInvalidToken() throws Exception {
                String validToken = createUserAndGetToken("grace@example.com", "Grace", "Kelly");
                String invalidToken = "invalid.token.here";

                // First request with valid token should work
                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken))
                                .andExpect(status().isOk());

                // Second request with invalid token should fail (security context should be
                // cleared)
                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + invalidToken))
                                .andExpect(status().isUnauthorized());
        }

        private String createUserAndGetToken(String email, String firstName, String lastName) throws Exception {
                RegisterRequest request = new RegisterRequest(firstName, lastName, email, "password123");

                String responseContent = mockMvc.perform(post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();

                var responseMap = objectMapper.readValue(responseContent, Map.class);
                return (String) responseMap.get("token");
        }
}
