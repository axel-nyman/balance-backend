package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.infrastructure.data.context.RecurringExpenseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class RecurringExpenseIntegrationTest {

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
        private RecurringExpenseRepository recurringExpenseRepository;

        @Autowired
        private ObjectMapper objectMapper;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders
                                .webAppContextSetup(context)
                                .build();

                // Clean database state between tests
                recurringExpenseRepository.deleteAll();
        }

        @AfterAll
        static void cleanup() {
                if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
                        postgreSQLContainer.stop();
                }
        }

        @Test
        void shouldCreateRecurringExpenseWithMonthlyInterval() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Netflix Subscription");
                request.put("amount", new BigDecimal("15.99"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.name", is("Netflix Subscription")))
                                .andExpect(jsonPath("$.amount", is(15.99)))
                                .andExpect(jsonPath("$.recurrenceInterval", is("MONTHLY")))
                                .andExpect(jsonPath("$.isManual", is(false)))
                                .andExpect(jsonPath("$.lastUsedDate").value(nullValue()))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldCreateRecurringExpenseWithQuarterlyInterval() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Quarterly Tax Payment");
                request.put("amount", new BigDecimal("500.00"));
                request.put("recurrenceInterval", "QUARTERLY");
                request.put("isManual", true);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.recurrenceInterval", is("QUARTERLY")))
                                .andExpect(jsonPath("$.isManual", is(true)));
        }

        @Test
        void shouldCreateRecurringExpenseWithBiannuallyInterval() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Car Insurance");
                request.put("amount", new BigDecimal("800.00"));
                request.put("recurrenceInterval", "BIANNUALLY");
                request.put("isManual", true);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.recurrenceInterval", is("BIANNUALLY")));
        }

        @Test
        void shouldCreateRecurringExpenseWithYearlyInterval() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Annual Subscription");
                request.put("amount", new BigDecimal("120.00"));
                request.put("recurrenceInterval", "YEARLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.recurrenceInterval", is("YEARLY")));
        }

        @Test
        void shouldRejectNegativeAmount() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Invalid Expense");
                request.put("amount", new BigDecimal("-10.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void shouldRejectZeroAmount() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Zero Amount");
                request.put("amount", new BigDecimal("0.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void shouldRejectNullAmount() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Null Amount");
                request.put("amount", null);
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void shouldRejectBlankName() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "");
                request.put("amount", new BigDecimal("50.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.name").exists());
        }

        @Test
        void shouldRejectNullInterval() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "No Interval");
                request.put("amount", new BigDecimal("50.00"));
                request.put("recurrenceInterval", null);
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDuplicateName() throws Exception {
                // Given - create first expense
                var firstRequest = new java.util.HashMap<String, Object>();
                firstRequest.put("name", "Duplicate Name");
                firstRequest.put("amount", new BigDecimal("100.00"));
                firstRequest.put("recurrenceInterval", "MONTHLY");
                firstRequest.put("isManual", false);

                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest)))
                                .andExpect(status().isCreated());

                // When - try to create second expense with same name
                var duplicateRequest = new java.util.HashMap<String, Object>();
                duplicateRequest.put("name", "Duplicate Name");
                duplicateRequest.put("amount", new BigDecimal("200.00"));
                duplicateRequest.put("recurrenceInterval", "YEARLY");
                duplicateRequest.put("isManual", true);

                // Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Recurring expense with this name already exists")));
        }

        @Test
        void shouldAllowSameNameAfterDeletion() throws Exception {
                // Given - create expense via API first
                var createRequest = new java.util.HashMap<String, Object>();
                createRequest.put("name", "Deleted Expense");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("recurrenceInterval", "MONTHLY");
                createRequest.put("isManual", false);

                String createResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                // Extract ID and soft delete it
                java.util.UUID expenseId = java.util.UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                expense.setDeletedAt(java.time.LocalDateTime.now());
                recurringExpenseRepository.save(expense);

                // When - create new expense with same name
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Deleted Expense");
                request.put("amount", new BigDecimal("150.00"));
                request.put("recurrenceInterval", "QUARTERLY");
                request.put("isManual", true);

                // Then - should succeed
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is("Deleted Expense")));
        }

        @Test
        void shouldSetLastUsedDateToNullOnCreation() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "New Template");
                request.put("amount", new BigDecimal("75.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.lastUsedDate").value(nullValue()));

                // Also verify in database
                var savedExpense = recurringExpenseRepository.findAll().get(0);
                assert savedExpense.getLastUsedDate() == null : "lastUsedDate should be null on creation";
        }
}
