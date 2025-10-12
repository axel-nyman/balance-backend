package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.*;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class BankAccountIntegrationTest {

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
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Clean database state between tests
        balanceHistoryRepository.deleteAll();
        bankAccountRepository.deleteAll();
    }

    @AfterAll
    static void cleanup() {
        if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
            postgreSQLContainer.stop();
        }
    }

    @Test
    void shouldCreateBankAccountWithInitialBalance() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                "Checking Account",
                "Primary checking account",
                new BigDecimal("1000.00")
        );

        // When & Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Checking Account")))
                .andExpect(jsonPath("$.description", is("Primary checking account")))
                .andExpect(jsonPath("$.currentBalance", is(1000.00)))
                .andExpect(jsonPath("$.createdAt").exists());

        // Verify balance history was created
        long historyCount = balanceHistoryRepository.count();
        assert historyCount == 1 : "Balance history entry should be created";
    }

    @Test
    void shouldCreateBankAccountWithDefaultBalanceWhenNotProvided() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                "Savings Account",
                "Emergency fund",
                null
        );

        // When & Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Savings Account")))
                .andExpect(jsonPath("$.description", is("Emergency fund")))
                .andExpect(jsonPath("$.currentBalance", is(0)))
                .andExpect(jsonPath("$.createdAt").exists());

        // Verify balance history was created
        long historyCount = balanceHistoryRepository.count();
        assert historyCount == 1 : "Balance history entry should be created";
    }

    @Test
    void shouldRejectDuplicateBankAccountName() throws Exception {
        // Given - create first account
        CreateBankAccountRequest firstRequest = new CreateBankAccountRequest(
                "Duplicate Account",
                "First account",
                new BigDecimal("500.00")
        );
        mockMvc.perform(post("/api/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // When - try to create second account with same name
        CreateBankAccountRequest duplicateRequest = new CreateBankAccountRequest(
                "Duplicate Account",
                "Second account",
                new BigDecimal("1000.00")
        );

        // Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bank account name already exists")));
    }

    @Test
    void shouldRejectNegativeInitialBalance() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                "Invalid Account",
                "Account with negative balance",
                new BigDecimal("-100.00")
        );

        // When & Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.details.initialBalance").exists());
    }

    @Test
    void shouldRejectEmptyName() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                "",
                "Account with empty name",
                new BigDecimal("100.00")
        );

        // When & Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void shouldRejectNullName() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                null,
                "Account with null name",
                new BigDecimal("100.00")
        );

        // When & Then
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void shouldCreateBankAccountWithoutAuthentication() throws Exception {
        // Given
        CreateBankAccountRequest request = new CreateBankAccountRequest(
                "Public Account",
                "No auth required",
                new BigDecimal("250.00")
        );

        // When & Then - No Authorization header needed
        mockMvc.perform(post("/api/bank-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Public Account")));
    }
}
