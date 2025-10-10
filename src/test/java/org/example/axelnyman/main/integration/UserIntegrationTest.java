package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
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

import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class UserIntegrationTest {

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
        void shouldGetCurrentUserProfile() throws Exception {
                String token = createUserAndGetToken("john.doe@example.com", "John", "Doe");
                User savedUser = userRepository.findAll().get(0);

                mockMvc.perform(get("/api/users/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(savedUser.getId().intValue())))
                                .andExpect(jsonPath("$.firstName", is("John")))
                                .andExpect(jsonPath("$.lastName", is("Doe")))
                                .andExpect(jsonPath("$.email", is("john.doe@example.com")))
                                .andExpect(jsonPath("$.createdAt").exists())
                                // Ensure password is never included in response
                                .andExpect(jsonPath("$.password").doesNotExist())
                                .andExpect(jsonPath("$.hashedPassword").doesNotExist());
        }

        @Test
        void shouldReturn401WhenNotAuthenticated() throws Exception {
                mockMvc.perform(get("/api/users/me"))
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
