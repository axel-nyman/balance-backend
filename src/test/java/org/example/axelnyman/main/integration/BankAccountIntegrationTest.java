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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
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
                                new BigDecimal("1000.00"));

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
                                null);

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
                                new BigDecimal("500.00"));
                mockMvc.perform(post("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest)))
                                .andExpect(status().isCreated());

                // When - try to create second account with same name
                CreateBankAccountRequest duplicateRequest = new CreateBankAccountRequest(
                                "Duplicate Account",
                                "Second account",
                                new BigDecimal("1000.00"));

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
                                new BigDecimal("-100.00"));

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
                                new BigDecimal("100.00"));

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
                                new BigDecimal("100.00"));

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
                                new BigDecimal("250.00"));

                // When & Then - No Authorization header needed
                mockMvc.perform(post("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.name", is("Public Account")));
        }

        @Test
        void shouldReturnAllActiveBankAccounts() throws Exception {
                // Given - create multiple bank accounts
                createBankAccount("Checking Account", "Primary checking", new BigDecimal("1000.00"));
                createBankAccount("Savings Account", "Emergency fund", new BigDecimal("5000.00"));
                createBankAccount("Investment Account", "Long term", new BigDecimal("10000.00"));

                // When & Then
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accounts", hasSize(3)))
                                .andExpect(jsonPath("$.accounts[*].name", containsInAnyOrder(
                                                "Checking Account", "Savings Account", "Investment Account")))
                                .andExpect(jsonPath("$.accounts[*].currentBalance", hasItem(1000.00)))
                                .andExpect(jsonPath("$.accounts[*].currentBalance", hasItem(5000.00)))
                                .andExpect(jsonPath("$.accounts[*].currentBalance", hasItem(10000.00)));
        }

        @Test
        void shouldCalculateTotalBalanceAcrossAllAccounts() throws Exception {
                // Given - create accounts with specific balances
                createBankAccount("Account A", "Description A", new BigDecimal("1000.00"));
                createBankAccount("Account B", "Description B", new BigDecimal("2500.50"));
                createBankAccount("Account C", "Description C", new BigDecimal("3499.50"));

                // When & Then - total should be 7000.00
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.totalBalance", is(7000.00)))
                                .andExpect(jsonPath("$.accountCount", is(3)));
        }

        @Test
        void shouldReturnCorrectAccountCount() throws Exception {
                // Given - create 5 accounts
                createBankAccount("Account 1", "Desc 1", new BigDecimal("100.00"));
                createBankAccount("Account 2", "Desc 2", new BigDecimal("200.00"));
                createBankAccount("Account 3", "Desc 3", new BigDecimal("300.00"));
                createBankAccount("Account 4", "Desc 4", new BigDecimal("400.00"));
                createBankAccount("Account 5", "Desc 5", new BigDecimal("500.00"));

                // When & Then
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCount", is(5)))
                                .andExpect(jsonPath("$.accounts", hasSize(5)))
                                .andExpect(jsonPath("$.totalBalance", is(1500.00)));
        }

        @Test
        void shouldExcludeSoftDeletedAccounts() throws Exception {
                // Given - create accounts and soft delete one
                createBankAccount("Active Account 1", "Active", new BigDecimal("1000.00"));
                createBankAccount("Active Account 2", "Active", new BigDecimal("2000.00"));
                var deletedAccount = createBankAccountEntity("Deleted Account", "To be deleted",
                                new BigDecimal("3000.00"));

                // Soft delete the third account
                deletedAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(deletedAccount);

                // When & Then - should only return 2 active accounts
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCount", is(2)))
                                .andExpect(jsonPath("$.accounts", hasSize(2)))
                                .andExpect(jsonPath("$.totalBalance", is(3000.00)))
                                .andExpect(jsonPath("$.accounts[*].name", not(hasItem("Deleted Account"))));
        }

        @Test
        void shouldReturnEmptyListWhenNoAccountsExist() throws Exception {
                // Given - no accounts created (database is clean from setUp)

                // When & Then
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCount", is(0)))
                                .andExpect(jsonPath("$.accounts", hasSize(0)))
                                .andExpect(jsonPath("$.totalBalance", is(0)));
        }

        @Test
        void shouldUpdateBalanceSuccessfullyWithPositiveChange() throws Exception {
                // Given - create account with initial balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1500.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().minusDays(1).toString());
                updateRequest.put("comment", "Salary deposit");

                // When & Then
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(account.getId().toString())))
                                .andExpect(jsonPath("$.currentBalance", is(1500.00)))
                                .andExpect(jsonPath("$.previousBalance", is(1000.00)))
                                .andExpect(jsonPath("$.changeAmount", is(500.00)))
                                .andExpect(jsonPath("$.lastUpdated").exists());
        }

        @Test
        void shouldUpdateBalanceSuccessfullyWithNegativeChange() throws Exception {
                // Given - create account with balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("500.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Withdrawal");

                // When & Then
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(500.00)))
                                .andExpect(jsonPath("$.previousBalance", is(1000.00)))
                                .andExpect(jsonPath("$.changeAmount", is(-500.00)));
        }

        @Test
        void shouldUpdateBalanceToZero() throws Exception {
                // Given - account with positive balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("250.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("0.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Account emptied");

                // When & Then
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(0.00)))
                                .andExpect(jsonPath("$.changeAmount", is(-250.00)));
        }

        @Test
        void shouldUpdateBalanceFromZero() throws Exception {
                // Given - account with zero balance
                var account = createBankAccountEntity("Empty Account", "Starting from zero", BigDecimal.ZERO);
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1000.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Initial deposit");

                // When & Then
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(1000.00)))
                                .andExpect(jsonPath("$.previousBalance", is(0.00)))
                                .andExpect(jsonPath("$.changeAmount", is(1000.00)));
        }

        @Test
        void shouldAllowNegativeBalance() throws Exception {
                // Given - account with small balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("100.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("-50.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Overdraft");

                // When & Then - should allow negative balance
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(-50.00)))
                                .andExpect(jsonPath("$.changeAmount", is(-150.00)));
        }

        @Test
        void shouldCreateBalanceHistoryEntryWithManualSource() throws Exception {
                // Given - account to update
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                long historyCountBefore = balanceHistoryRepository.count();

                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1200.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Manual adjustment");

                // When
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());

                // Then - verify balance history entry created
                long historyCountAfter = balanceHistoryRepository.count();
                assert historyCountAfter == historyCountBefore + 1 : "Balance history entry should be created";

                // Verify it has MANUAL source
                var historyEntries = balanceHistoryRepository.findAll();
                var latestEntry = historyEntries.get(historyEntries.size() - 1);
                assert latestEntry.getSource().toString().equals("MANUAL") : "Source should be MANUAL";
                assert latestEntry.getBudgetId() == null : "BudgetId should be null";
        }

        @Test
        void shouldCalculateChangeAmountCorrectly() throws Exception {
                // Given - account with specific balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1234.56"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("2345.67"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Test calculation");

                // When & Then - change should be 2345.67 - 1234.56 = 1111.11
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.changeAmount", is(1111.11)));
        }

        @Test
        void shouldStoreCommentWhenProvided() throws Exception {
                // Given - update request with comment
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1100.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "This is a test comment");

                // When
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());

                // Then - verify comment is stored in history
                var historyEntries = balanceHistoryRepository.findAll();
                var latestEntry = historyEntries.get(historyEntries.size() - 1);
                assert latestEntry.getComment().equals("This is a test comment") : "Comment should be stored";
        }

        @Test
        void shouldHandleNullComment() throws Exception {
                // Given - update request without comment
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1100.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", null);

                // When & Then - should accept null comment
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldRejectFutureDate() throws Exception {
                // Given - update request with future date
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1500.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().plusDays(1).toString());
                updateRequest.put("comment", "Future date test");

                // When & Then - should reject with 403 Forbidden
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.error", is("Date cannot be in the future")));
        }

        @Test
        void shouldAcceptCurrentDate() throws Exception {
                // Given - update request with current date
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1200.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Current date test");

                // When & Then - should accept current date
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldAcceptPastDate() throws Exception {
                // Given - update request with past date
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("900.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().minusYears(1).toString());
                updateRequest.put("comment", "Historical adjustment");

                // When & Then - should accept past date
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());
        }

        @Test
        void shouldReturn404WhenBankAccountNotFound() throws Exception {
                // Given - non-existent account ID
                UUID nonExistentId = UUID.randomUUID();
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1000.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Test");

                // When & Then - should return 404
                mockMvc.perform(post("/api/bank-accounts/" + nonExistentId + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldHandleDecimalPrecision() throws Exception {
                // Given - account with precise decimal balance
                var account = createBankAccountEntity("Test Account", "For testing", new BigDecimal("1000.99"));
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("2500.49"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Precision test");

                // When & Then - should handle decimal precision correctly
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(2500.49)))
                                .andExpect(jsonPath("$.previousBalance", is(1000.99)))
                                .andExpect(jsonPath("$.changeAmount", is(1499.50)));
        }

        @Test
        void shouldUpdateBothNameAndDescription() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Old Name", "Old Description", new BigDecimal("1000.00"));
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "New Name");
                updateRequest.put("description", "New Description");

                // When & Then
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(account.getId().toString())))
                                .andExpect(jsonPath("$.name", is("New Name")))
                                .andExpect(jsonPath("$.description", is("New Description")))
                                .andExpect(jsonPath("$.currentBalance", is(1000.00)))
                                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        void shouldUpdateNameOnly() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Old Name", "Keep Description", new BigDecimal("500.00"));
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("description", "Keep Description");

                // When & Then
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Updated Name")))
                                .andExpect(jsonPath("$.description", is("Keep Description")));
        }

        @Test
        void shouldUpdateDescriptionOnly() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Keep Name", "Old Description", new BigDecimal("200.00"));
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Keep Name");
                updateRequest.put("description", "Updated Description");

                // When & Then
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Keep Name")))
                                .andExpect(jsonPath("$.description", is("Updated Description")));
        }

        @Test
        void shouldUpdateToEmptyDescription() throws Exception {
                // Given - account with description
                var account = createBankAccountEntity("Test Account", "Has Description", new BigDecimal("100.00"));
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Test Account");
                updateRequest.put("description", "");

                // When & Then - should allow empty description
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.description", is("")));
        }

        @Test
        void shouldReturnUpdatedTimestamp() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Test Account", "Description", new BigDecimal("1000.00"));
                Thread.sleep(100); // Ensure time difference

                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Updated Account");
                updateRequest.put("description", "Description");

                // When & Then - should have updatedAt field
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedAt").exists())
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldRejectDuplicateNameWhenUpdating() throws Exception {
                // Given - two accounts
                @SuppressWarnings("unused")
                var account1 = createBankAccountEntity("Account One", "First", new BigDecimal("100.00"));
                var account2 = createBankAccountEntity("Account Two", "Second", new BigDecimal("200.00"));

                // Try to update account2 with account1's name
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Account One");
                updateRequest.put("description", "Second");

                // When & Then - should reject with 400
                mockMvc.perform(put("/api/bank-accounts/" + account2.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Bank account name already exists")));
        }

        @Test
        void shouldAllowSameNameForSameAccount() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Same Name", "Old Description", new BigDecimal("500.00"));

                // Update description but keep same name
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Same Name");
                updateRequest.put("description", "New Description");

                // When & Then - should allow (same account keeps same name)
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Same Name")))
                                .andExpect(jsonPath("$.description", is("New Description")));
        }

        @Test
        void shouldRejectEmptyNameWhenUpdating() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Test Account", "Description", new BigDecimal("100.00"));

                // Try to update with empty name
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "");
                updateRequest.put("description", "Description");

                // When & Then - should reject with 400
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNameTooLong() throws Exception {
                // Given - existing account
                var account = createBankAccountEntity("Test Account", "Description", new BigDecimal("100.00"));

                // Create a name longer than 255 characters
                String longName = "A".repeat(256);
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", longName);
                updateRequest.put("description", "Description");

                // When & Then - should reject with 400
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn404WhenUpdatingNonExistentAccount() throws Exception {
                // Given - non-existent account ID
                UUID nonExistentId = UUID.randomUUID();
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "New Name");
                updateRequest.put("description", "New Description");

                // When & Then - should return 404
                mockMvc.perform(put("/api/bank-accounts/" + nonExistentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectUpdatingDeletedAccount() throws Exception {
                // Given - soft-deleted account
                var account = createBankAccountEntity("Deleted Account", "Description", new BigDecimal("100.00"));
                account.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(account);

                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "New Name");
                updateRequest.put("description", "New Description");

                // When & Then - should reject (404 or 400)
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldNotUpdateBalanceThroughDetailsEndpoint() throws Exception {
                // Given - account with specific balance
                BigDecimal originalBalance = new BigDecimal("1234.56");
                var account = createBankAccountEntity("Test Account", "Description", originalBalance);

                // Try to update details (balance not in request)
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("description", "Updated Description");

                // When
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.currentBalance", is(1234.56)));

                // Then - verify balance unchanged in database
                var updatedAccount = bankAccountRepository.findById(account.getId()).orElseThrow();
                assert updatedAccount.getCurrentBalance().compareTo(originalBalance) == 0
                                : "Balance should remain unchanged";
        }

        @Test
        void shouldDeleteBankAccountSuccessfully() throws Exception {
                // Given - create an account
                var account = createBankAccountEntity("Account to Delete", "Will be deleted", new BigDecimal("1000.00"));

                // When - delete the account
                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNoContent());
        }

        @Test
        void shouldSetDeletedAtTimestampWhenDeleted() throws Exception {
                // Given - create an account
                var account = createBankAccountEntity("Test Account", "For deletion", new BigDecimal("500.00"));
                assert account.getDeletedAt() == null : "DeletedAt should be null initially";

                // When - delete the account via API
                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNoContent());

                // Then - verify deletedAt is set in database
                var deletedAccount = bankAccountRepository.findById(account.getId()).orElseThrow();
                assert deletedAccount.getDeletedAt() != null : "DeletedAt should be set after deletion";
        }

        @Test
        void shouldExcludeDeletedAccountsFromGetAll() throws Exception {
                // Given - create 3 accounts and delete 1
                createBankAccount("Active Account 1", "Still active", new BigDecimal("1000.00"));
                createBankAccount("Active Account 2", "Still active", new BigDecimal("2000.00"));
                var accountToDelete = createBankAccountEntity("Deleted Account", "Will be deleted", new BigDecimal("3000.00"));

                // Delete the third account
                mockMvc.perform(delete("/api/bank-accounts/" + accountToDelete.getId()))
                                .andExpect(status().isNoContent());

                // When & Then - GET all should return only 2 active accounts
                mockMvc.perform(get("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accountCount", is(2)))
                                .andExpect(jsonPath("$.accounts", hasSize(2)))
                                .andExpect(jsonPath("$.totalBalance", is(3000.00)))
                                .andExpect(jsonPath("$.accounts[*].name", not(hasItem("Deleted Account"))));
        }

        @Test
        void shouldPreserveBalanceHistoryAfterDeletion() throws Exception {
                // Given - create account with balance history via API (creates initial history entry)
                CreateBankAccountRequest createRequest = new CreateBankAccountRequest(
                                "Account with History",
                                "Has history",
                                new BigDecimal("1000.00"));

                String createResponse = mockMvc.perform(post("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                UUID accountId = UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());

                // Create additional balance history entry
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("1500.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Balance update");

                mockMvc.perform(post("/api/bank-accounts/" + accountId + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk());

                // Count balance history entries before deletion
                long historyCountBefore = balanceHistoryRepository.count();
                assert historyCountBefore >= 2 : "Should have at least 2 balance history entries";

                // When - delete the account
                mockMvc.perform(delete("/api/bank-accounts/" + accountId))
                                .andExpect(status().isNoContent());

                // Then - verify balance history is still preserved
                long historyCountAfter = balanceHistoryRepository.count();
                assert historyCountAfter == historyCountBefore : "Balance history should be preserved after deletion";
        }

        @Test
        void shouldReturn404WhenDeletingNonExistentAccount() throws Exception {
                // Given - non-existent account ID
                UUID nonExistentId = UUID.randomUUID();

                // When & Then - should return 404
                mockMvc.perform(delete("/api/bank-accounts/" + nonExistentId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldReturn404WhenDeletingAlreadyDeletedAccount() throws Exception {
                // Given - create and delete an account
                var account = createBankAccountEntity("Account to Delete Twice", "First deletion", new BigDecimal("1000.00"));

                // Delete once
                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNoContent());

                // When & Then - try to delete again, should return 404
                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldNotAllowUpdatingDeletedAccountDetails() throws Exception {
                // Given - create and delete an account
                var account = createBankAccountEntity("Deleted Account", "Already deleted", new BigDecimal("1000.00"));

                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNoContent());

                // When - try to update the deleted account
                var updateRequest = new java.util.HashMap<String, String>();
                updateRequest.put("name", "New Name");
                updateRequest.put("description", "New Description");

                // Then - should return 404
                mockMvc.perform(put("/api/bank-accounts/" + account.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldNotAllowUpdatingDeletedAccountBalance() throws Exception {
                // Given - create and delete an account
                var account = createBankAccountEntity("Deleted Account", "Already deleted", new BigDecimal("1000.00"));

                mockMvc.perform(delete("/api/bank-accounts/" + account.getId()))
                                .andExpect(status().isNoContent());

                // When - try to update the balance of deleted account
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("newBalance", new BigDecimal("2000.00"));
                updateRequest.put("date", java.time.LocalDateTime.now().toString());
                updateRequest.put("comment", "Should fail");

                // Then - should return 404
                mockMvc.perform(post("/api/bank-accounts/" + account.getId() + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        // Helper methods for test data creation
        private void createBankAccount(String name, String description, BigDecimal initialBalance) throws Exception {
                CreateBankAccountRequest request = new CreateBankAccountRequest(name, description, initialBalance);
                mockMvc.perform(post("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        private org.example.axelnyman.main.domain.model.BankAccount createBankAccountEntity(String name,
                        String description, BigDecimal initialBalance) {
                org.example.axelnyman.main.domain.model.BankAccount account = new org.example.axelnyman.main.domain.model.BankAccount();
                account.setName(name);
                account.setDescription(description);
                account.setCurrentBalance(initialBalance);
                return bankAccountRepository.save(account);
        }
}
