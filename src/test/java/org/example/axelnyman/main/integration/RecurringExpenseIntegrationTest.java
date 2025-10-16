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

        // ========== LIST RECURRING EXPENSES TESTS ==========

        @Test
        void shouldListAllActiveRecurringExpenses() throws Exception {
                // Given - create 3 expenses with different names
                createRecurringExpense("Zebra Subscription", "100.00", "MONTHLY");
                createRecurringExpense("Apple Music", "10.99", "MONTHLY");
                createRecurringExpense("Mango Insurance", "500.00", "YEARLY");

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(3)))
                                // Verify sorted alphabetically
                                .andExpect(jsonPath("$.expenses[0].name", is("Apple Music")))
                                .andExpect(jsonPath("$.expenses[1].name", is("Mango Insurance")))
                                .andExpect(jsonPath("$.expenses[2].name", is("Zebra Subscription")));
        }

        @Test
        void shouldCalculateNextDueDateForNeverUsedExpense() throws Exception {
                // Given - create expense that has never been used (lastUsedDate = null)
                createRecurringExpense("Never Used Expense", "50.00", "MONTHLY");

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].lastUsedDate").value(nullValue()))
                                .andExpect(jsonPath("$.expenses[0].nextDueDate").value(nullValue()))
                                .andExpect(jsonPath("$.expenses[0].isDue", is(true))); // Always due if never used
        }

        @Test
        void shouldCalculateNextDueDateForMonthlyInterval() throws Exception {
                // Given - create expense with MONTHLY interval
                java.util.UUID expenseId = createRecurringExpense("Monthly Bill", "100.00", "MONTHLY");

                // Set lastUsedDate to 2 months ago (so it's past due)
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDateTime lastUsed = java.time.LocalDateTime.now().minusMonths(2);
                expense.setLastUsedDate(lastUsed);
                recurringExpenseRepository.save(expense);

                // Expected next due date = lastUsed + 1 month (which is 1 month ago, so past
                // due)

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].nextDueDate").exists())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(true))); // 2 months ago means it's past
                                                                                       // due
        }

        @Test
        void shouldCalculateNextDueDateForQuarterlyInterval() throws Exception {
                // Given - create expense with QUARTERLY interval
                java.util.UUID expenseId = createRecurringExpense("Quarterly Tax", "500.00", "QUARTERLY");

                // Set lastUsedDate to 2 months ago
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDateTime lastUsed = java.time.LocalDateTime.now().minusMonths(2);
                expense.setLastUsedDate(lastUsed);
                recurringExpenseRepository.save(expense);

                // Expected next due date = lastUsed + 3 months
                @SuppressWarnings("unused")
                java.time.LocalDateTime expectedNextDue = lastUsed.plusMonths(3);

                // When & Then - should not be due yet (2 months < 3 months)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].nextDueDate").exists())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(false))); // Not due yet
        }

        @Test
        void shouldCalculateNextDueDateForBiannuallyInterval() throws Exception {
                // Given - create expense with BIANNUALLY interval
                java.util.UUID expenseId = createRecurringExpense("Car Insurance", "800.00", "BIANNUALLY");

                // Set lastUsedDate to 7 months ago
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDateTime lastUsed = java.time.LocalDateTime.now().minusMonths(7);
                expense.setLastUsedDate(lastUsed);
                recurringExpenseRepository.save(expense);

                // Expected next due date = lastUsed + 6 months
                @SuppressWarnings("unused")
                java.time.LocalDateTime expectedNextDue = lastUsed.plusMonths(6);

                // When & Then - should be due (7 months > 6 months)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].nextDueDate").exists())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(true))); // Past due
        }

        @Test
        void shouldCalculateNextDueDateForYearlyInterval() throws Exception {
                // Given - create expense with YEARLY interval
                java.util.UUID expenseId = createRecurringExpense("Annual Subscription", "1200.00", "YEARLY");

                // Set lastUsedDate to 11 months ago
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDateTime lastUsed = java.time.LocalDateTime.now().minusMonths(11);
                expense.setLastUsedDate(lastUsed);
                recurringExpenseRepository.save(expense);

                // Expected next due date = lastUsed + 1 year
                @SuppressWarnings("unused")
                java.time.LocalDateTime expectedNextDue = lastUsed.plusYears(1);

                // When & Then - should not be due yet (11 months < 12 months)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].nextDueDate").exists())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(false))); // Not due yet
        }

        @Test
        void shouldExcludeSoftDeletedExpenses() throws Exception {
                // Given - create 2 active expenses
                createRecurringExpense("Active Expense 1", "100.00", "MONTHLY");
                createRecurringExpense("Active Expense 2", "200.00", "YEARLY");

                // Create and soft delete one expense
                java.util.UUID deletedExpenseId = createRecurringExpense("Deleted Expense", "300.00", "QUARTERLY");
                var deletedExpense = recurringExpenseRepository.findById(deletedExpenseId).orElseThrow();
                deletedExpense.setDeletedAt(java.time.LocalDateTime.now());
                recurringExpenseRepository.save(deletedExpense);

                // When & Then - should only return 2 active expenses
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(2)))
                                .andExpect(jsonPath("$.expenses[*].name", not(hasItem("Deleted Expense"))));
        }

        @Test
        void shouldReturnEmptyListWhenNoExpenses() throws Exception {
                // Given - no expenses in database (already cleaned in setUp)

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(0)))
                                .andExpect(jsonPath("$.expenses", is(empty())));
        }

        @Test
        void shouldSortExpensesByNameAlphabetically() throws Exception {
                // Given - create expenses in non-alphabetical order
                createRecurringExpense("Zebra", "100.00", "MONTHLY");
                createRecurringExpense("Apple", "200.00", "YEARLY");
                createRecurringExpense("Mango", "300.00", "QUARTERLY");
                createRecurringExpense("Banana", "150.00", "BIANNUALLY");

                // When & Then - verify returned in alphabetical order
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(4)))
                                .andExpect(jsonPath("$.expenses[0].name", is("Apple")))
                                .andExpect(jsonPath("$.expenses[1].name", is("Banana")))
                                .andExpect(jsonPath("$.expenses[2].name", is("Mango")))
                                .andExpect(jsonPath("$.expenses[3].name", is("Zebra")));
        }

        @Test
        void shouldMarkExpenseAsDueWhenNextDueDateIsPast() throws Exception {
                // Given - create expense with lastUsedDate 2 months ago and MONTHLY interval
                java.util.UUID expenseId = createRecurringExpense("Overdue Expense", "100.00", "MONTHLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                expense.setLastUsedDate(java.time.LocalDateTime.now().minusMonths(2));
                recurringExpenseRepository.save(expense);

                // When & Then - should be marked as due
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(true)));
        }

        @Test
        void shouldMarkExpenseAsNotDueWhenNextDueDateIsFuture() throws Exception {
                // Given - create expense with lastUsedDate 1 day ago and MONTHLY interval
                java.util.UUID expenseId = createRecurringExpense("Recent Expense", "100.00", "MONTHLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                expense.setLastUsedDate(java.time.LocalDateTime.now().minusDays(1));
                recurringExpenseRepository.save(expense);

                // When & Then - should not be marked as due (only 1 day < 1 month)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses[0].isDue", is(false)));
        }

        // Helper method to create recurring expense via API
        private java.util.UUID createRecurringExpense(String name, String amount, String interval) throws Exception {
                var request = new java.util.HashMap<String, Object>();
                request.put("name", name);
                request.put("amount", new BigDecimal(amount));
                request.put("recurrenceInterval", interval);
                request.put("isManual", false);

                String response = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                return java.util.UUID.fromString(objectMapper.readTree(response).get("id").asText());
        }
}
