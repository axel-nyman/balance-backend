package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
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
        private BankAccountRepository bankAccountRepository;

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
                bankAccountRepository.deleteAll();
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
                // Given - create expense that has never been used (lastUsedMonth/Year = null)
                createRecurringExpense("Never Used Expense", "50.00", "MONTHLY");

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].dueMonth").value(nullValue()))
                                .andExpect(jsonPath("$.expenses[0].dueYear").value(nullValue()))
                                .andExpect(jsonPath("$.expenses[0].dueDisplay").value(nullValue()));
        }

        @Test
        void shouldCalculateNextDueDateForMonthlyInterval() throws Exception {
                // Given - create expense with MONTHLY interval, last used 2 months ago
                java.util.UUID expenseId = createRecurringExpense("Monthly Bill", "100.00", "MONTHLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDate twoMonthsAgo = java.time.LocalDate.now().minusMonths(2);
                expense.setLastUsedMonth(twoMonthsAgo.getMonthValue());
                expense.setLastUsedYear(twoMonthsAgo.getYear());
                recurringExpenseRepository.save(expense);

                // Expected: dueMonth = twoMonthsAgo + 1 month = 1 month ago (past due)
                java.time.LocalDate expectedDue = twoMonthsAgo.plusMonths(1);

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].dueMonth", is(expectedDue.getMonthValue())))
                                .andExpect(jsonPath("$.expenses[0].dueYear", is(expectedDue.getYear())))
                                .andExpect(jsonPath("$.expenses[0].dueDisplay").exists());
        }

        @Test
        void shouldCalculateNextDueDateForQuarterlyInterval() throws Exception {
                // Given - create expense with QUARTERLY interval, last used 2 months ago
                java.util.UUID expenseId = createRecurringExpense("Quarterly Tax", "500.00", "QUARTERLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDate twoMonthsAgo = java.time.LocalDate.now().minusMonths(2);
                expense.setLastUsedMonth(twoMonthsAgo.getMonthValue());
                expense.setLastUsedYear(twoMonthsAgo.getYear());
                recurringExpenseRepository.save(expense);

                // Expected: dueMonth = twoMonthsAgo + 3 months = 1 month in the future
                java.time.LocalDate expectedDue = twoMonthsAgo.plusMonths(3);

                // When & Then - should not be due yet (due next month)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].dueMonth", is(expectedDue.getMonthValue())))
                                .andExpect(jsonPath("$.expenses[0].dueYear", is(expectedDue.getYear())));
        }

        @Test
        void shouldCalculateNextDueDateForBiannuallyInterval() throws Exception {
                // Given - create expense with BIANNUALLY interval, last used 7 months ago
                java.util.UUID expenseId = createRecurringExpense("Car Insurance", "800.00", "BIANNUALLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDate sevenMonthsAgo = java.time.LocalDate.now().minusMonths(7);
                expense.setLastUsedMonth(sevenMonthsAgo.getMonthValue());
                expense.setLastUsedYear(sevenMonthsAgo.getYear());
                recurringExpenseRepository.save(expense);

                // Expected: dueMonth = sevenMonthsAgo + 6 months = 1 month ago (past due)
                java.time.LocalDate expectedDue = sevenMonthsAgo.plusMonths(6);

                // When & Then - should be due (past due by 1 month)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].dueMonth", is(expectedDue.getMonthValue())))
                                .andExpect(jsonPath("$.expenses[0].dueYear", is(expectedDue.getYear())));
        }

        @Test
        void shouldCalculateNextDueDateForYearlyInterval() throws Exception {
                // Given - create expense with YEARLY interval, last used 11 months ago
                java.util.UUID expenseId = createRecurringExpense("Annual Subscription", "1200.00", "YEARLY");

                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                java.time.LocalDate elevenMonthsAgo = java.time.LocalDate.now().minusMonths(11);
                expense.setLastUsedMonth(elevenMonthsAgo.getMonthValue());
                expense.setLastUsedYear(elevenMonthsAgo.getYear());
                recurringExpenseRepository.save(expense);

                // Expected: dueMonth = elevenMonthsAgo + 12 months = 1 month in the future
                java.time.LocalDate expectedDue = elevenMonthsAgo.plusMonths(12);

                // When & Then - should not be due yet (due next month)
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].dueMonth", is(expectedDue.getMonthValue())))
                                .andExpect(jsonPath("$.expenses[0].dueYear", is(expectedDue.getYear())));
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

        // ========== UPDATE RECURRING EXPENSE TESTS ==========

        @Test
        void shouldUpdateRecurringExpenseWithAllFields() throws Exception {
                // Given - create initial expense
                java.util.UUID expenseId = createRecurringExpense("Original Name", "100.00", "MONTHLY");

                // When - update all fields
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("recurrenceInterval", "YEARLY");
                updateRequest.put("isManual", true);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(expenseId.toString())))
                                .andExpect(jsonPath("$.name", is("Updated Name")))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.recurrenceInterval", is("YEARLY")))
                                .andExpect(jsonPath("$.isManual", is(true)))
                                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        void shouldUpdateOnlyName() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Old Name", "50.00", "MONTHLY");

                // When - update only name
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "New Name");
                updateRequest.put("amount", new BigDecimal("50.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("New Name")))
                                .andExpect(jsonPath("$.amount", is(50.00)));
        }

        @Test
        void shouldUpdateOnlyAmount() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Fixed Name", "100.00", "MONTHLY");

                // When - update only amount
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Fixed Name");
                updateRequest.put("amount", new BigDecimal("150.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Fixed Name")))
                                .andExpect(jsonPath("$.amount", is(150.00)));
        }

        @Test
        void shouldUpdateOnlyInterval() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Fixed Name", "100.00", "MONTHLY");

                // When - update only interval
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Fixed Name");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "YEARLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.recurrenceInterval", is("YEARLY")));
        }

        @Test
        void shouldUpdateOnlyIsManualFlag() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Fixed Name", "100.00", "MONTHLY");

                // When - update only isManual flag
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Fixed Name");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", true);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isManual", is(true)));
        }

        @Test
        void shouldRejectUpdateWhenNameIsDuplicate() throws Exception {
                // Given - create two expenses
                createRecurringExpense("Expense A", "100.00", "MONTHLY");
                java.util.UUID expenseBId = createRecurringExpense("Expense B", "200.00", "YEARLY");

                // When - try to update Expense B to have the same name as Expense A
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Expense A");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("recurrenceInterval", "YEARLY");
                updateRequest.put("isManual", false);

                // Then - should reject with duplicate error
                mockMvc.perform(put("/api/recurring-expenses/" + expenseBId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Recurring expense with this name already exists")));
        }

        @Test
        void shouldAllowSameNameWhenUpdatingOwnRecord() throws Exception {
                // Given - create expense
                java.util.UUID expenseId = createRecurringExpense("Netflix", "15.99", "MONTHLY");

                // When - update with same name but different amount
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Netflix");
                updateRequest.put("amount", new BigDecimal("17.99"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then - should succeed
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Netflix")))
                                .andExpect(jsonPath("$.amount", is(17.99)));
        }

        @Test
        void shouldAllowNameOfDeletedExpense() throws Exception {
                // Given - create and soft delete an expense
                java.util.UUID deletedExpenseId = createRecurringExpense("Old Name", "100.00", "MONTHLY");
                var deletedExpense = recurringExpenseRepository.findById(deletedExpenseId).orElseThrow();
                deletedExpense.setDeletedAt(java.time.LocalDateTime.now());
                recurringExpenseRepository.save(deletedExpense);

                // Create another expense
                java.util.UUID activeExpenseId = createRecurringExpense("New Name", "200.00", "YEARLY");

                // When - update active expense to use the deleted expense's name
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Old Name");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("recurrenceInterval", "YEARLY");
                updateRequest.put("isManual", false);

                // Then - should succeed
                mockMvc.perform(put("/api/recurring-expenses/" + activeExpenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Old Name")));
        }

        @Test
        void shouldRejectUpdateWithNegativeAmount() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "100.00", "MONTHLY");

                // When - try to update with negative amount
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test Expense");
                updateRequest.put("amount", new BigDecimal("-50.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void shouldRejectUpdateWithZeroAmount() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "100.00", "MONTHLY");

                // When - try to update with zero amount
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test Expense");
                updateRequest.put("amount", new BigDecimal("0.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists())
                                .andExpect(jsonPath("$.details.amount").exists());
        }

        @Test
        void shouldReturn404WhenExpenseNotFound() throws Exception {
                // Given - random UUID that doesn't exist
                java.util.UUID nonExistentId = java.util.UUID.randomUUID();

                // When - try to update non-existent expense
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + nonExistentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        void shouldRejectUpdateOfDeletedExpense() throws Exception {
                // Given - create and soft delete an expense
                java.util.UUID expenseId = createRecurringExpense("Deleted Expense", "100.00", "MONTHLY");
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                expense.setDeletedAt(java.time.LocalDateTime.now());
                recurringExpenseRepository.save(expense);

                // When - try to update the deleted expense
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("amount", new BigDecimal("150.00"));
                updateRequest.put("recurrenceInterval", "YEARLY");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        void shouldRejectInvalidRecurrenceInterval() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "100.00", "MONTHLY");

                // When - try to update with invalid interval
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test Expense");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "INVALID_INTERVAL");
                updateRequest.put("isManual", false);

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        // ========== DELETE RECURRING EXPENSE TESTS ==========

        @Test
        void shouldDeleteRecurringExpenseSuccessfully() throws Exception {
                // Given - create a recurring expense
                java.util.UUID expenseId = createRecurringExpense("Netflix Subscription", "15.99", "MONTHLY");

                // When - delete the expense
                mockMvc.perform(delete("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNoContent());

                // Then - verify deletedAt is set in database
                var deletedExpense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                assert deletedExpense.getDeletedAt() != null : "deletedAt should be set after deletion";
        }

        @Test
        void shouldReturn404WhenDeletingNonExistentExpense() throws Exception {
                // Given - random UUID that doesn't exist
                java.util.UUID nonExistentId = java.util.UUID.randomUUID();

                // When & Then - delete should return 404
                mockMvc.perform(delete("/api/recurring-expenses/" + nonExistentId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        void shouldReturn404WhenDeletingAlreadyDeletedExpense() throws Exception {
                // Given - create and soft delete an expense
                java.util.UUID expenseId = createRecurringExpense("Deleted Expense", "100.00", "MONTHLY");
                var expense = recurringExpenseRepository.findById(expenseId).orElseThrow();
                expense.setDeletedAt(java.time.LocalDateTime.now());
                recurringExpenseRepository.save(expense);

                // When & Then - trying to delete again should return 404
                mockMvc.perform(delete("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        void shouldReturn404OnSecondDeleteAttempt() throws Exception {
                // Given - create a recurring expense
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "50.00", "MONTHLY");

                // When - delete first time
                mockMvc.perform(delete("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNoContent());

                // Then - delete second time should return 404
                mockMvc.perform(delete("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("not found")));
        }

        @Test
        void shouldExcludeDeletedExpenseFromListEndpoint() throws Exception {
                // Given - create 3 expenses
                createRecurringExpense("Active Expense 1", "100.00", "MONTHLY");
                createRecurringExpense("Active Expense 2", "200.00", "YEARLY");
                java.util.UUID expenseToDelete = createRecurringExpense("Will Be Deleted", "300.00", "QUARTERLY");

                // When - delete one expense
                mockMvc.perform(delete("/api/recurring-expenses/" + expenseToDelete)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNoContent());

                // Then - list should only return 2 active expenses
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(2)))
                                .andExpect(jsonPath("$.expenses[*].name", not(hasItem("Will Be Deleted"))));
        }

        @Test
        void shouldAllowNameReuseAfterDeletion() throws Exception {
                // Given - create and delete an expense
                java.util.UUID expenseId = createRecurringExpense("Reusable Name", "100.00", "MONTHLY");

                mockMvc.perform(delete("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isNoContent());

                // When - create new expense with same name
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Reusable Name");
                request.put("amount", new BigDecimal("150.00"));
                request.put("recurrenceInterval", "YEARLY");
                request.put("isManual", true);

                // Then - should succeed without duplicate name error
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is("Reusable Name")))
                                .andExpect(jsonPath("$.amount", is(150.00)));
        }

        // ========== BANK ACCOUNT LINK TESTS ==========

        @Test
        void shouldCreateRecurringExpenseWithBankAccount() throws Exception {
                // Given
                var bankAccount = createBankAccountEntity("Checking Account", "Main account", new BigDecimal("5000.00"));

                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Netflix Subscription");
                request.put("amount", new BigDecimal("15.99"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);
                request.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is("Netflix Subscription")))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Checking Account")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(5000.00)));
        }

        @Test
        void shouldCreateRecurringExpenseWithoutBankAccount() throws Exception {
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
                                .andExpect(jsonPath("$.name", is("Netflix Subscription")))
                                .andExpect(jsonPath("$.bankAccount").value(nullValue()));
        }

        @Test
        void shouldReturnNotFoundWhenCreatingWithNonExistentBankAccount() throws Exception {
                // Given
                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Netflix Subscription");
                request.put("amount", new BigDecimal("15.99"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);
                request.put("bankAccountId", java.util.UUID.randomUUID().toString());

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("Bank account not found")));
        }

        @Test
        void shouldReturnNotFoundWhenCreatingWithDeletedBankAccount() throws Exception {
                // Given
                var bankAccount = createBankAccountEntity("Deleted Account", "To be deleted", new BigDecimal("1000.00"));
                bankAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount);

                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Netflix Subscription");
                request.put("amount", new BigDecimal("15.99"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);
                request.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("Bank account not found")));
        }

        @Test
        void shouldUpdateRecurringExpenseWithBankAccount() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "100.00", "MONTHLY");
                var bankAccount = createBankAccountEntity("Savings Account", "My savings", new BigDecimal("10000.00"));

                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test Expense");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Savings Account")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(10000.00)));
        }

        @Test
        void shouldUpdateRecurringExpenseToRemoveBankAccount() throws Exception {
                // Given - create expense with bank account
                var bankAccount = createBankAccountEntity("Checking Account", "Main", new BigDecimal("5000.00"));

                var createRequest = new java.util.HashMap<String, Object>();
                createRequest.put("name", "Linked Expense");
                createRequest.put("amount", new BigDecimal("50.00"));
                createRequest.put("recurrenceInterval", "MONTHLY");
                createRequest.put("isManual", false);
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                java.util.UUID expenseId = java.util.UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());

                // When - update to remove bank account (bankAccountId = null)
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Linked Expense");
                updateRequest.put("amount", new BigDecimal("50.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);
                // bankAccountId intentionally omitted (null)

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.bankAccount").value(nullValue()));
        }

        @Test
        void shouldUpdateRecurringExpenseToChangeBankAccount() throws Exception {
                // Given - create expense with first bank account
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                var createRequest = new java.util.HashMap<String, Object>();
                createRequest.put("name", "Changing Account Expense");
                createRequest.put("amount", new BigDecimal("75.00"));
                createRequest.put("recurrenceInterval", "MONTHLY");
                createRequest.put("isManual", false);
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                java.util.UUID expenseId = java.util.UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());

                // When - update to use second bank account
                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Changing Account Expense");
                updateRequest.put("amount", new BigDecimal("75.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")));
        }

        @Test
        void shouldReturnNotFoundWhenUpdatingWithNonExistentBankAccount() throws Exception {
                // Given
                java.util.UUID expenseId = createRecurringExpense("Test Expense", "100.00", "MONTHLY");

                var updateRequest = new java.util.HashMap<String, Object>();
                updateRequest.put("name", "Test Expense");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("recurrenceInterval", "MONTHLY");
                updateRequest.put("isManual", false);
                updateRequest.put("bankAccountId", java.util.UUID.randomUUID().toString());

                // When & Then
                mockMvc.perform(put("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", containsString("Bank account not found")));
        }

        @Test
        void shouldGetRecurringExpenseWithBankAccount() throws Exception {
                // Given - create expense with bank account
                var bankAccount = createBankAccountEntity("Checking Account", "Main", new BigDecimal("5000.00"));

                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Linked Expense");
                request.put("amount", new BigDecimal("50.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);
                request.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                java.util.UUID expenseId = java.util.UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());

                // When & Then - GET by ID
                mockMvc.perform(get("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Checking Account")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(5000.00)));
        }

        @Test
        void shouldGetAllRecurringExpensesWithBankAccounts() throws Exception {
                // Given - create expenses, some with bank accounts
                var bankAccount = createBankAccountEntity("Checking Account", "Main", new BigDecimal("5000.00"));

                // Expense with bank account
                var request1 = new java.util.HashMap<String, Object>();
                request1.put("name", "Linked Expense");
                request1.put("amount", new BigDecimal("50.00"));
                request1.put("recurrenceInterval", "MONTHLY");
                request1.put("isManual", false);
                request1.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request1)))
                                .andExpect(status().isCreated());

                // Expense without bank account
                createRecurringExpense("Unlinked Expense", "100.00", "YEARLY");

                // When & Then
                mockMvc.perform(get("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.expenses", hasSize(2)))
                                // Sorted alphabetically: Linked, Unlinked
                                .andExpect(jsonPath("$.expenses[0].name", is("Linked Expense")))
                                .andExpect(jsonPath("$.expenses[0].bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.expenses[0].bankAccount.name", is("Checking Account")))
                                .andExpect(jsonPath("$.expenses[1].name", is("Unlinked Expense")))
                                .andExpect(jsonPath("$.expenses[1].bankAccount").value(nullValue()));
        }

        @Test
        void shouldReturnNullBankAccountWhenLinkedAccountIsDeleted() throws Exception {
                // Given - create expense linked to bank account
                var bankAccount = createBankAccountEntity("Soon Deleted", "Will be deleted", new BigDecimal("1000.00"));

                var request = new java.util.HashMap<String, Object>();
                request.put("name", "Orphaned Expense");
                request.put("amount", new BigDecimal("50.00"));
                request.put("recurrenceInterval", "MONTHLY");
                request.put("isManual", false);
                request.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andReturn().getResponse().getContentAsString();

                java.util.UUID expenseId = java.util.UUID.fromString(
                                objectMapper.readTree(createResponse).get("id").asText());

                // Soft-delete the bank account
                bankAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount);

                // When & Then - GET should return bankAccount as null
                mockMvc.perform(get("/api/recurring-expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Orphaned Expense")))
                                .andExpect(jsonPath("$.bankAccount").value(nullValue()));
        }

        // ========== HELPER METHODS ==========

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

        // Helper method to create bank account entity directly via repository
        private org.example.axelnyman.main.domain.model.BankAccount createBankAccountEntity(
                        String name, String description, BigDecimal initialBalance) {
                org.example.axelnyman.main.domain.model.BankAccount account =
                                new org.example.axelnyman.main.domain.model.BankAccount(name, description, initialBalance);
                return bankAccountRepository.save(account);
        }
}
