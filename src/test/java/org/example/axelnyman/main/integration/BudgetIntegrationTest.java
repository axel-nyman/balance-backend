package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.infrastructure.data.context.BudgetRepository;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.example.axelnyman.main.TestDateTimeMatchers.matchesTimestampIgnoringNanos;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class BudgetIntegrationTest {

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
        private BudgetRepository budgetRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository bankAccountRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetIncomeRepository budgetIncomeRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetExpenseRepository budgetExpenseRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetSavingsRepository budgetSavingsRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.RecurringExpenseRepository recurringExpenseRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.TodoListRepository todoListRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.TodoItemRepository todoItemRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository balanceHistoryRepository;

        @Autowired
        private ObjectMapper objectMapper;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders
                                .webAppContextSetup(context)
                                .build();

                // Clean database state between tests (order matters due to foreign keys)
                todoItemRepository.deleteAll();
                todoListRepository.deleteAll();
                budgetIncomeRepository.deleteAll();
                budgetExpenseRepository.deleteAll();
                budgetSavingsRepository.deleteAll();
                recurringExpenseRepository.deleteAll();
                budgetRepository.deleteAll();
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
        void shouldCreateBudgetWithValidMonthAndYear() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.month", is(6)))
                                .andExpect(jsonPath("$.year", is(2024)))
                                .andExpect(jsonPath("$.status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.lockedAt").doesNotExist())
                                .andExpect(jsonPath("$.totals").exists());
        }

        @Test
        void shouldCreateBudgetWithMinimumMonth() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(1, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.month", is(1)));
        }

        @Test
        void shouldCreateBudgetWithMaximumMonth() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(12, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.month", is(12)));
        }

        @Test
        void shouldCreateBudgetWithMinimumYear() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2000);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.year", is(2000)));
        }

        @Test
        void shouldCreateBudgetWithMaximumYear() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2100);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.year", is(2100)));
        }

        @Test
        void shouldReturnZeroTotalsForNewBudget() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.totals.income", is(0)))
                                .andExpect(jsonPath("$.totals.expenses", is(0)))
                                .andExpect(jsonPath("$.totals.savings", is(0)))
                                .andExpect(jsonPath("$.totals.balance", is(0)));
        }

        @Test
        void shouldRejectDuplicateBudgetForSameMonthYear() throws Exception {
                // Given - create first budget
                Map<String, Object> firstRequest = createBudgetRequest(6, 2024);
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest)))
                                .andExpect(status().isCreated());

                // When - try to create duplicate
                Map<String, Object> duplicateRequest = createBudgetRequest(6, 2024);

                // Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(duplicateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Budget already exists for this month")));
        }

        @Test
        void shouldRejectBudgetWhenAnotherIsUnlocked() throws Exception {
                // Given - create first unlocked budget
                Map<String, Object> firstRequest = createBudgetRequest(6, 2024);
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(firstRequest)))
                                .andExpect(status().isCreated());

                // When - try to create another budget
                Map<String, Object> secondRequest = createBudgetRequest(7, 2024);

                // Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(secondRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is(
                                                "Another budget is currently unlocked. Lock or delete it before creating a new budget.")));
        }

        @Test
        void shouldRejectInvalidMonthZero() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(0, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectInvalidMonthNegative() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(-1, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectInvalidMonthThirteen() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(13, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectInvalidYearTooLow() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 1999);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error",
                                                is("Invalid year value. Must be between 2000 and 2100")));
        }

        @Test
        void shouldRejectInvalidYearTooHigh() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2101);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error",
                                                is("Invalid year value. Must be between 2000 and 2100")));
        }

        @Test
        void shouldRejectNullMonth() throws Exception {
                // Given
                Map<String, Object> request = new HashMap<>();
                request.put("month", null);
                request.put("year", 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullYear() throws Exception {
                // Given
                Map<String, Object> request = new HashMap<>();
                request.put("month", 6);
                request.put("year", null);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldSetCreatedAtTimestamp() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldNotSetLockedAtForUnlockedBudget() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);

                // When & Then
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.lockedAt").doesNotExist());
        }

        // ============================================
        // List Budgets Tests (Story 11)
        // ============================================

        @Test
        void shouldReturnEmptyListWhenNoBudgetsExist() throws Exception {
                // Given - no budgets in database

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets").isArray())
                                .andExpect(jsonPath("$.budgets", hasSize(0)));
        }

        @Test
        void shouldReturnSingleBudget() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets").isArray())
                                .andExpect(jsonPath("$.budgets", hasSize(1)))
                                .andExpect(jsonPath("$.budgets[0].month", is(6)))
                                .andExpect(jsonPath("$.budgets[0].year", is(2024)))
                                .andExpect(jsonPath("$.budgets[0].status", is("UNLOCKED")));
        }

        @Test
        void shouldReturnMultipleBudgets() throws Exception {
                // Given - create 3 budgets (manually set status to LOCKED for first 2)
                createBudget(6, 2024);
                var budget1 = budgetRepository.findAll().get(0);
                budget1.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget1);

                createBudget(7, 2024);
                var budget2 = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 7).findFirst().get();
                budget2.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget2);

                createBudget(8, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets").isArray())
                                .andExpect(jsonPath("$.budgets", hasSize(3)));
        }

        @Test
        void shouldIncludeAllBudgetFields() throws Exception {
                // Given
                createBudget(6, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets[0].id").exists())
                                .andExpect(jsonPath("$.budgets[0].month").exists())
                                .andExpect(jsonPath("$.budgets[0].year").exists())
                                .andExpect(jsonPath("$.budgets[0].status").exists())
                                .andExpect(jsonPath("$.budgets[0].createdAt").exists())
                                .andExpect(jsonPath("$.budgets[0].totals").exists())
                                .andExpect(jsonPath("$.budgets[0].totals.income").exists())
                                .andExpect(jsonPath("$.budgets[0].totals.expenses").exists())
                                .andExpect(jsonPath("$.budgets[0].totals.savings").exists())
                                .andExpect(jsonPath("$.budgets[0].totals.balance").exists());
        }

        @Test
        void shouldReturnZeroTotalsForBudgetsWithoutItems() throws Exception {
                // Given - budget without income/expenses/savings
                createBudget(6, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets[0].totals.income", is(0)))
                                .andExpect(jsonPath("$.budgets[0].totals.expenses", is(0)))
                                .andExpect(jsonPath("$.budgets[0].totals.savings", is(0)))
                                .andExpect(jsonPath("$.budgets[0].totals.balance", is(0)));
        }

        @Test
        void shouldReturnCorrectTotalsForBudgetsWithItems() throws Exception {
                // Given - create budget with income, expenses, and savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                UUID budgetId = budget.getId();

                var bankAccount = createBankAccountEntity("Test Account", "Test", new BigDecimal("10000.00"));

                // Add income: 3000
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", "3000.00");
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                        .andExpect(status().isCreated());

                // Add expense: 1500
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", "1500.00");
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("deductedAt", "2024-06-01");
                expenseRequest.put("isManual", true);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                        .andExpect(status().isCreated());

                // Add savings: 500
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", "500.00");
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                        .andExpect(status().isCreated());

                // When & Then - verify totals are correctly calculated
                // Expected: income=3000, expenses=1500, savings=500, balance=1000
                mockMvc.perform(get("/api/budgets"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.budgets", hasSize(1)))
                        .andExpect(jsonPath("$.budgets[0].totals.income", is(3000.00)))
                        .andExpect(jsonPath("$.budgets[0].totals.expenses", is(1500.00)))
                        .andExpect(jsonPath("$.budgets[0].totals.savings", is(500.00)))
                        .andExpect(jsonPath("$.budgets[0].totals.balance", is(1000.00)));
        }

        @Test
        void shouldSortBudgetsByYearDescending() throws Exception {
                // Given - budgets from different years (manually set to LOCKED for 2nd and 3rd)
                createBudget(6, 2022);
                var budget2022 = budgetRepository.findAll().get(0);
                budget2022.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget2022);

                createBudget(6, 2023);
                var budget2023 = budgetRepository.findAll().stream()
                                .filter(b -> b.getYear() == 2023).findFirst().get();
                budget2023.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget2023);

                createBudget(6, 2024);

                // When & Then - newest year first
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(3)))
                                .andExpect(jsonPath("$.budgets[0].year", is(2024)))
                                .andExpect(jsonPath("$.budgets[1].year", is(2023)))
                                .andExpect(jsonPath("$.budgets[2].year", is(2022)));
        }

        @Test
        void shouldSortBudgetsByMonthDescendingWithinSameYear() throws Exception {
                // Given - multiple months in same year
                createBudget(1, 2024);
                var budgetJan = budgetRepository.findAll().get(0);
                budgetJan.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budgetJan);

                createBudget(6, 2024);
                var budgetJun = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 6).findFirst().get();
                budgetJun.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budgetJun);

                createBudget(12, 2024);

                // When & Then - newest month first
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(3)))
                                .andExpect(jsonPath("$.budgets[0].month", is(12)))
                                .andExpect(jsonPath("$.budgets[1].month", is(6)))
                                .andExpect(jsonPath("$.budgets[2].month", is(1)));
        }

        @Test
        void shouldSortByYearThenMonth() throws Exception {
                // Given - mix of years and months
                createBudget(12, 2023);
                var budget202312 = budgetRepository.findAll().get(0);
                budget202312.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget202312);

                createBudget(1, 2024);
                var budget202401 = budgetRepository.findAll().stream()
                                .filter(b -> b.getYear() == 2024 && b.getMonth() == 1).findFirst().get();
                budget202401.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget202401);

                createBudget(6, 2024);

                // When & Then - sorted by year DESC, then month DESC
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(3)))
                                .andExpect(jsonPath("$.budgets[0].year", is(2024)))
                                .andExpect(jsonPath("$.budgets[0].month", is(6)))
                                .andExpect(jsonPath("$.budgets[1].year", is(2024)))
                                .andExpect(jsonPath("$.budgets[1].month", is(1)))
                                .andExpect(jsonPath("$.budgets[2].year", is(2023)))
                                .andExpect(jsonPath("$.budgets[2].month", is(12)));
        }

        @Test
        void shouldIncludeBothLockedAndUnlockedBudgets() throws Exception {
                // Given - one unlocked, one locked
                createBudget(5, 2024);
                var lockedBudget = budgetRepository.findAll().get(0);
                lockedBudget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(lockedBudget);

                createBudget(6, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(2)))
                                .andExpect(jsonPath("$.budgets[0].status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.budgets[1].status", is("LOCKED")));
        }

        @Test
        void shouldShowLockedAtTimestampForLockedBudget() throws Exception {
                // Given - locked budget with lockedAt timestamp
                createBudget(5, 2024);
                var lockedBudget = budgetRepository.findAll().get(0);
                lockedBudget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                lockedBudget.setLockedAt(java.time.LocalDateTime.now());
                budgetRepository.save(lockedBudget);

                createBudget(6, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets[0].lockedAt").doesNotExist())
                                .andExpect(jsonPath("$.budgets[1].lockedAt").exists());
        }

        @Test
        void shouldHandleBudgetsFromDifferentYears() throws Exception {
                // Given - wide year range
                createBudget(1, 2020);
                var budget2020 = budgetRepository.findAll().get(0);
                budget2020.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget2020);

                createBudget(1, 2021);
                var budget2021 = budgetRepository.findAll().stream()
                                .filter(b -> b.getYear() == 2021).findFirst().get();
                budget2021.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget2021);

                createBudget(1, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(3)))
                                .andExpect(jsonPath("$.budgets[0].year", is(2024)))
                                .andExpect(jsonPath("$.budgets[1].year", is(2021)))
                                .andExpect(jsonPath("$.budgets[2].year", is(2020)));
        }

        @Test
        void shouldHandleAllMonthsInYear() throws Exception {
                // Given - all 12 months (set first 11 to LOCKED)
                for (int month = 1; month <= 11; month++) {
                        final int currentMonth = month;
                        createBudget(currentMonth, 2024);
                        var budget = budgetRepository.findAll().stream()
                                        .filter(b -> b.getMonth() == currentMonth).findFirst().get();
                        budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                        budgetRepository.save(budget);
                }
                createBudget(12, 2024);

                // When & Then
                mockMvc.perform(get("/api/budgets"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.budgets", hasSize(12)))
                                .andExpect(jsonPath("$.budgets[0].month", is(12)))
                                .andExpect(jsonPath("$.budgets[11].month", is(1)));
        }

        // ============================================
        // View Budget Details Tests (Story 21)
        // ============================================

        @Test
        void shouldGetBudgetDetailsById() throws Exception {
                // Given - create budget with income, expenses, and savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                UUID budgetId = budget.getId();

                var bankAccount1 = createBankAccountEntity("Checking", "Main account", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Savings", "Savings account", new BigDecimal("5000.00"));

                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", "3000.00");
                incomeRequest.put("bankAccountId", bankAccount1.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", "1500.00");
                expenseRequest.put("bankAccountId", bankAccount1.getId().toString());
                expenseRequest.put("deductedAt", "2024-06-01");
                expenseRequest.put("isManual", true);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", "500.00");
                savingsRequest.put("bankAccountId", bankAccount2.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When - get budget details
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(budgetId.toString())))
                                .andExpect(jsonPath("$.month", is(6)))
                                .andExpect(jsonPath("$.year", is(2024)))
                                .andExpect(jsonPath("$.status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.lockedAt").doesNotExist())
                                // Verify income
                                .andExpect(jsonPath("$.income", hasSize(1)))
                                .andExpect(jsonPath("$.income[0].name", is("Salary")))
                                .andExpect(jsonPath("$.income[0].amount", is(3000.00)))
                                .andExpect(jsonPath("$.income[0].bankAccount.id", is(bankAccount1.getId().toString())))
                                .andExpect(jsonPath("$.income[0].bankAccount.name", is("Checking")))
                                // Verify expenses
                                .andExpect(jsonPath("$.expenses", hasSize(1)))
                                .andExpect(jsonPath("$.expenses[0].name", is("Rent")))
                                .andExpect(jsonPath("$.expenses[0].amount", is(1500.00)))
                                .andExpect(jsonPath("$.expenses[0].bankAccount.id",
                                                is(bankAccount1.getId().toString())))
                                .andExpect(jsonPath("$.expenses[0].bankAccount.name", is("Checking")))
                                .andExpect(jsonPath("$.expenses[0].deductedAt", is("2024-06-01")))
                                .andExpect(jsonPath("$.expenses[0].isManual", is(true)))
                                .andExpect(jsonPath("$.expenses[0].recurringExpenseId").doesNotExist())
                                // Verify savings
                                .andExpect(jsonPath("$.savings", hasSize(1)))
                                .andExpect(jsonPath("$.savings[0].name", is("Emergency Fund")))
                                .andExpect(jsonPath("$.savings[0].amount", is(500.00)))
                                .andExpect(jsonPath("$.savings[0].bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.savings[0].bankAccount.name", is("Savings")))
                                // Verify totals: 3000 income - 1500 expenses - 500 savings = 1000 balance
                                .andExpect(jsonPath("$.totals.income", is(3000.00)))
                                .andExpect(jsonPath("$.totals.expenses", is(1500.00)))
                                .andExpect(jsonPath("$.totals.savings", is(500.00)))
                                .andExpect(jsonPath("$.totals.balance", is(1000.00)));
        }

        @Test
        void shouldGetBudgetDetailsWithMultipleItems() throws Exception {
                // Given - create budget with multiple items of each type
                createBudget(7, 2024);
                var budget = budgetRepository.findAll().get(0);
                UUID budgetId = budget.getId();

                var bankAccount = createBankAccountEntity("Main Account", "Primary", new BigDecimal("10000.00"));
                var recurringExpense = createRecurringExpenseEntity("Netflix", new BigDecimal("15.99"));

                // Add multiple income items
                for (int i = 1; i <= 3; i++) {
                        Map<String, Object> incomeRequest = new HashMap<>();
                        incomeRequest.put("name", "Income " + i);
                        incomeRequest.put("amount", "1000.00");
                        incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                        mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(incomeRequest)))
                                        .andExpect(status().isCreated());
                }

                // Add multiple expenses (manual and recurring)
                Map<String, Object> manualExpense = new HashMap<>();
                manualExpense.put("name", "Groceries");
                manualExpense.put("amount", "200.00");
                manualExpense.put("bankAccountId", bankAccount.getId().toString());
                manualExpense.put("deductedAt", "2024-07-15");
                manualExpense.put("isManual", true);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(manualExpense)))
                                .andExpect(status().isCreated());

                Map<String, Object> recurringExpenseReq = new HashMap<>();
                recurringExpenseReq.put("name", "Netflix Subscription");
                recurringExpenseReq.put("amount", "15.99");
                recurringExpenseReq.put("bankAccountId", bankAccount.getId().toString());
                recurringExpenseReq.put("recurringExpenseId", recurringExpense.getId().toString());
                recurringExpenseReq.put("deductedAt", "2024-07-01");
                recurringExpenseReq.put("isManual", false);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(recurringExpenseReq)))
                                .andExpect(status().isCreated());

                // Add multiple savings
                for (int i = 1; i <= 2; i++) {
                        Map<String, Object> savingsRequest = new HashMap<>();
                        savingsRequest.put("name", "Savings Goal " + i);
                        savingsRequest.put("amount", "300.00");
                        savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                        mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(savingsRequest)))
                                        .andExpect(status().isCreated());
                }

                // When & Then
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.income", hasSize(3)))
                                .andExpect(jsonPath("$.expenses", hasSize(2)))
                                .andExpect(jsonPath("$.savings", hasSize(2)))
                                // Verify recurring expense has recurringExpenseId
                                .andExpect(jsonPath(
                                                "$.expenses[?(@.name == 'Netflix Subscription')].recurringExpenseId",
                                                hasSize(1)))
                                // Verify totals: 3000 income - 215.99 expenses - 600 savings = 2184.01 balance
                                .andExpect(jsonPath("$.totals.income", is(3000.00)))
                                .andExpect(jsonPath("$.totals.expenses", is(215.99)))
                                .andExpect(jsonPath("$.totals.savings", is(600.00)))
                                .andExpect(jsonPath("$.totals.balance", is(2184.01)));
        }

        @Test
        void shouldGetBudgetDetailsWhenNoItems() throws Exception {
                // Given - budget without any items
                createBudget(8, 2024);
                var budget = budgetRepository.findAll().get(0);
                UUID budgetId = budget.getId();

                // When & Then
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(budgetId.toString())))
                                .andExpect(jsonPath("$.month", is(8)))
                                .andExpect(jsonPath("$.year", is(2024)))
                                .andExpect(jsonPath("$.income", hasSize(0)))
                                .andExpect(jsonPath("$.expenses", hasSize(0)))
                                .andExpect(jsonPath("$.savings", hasSize(0)))
                                .andExpect(jsonPath("$.totals.income", is(0)))
                                .andExpect(jsonPath("$.totals.expenses", is(0)))
                                .andExpect(jsonPath("$.totals.savings", is(0)))
                                .andExpect(jsonPath("$.totals.balance", is(0)));
        }

        @Test
        void shouldGetBudgetDetailsWithLockedBudget() throws Exception {
                // Given - locked budget with items
                createBudget(9, 2024);
                var budget = budgetRepository.findAll().get(0);
                UUID budgetId = budget.getId();

                var bankAccount = createBankAccountEntity("Account", "Desc", new BigDecimal("1000.00"));

                // Add income to budget before locking
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Monthly Income");
                incomeRequest.put("amount", "2500.00");
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Lock budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budget.setLockedAt(java.time.LocalDateTime.now());
                budgetRepository.save(budget);

                // When & Then
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("LOCKED")))
                                .andExpect(jsonPath("$.lockedAt").exists())
                                .andExpect(jsonPath("$.income", hasSize(1)))
                                .andExpect(jsonPath("$.totals.income", is(2500.00)));
        }

        @Test
        void shouldReturn404WhenBudgetNotFound() throws Exception {
                // Given - non-existent budget ID
                UUID nonExistentId = UUID.randomUUID();

                // When & Then
                mockMvc.perform(get("/api/budgets/" + nonExistentId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        // ============================================
        // Add Income to Budget Tests (Story 12)
        // ============================================

        @Test
        void shouldAddIncomeToUnlockedBudget() throws Exception {
                // Given - create unlocked budget and bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Salary Account", "Main income", new BigDecimal("5000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Monthly Salary");
                incomeRequest.put("amount", new BigDecimal("3000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Monthly Salary")))
                                .andExpect(jsonPath("$.amount", is(3000.00)))
                                .andExpect(jsonPath("$.bankAccount").exists())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Salary Account")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(5000.00)))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldReturnIncomeWithBankAccountDetails() throws Exception {
                // Given - budget and bank account with specific details
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Checking", "Primary account", new BigDecimal("1234.56"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Freelance Payment");
                incomeRequest.put("amount", new BigDecimal("500.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then - verify nested bank account object
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.bankAccount.id").exists())
                                .andExpect(jsonPath("$.bankAccount.name").exists())
                                .andExpect(jsonPath("$.bankAccount.currentBalance").exists());
        }

        @Test
        void shouldReturnCreatedTimestampForIncome() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Test Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("100.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldRejectIncomeForLockedBudget() throws Exception {
                // Given - locked budget
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify locked budget")));
        }

        @Test
        void shouldRejectIncomeForNonExistentBudget() throws Exception {
                // Given - non-existent budget ID
                java.util.UUID nonExistentId = java.util.UUID.randomUUID();
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + nonExistentId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectIncomeForDeletedBudget() throws Exception {
                // Given - soft-deleted budget
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                budget.setDeletedAt(java.time.LocalDateTime.now());
                budgetRepository.save(budget);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNegativeIncomeAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("-100.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectZeroIncomeAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", BigDecimal.ZERO);
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldAllowDecimalIncomeAmounts() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1234.56"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.amount", is(1234.56)));
        }

        @Test
        void shouldRejectEmptyIncomeName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullIncomeName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", null);
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldAllowLongIncomeNames() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                String longName = "This is a reasonably long income name that should be accepted by the system";
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", longName);
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name", is(longName)));
        }

        @Test
        void shouldRejectNonExistentBankAccount() throws Exception {
                // Given - non-existent bank account ID
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                java.util.UUID nonExistentAccountId = java.util.UUID.randomUUID();

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", nonExistentAccountId.toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeletedBankAccount() throws Exception {
                // Given - soft-deleted bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Deleted Account", "Will be deleted",
                                new BigDecimal("1000.00"));
                bankAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount);

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullBankAccountId() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);

                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", new BigDecimal("1000.00"));
                incomeRequest.put("bankAccountId", null);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldPreventDeletingBankAccountLinkedToUnlockedBudgetIncome() throws Exception {
                // Given - bank account used in unlocked budget income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Linked Account", "Used in budget",
                                new BigDecimal("1000.00"));

                // Add income to budget using this bank account
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", new BigDecimal("3000.00"));
                incomeRequest.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // When & Then - try to delete bank account
                mockMvc.perform(delete("/api/bank-accounts/" + bankAccount.getId()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot delete account used in unlocked budget")));
        }

        // ============================================
        // Update Income in Budget Tests (Story 13)
        // ============================================

        @Test
        void shouldUpdateIncomeInUnlockedBudget() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Income");
                updateRequest.put("amount", new BigDecimal("750.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(incomeId)))
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Updated Income")))
                                .andExpect(jsonPath("$.amount", is(750.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")))
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        void shouldUpdateOnlyIncomeName() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Updated Name")))
                                .andExpect(jsonPath("$.amount", is(500.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())));
        }

        @Test
        void shouldUpdateOnlyIncomeAmount() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("999.99"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Income")))
                                .andExpect(jsonPath("$.amount", is(999.99)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())));
        }

        @Test
        void shouldUpdateOnlyBankAccount() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Income")))
                                .andExpect(jsonPath("$.amount", is(500.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")));
        }

        @Test
        void shouldReturnUpdatedTimestampAfterUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("600.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedAt").exists())
                                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        void shouldKeepCreatedAtUnchangedAfterUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();
                String originalCreatedAt = objectMapper.readTree(createResponse).get("createdAt").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("600.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)));
        }

        @Test
        void shouldRejectUpdateForLockedBudget() throws Exception {
                // Given - locked budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("600.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectUpdateForNonExistentIncome() throws Exception {
                // Given - budget without the income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                java.util.UUID nonExistentIncomeId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("600.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + nonExistentIncomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNegativeAmount() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("-100.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectZeroAmount() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", BigDecimal.ZERO);
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectEmptyName() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullName() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", null);
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullAmount() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", null);
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNonExistentBankAccountOnUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentAccountId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", nonExistentAccountId.toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeletedBankAccountOnUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "Test", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Will be deleted", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // Soft delete the second account
                bankAccount2.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount2);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullBankAccountIdOnUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", null);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectUpdateForNonExistentBudget() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("600.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + nonExistentBudgetId + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldAllowDecimalAmountsOnUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Income");
                updateRequest.put("amount", new BigDecimal("1234.56"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.amount", is(1234.56)));
        }

        @Test
        void shouldAllowLongIncomeNamesOnUpdate() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                String longName = "This is a very long income name that should be accepted by the update system";

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", longName);
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/income/" + incomeId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is(longName)));
        }

        // ============================================
        // Delete Income from Budget Tests (Story 14)
        // ============================================

        @Test
        void shouldDeleteIncomeFromUnlockedBudget() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income to Delete");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // When & Then - delete should return 204 No Content
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/income/" + incomeId))
                                .andExpect(status().isNoContent());
        }

        @Test
        void shouldActuallyRemoveIncomeFromDatabase() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income to Delete");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // When - delete income
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/income/" + incomeId))
                                .andExpect(status().isNoContent());

                // Then - verify income is actually removed
                var remainingIncome = budgetIncomeRepository.findById(java.util.UUID.fromString(incomeId));
                assert remainingIncome.isEmpty();
        }

        @Test
        void shouldRejectDeleteForLockedBudget() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                // When & Then - should reject with 400
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/income/" + incomeId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectDeleteForNonExistentIncome() throws Exception {
                // Given - budget without the income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);

                java.util.UUID nonExistentIncomeId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/income/" + nonExistentIncomeId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteForNonExistentBudget() throws Exception {
                // Given - budget with income
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + nonExistentBudgetId + "/income/" + incomeId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteWhenIncomeDoesNotBelongToBudget() throws Exception {
                // Given - two budgets, income belongs to first budget
                createBudget(6, 2024);
                var budget1 = budgetRepository.findAll().get(0);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Income");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget1.getId() + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String incomeId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock budget1 and create budget2
                budget1.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget1);

                createBudget(7, 2024);
                var budget2 = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 7).findFirst().get();

                // When & Then - try to delete income using budget2's ID (income belongs to
                // budget1)
                mockMvc.perform(delete("/api/budgets/" + budget2.getId() + "/income/" + incomeId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        // ============================================
        // Add Expense to Budget Tests (Story 15)
        // ============================================

        @Test
        void shouldAddExpenseToUnlockedBudget() throws Exception {
                // Given - create unlocked budget and bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Checking", "Main account", new BigDecimal("2000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Electric Bill");
                expenseRequest.put("amount", new BigDecimal("150.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Electric Bill")))
                                .andExpect(jsonPath("$.amount", is(150.00)))
                                .andExpect(jsonPath("$.bankAccount").exists())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Checking")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(2000.00)))
                                .andExpect(jsonPath("$.isManual", is(false)))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldReturnExpenseWithBankAccountDetails() throws Exception {
                // Given - budget and bank account with specific details
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Savings", "Emergency fund", new BigDecimal("5000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Insurance Payment");
                expenseRequest.put("amount", new BigDecimal("250.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", true);

                // When & Then - verify nested bank account object
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.bankAccount.id").exists())
                                .andExpect(jsonPath("$.bankAccount.name").exists())
                                .andExpect(jsonPath("$.bankAccount.currentBalance").exists());
        }

        @Test
        void shouldAddExpenseWithRecurringTemplate() throws Exception {
                // Given - budget, bank account, and recurring expense template
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Checking", "Main", new BigDecimal("1000.00"));

                // Create recurring expense via API or directly
                var recurringExpense = createRecurringExpenseEntity("Netflix Subscription", new BigDecimal("15.99"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Netflix Subscription");
                expenseRequest.put("amount", new BigDecimal("15.99"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("recurringExpenseId", recurringExpense.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.recurringExpenseId", is(recurringExpense.getId().toString())));
        }

        @Test
        void shouldAddExpenseWithDeductionDate() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Checking", "Main", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Rent Payment");
                expenseRequest.put("amount", new BigDecimal("1200.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("deductedAt", "2024-06-01");
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.deductedAt", is("2024-06-01")));
        }

        @Test
        void shouldAddExpenseMarkedAsManual() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Cash", "Cash payments", new BigDecimal("500.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Cash Payment");
                expenseRequest.put("amount", new BigDecimal("50.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.isManual", is(true)));
        }

        @Test
        void shouldAddExpenseWithNullDeductionDate() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Checking", "Main", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Flexible Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("deductedAt", null);
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.deductedAt").doesNotExist());
        }

        @Test
        void shouldRejectExpenseForLockedBudget() throws Exception {
                // Given - locked budget
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify locked budget")));
        }

        @Test
        void shouldRejectExpenseForNonExistentBudget() throws Exception {
                // Given - non-existent budget ID
                java.util.UUID nonExistentId = java.util.UUID.randomUUID();
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + nonExistentId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNegativeExpenseAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("-50.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectZeroExpenseAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", BigDecimal.ZERO);
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectEmptyExpenseName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullExpenseName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", null);
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNonExistentBankAccountForExpense() throws Exception {
                // Given - non-existent bank account ID
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                java.util.UUID nonExistentAccountId = java.util.UUID.randomUUID();

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", nonExistentAccountId.toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeletedBankAccountForExpense() throws Exception {
                // Given - soft-deleted bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Deleted Account", "Will be deleted",
                                new BigDecimal("1000.00"));
                bankAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount);

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNonExistentRecurringExpense() throws Exception {
                // Given - non-existent recurring expense ID
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));
                java.util.UUID nonExistentRecurringId = java.util.UUID.randomUUID();

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", new BigDecimal("100.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("recurringExpenseId", nonExistentRecurringId.toString());
                expenseRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldPreventDeletingBankAccountLinkedToUnlockedBudgetExpense() throws Exception {
                // Given - bank account used in unlocked budget expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Linked Account", "Used in budget",
                                new BigDecimal("1000.00"));

                // Add expense to budget using this bank account
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("name", "Electric Bill");
                expenseRequest.put("amount", new BigDecimal("150.00"));
                expenseRequest.put("bankAccountId", bankAccount.getId().toString());
                expenseRequest.put("isManual", false);

                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // When & Then - try to delete bank account
                mockMvc.perform(delete("/api/bank-accounts/" + bankAccount.getId()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot delete account used in unlocked budget")));
        }

        // Update Budget Expense Tests (Story 16)

        @Test
        void shouldUpdateExpenseInUnlockedBudget() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());
                createRequest.put("deductedAt", "2024-06-15");
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Expense");
                updateRequest.put("amount", new BigDecimal("350.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());
                updateRequest.put("deductedAt", "2024-06-20");
                updateRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(expenseId)))
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Updated Expense")))
                                .andExpect(jsonPath("$.amount", is(350.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")))
                                .andExpect(jsonPath("$.deductedAt", is("2024-06-20")))
                                .andExpect(jsonPath("$.isManual", is(false)))
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        void shouldUpdateOnlyExpenseName() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Updated Name")))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())));
        }

        @Test
        void shouldUpdateOnlyExpenseAmount() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", false);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("999.99"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Expense")))
                                .andExpect(jsonPath("$.amount", is(999.99)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())));
        }

        @Test
        void shouldUpdateOnlyExpenseBankAccount() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Expense")))
                                .andExpect(jsonPath("$.amount", is(200.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")));
        }

        @Test
        void shouldUpdateOnlyDeductedAt() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("deductedAt", "2024-06-15");
                createRequest.put("isManual", false);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("deductedAt", "2024-06-25");
                updateRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.deductedAt", is("2024-06-25")));
        }

        @Test
        void shouldUpdateOnlyIsManual() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", false);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.isManual", is(false)));
        }

        @Test
        void shouldReturnUpdatedTimestampAfterExpenseUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.updatedAt").exists())
                                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        void shouldKeepCreatedAtUnchangedAfterExpenseUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();
                String originalCreatedAt = objectMapper.readTree(createResponse).get("createdAt").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)));
        }

        @Test
        void shouldRejectUpdateForLockedBudgetExpense() throws Exception {
                // Given - locked budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectUpdateForNonExistentExpense() throws Exception {
                // Given - budget without the expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                java.util.UUID nonExistentExpenseId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + nonExistentExpenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectUpdateForNonExistentBudgetExpense() throws Exception {
                // Given - no budget exists
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));
                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();
                java.util.UUID expenseId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + nonExistentBudgetId + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNegativeExpenseAmountOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("-100.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectZeroExpenseAmountOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", BigDecimal.ZERO);
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectEmptyExpenseNameOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullExpenseNameOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", null);
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullExpenseAmountOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", null);
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullBankAccountIdOnExpenseUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", null);
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNonExistentBankAccountOnExpenseUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentAccountId = java.util.UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", nonExistentAccountId.toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeletedBankAccountOnExpenseUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "Test", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Will be deleted", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // Soft delete the second account
                bankAccount2.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount2);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());
                updateRequest.put("isManual", true);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectNullIsManualOnUpdate() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Expense");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());
                updateRequest.put("isManual", null);

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/expenses/" + expenseId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        // ============================================
        // Delete Expense from Budget Tests (Story 17)
        // ============================================

        @Test
        void shouldDeleteExpenseFromUnlockedBudget() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense to Delete");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // When & Then - delete should return 204 No Content
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/expenses/" + expenseId))
                                .andExpect(status().isNoContent());
        }

        @Test
        void shouldActuallyRemoveExpenseFromDatabase() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense to Delete");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // When - delete expense
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/expenses/" + expenseId))
                                .andExpect(status().isNoContent());

                // Then - verify expense is actually removed
                var remainingExpense = budgetExpenseRepository.findById(java.util.UUID.fromString(expenseId));
                assert remainingExpense.isEmpty();
        }

        @Test
        void shouldRejectDeleteForLockedBudgetExpense() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                // When & Then - should reject with 400
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/expenses/" + expenseId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectDeleteForNonExistentExpense() throws Exception {
                // Given - budget without the expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);

                java.util.UUID nonExistentExpenseId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/expenses/" + nonExistentExpenseId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteForNonExistentBudgetExpense() throws Exception {
                // Given - budget with expense
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + nonExistentBudgetId + "/expenses/" + expenseId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteWhenExpenseDoesNotBelongToBudget() throws Exception {
                // Given - two budgets, expense belongs to first budget
                createBudget(6, 2024);
                var budget1 = budgetRepository.findAll().get(0);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Expense");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("isManual", true);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget1.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock budget1 and create budget2
                budget1.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget1);

                createBudget(7, 2024);
                var budget2 = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 7).findFirst().get();

                // When & Then - try to delete expense using budget2's ID (expense belongs to
                // budget1)
                mockMvc.perform(delete("/api/budgets/" + budget2.getId() + "/expenses/" + expenseId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldNotAffectRecurringExpenseTemplate() throws Exception {
                // Given - recurring expense template and budget expense linked to it
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));
                var recurringExpense = createRecurringExpenseEntity("Netflix Subscription", new BigDecimal("15.99"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Netflix");
                createRequest.put("amount", new BigDecimal("15.99"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());
                createRequest.put("recurringExpenseId", recurringExpense.getId().toString());
                createRequest.put("isManual", false);

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String expenseId = objectMapper.readTree(createResponse).get("id").asText();

                // When - delete the budget expense
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/expenses/" + expenseId))
                                .andExpect(status().isNoContent());

                // Then - verify recurring expense template still exists and is unchanged
                var template = recurringExpenseRepository.findById(recurringExpense.getId());
                assert template.isPresent();
                assert template.get().getName().equals("Netflix Subscription");
                assert template.get().getAmount().compareTo(new BigDecimal("15.99")) == 0;
                assert template.get().getDeletedAt() == null;
        }

        // ============================================
        // Add Savings to Budget Tests (Story 18)
        // ============================================

        @Test
        void shouldAddSavingsToUnlockedBudget() throws Exception {
                // Given - create unlocked budget and bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Savings Account", "Emergency fund",
                                new BigDecimal("10000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", new BigDecimal("500.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Emergency Fund")))
                                .andExpect(jsonPath("$.amount", is(500.00)))
                                .andExpect(jsonPath("$.bankAccount").exists())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Savings Account")))
                                .andExpect(jsonPath("$.bankAccount.currentBalance", is(10000.00)))
                                .andExpect(jsonPath("$.createdAt").exists());
        }

        @Test
        void shouldReturnSavingsWithBankAccountDetails() throws Exception {
                // Given - budget and bank account with specific details
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Investment Account", "Long-term savings",
                                new BigDecimal("25000.50"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Retirement Fund");
                savingsRequest.put("amount", new BigDecimal("1000.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then - verify nested bank account object
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.bankAccount.id").exists())
                                .andExpect(jsonPath("$.bankAccount.name").exists())
                                .andExpect(jsonPath("$.bankAccount.currentBalance").exists());
        }

        @Test
        void shouldReturnCreatedTimestampForSavings() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Test Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Vacation Fund");
                savingsRequest.put("amount", new BigDecimal("200.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.createdAt").isNotEmpty());
        }

        @Test
        void shouldRejectSavingsForLockedBudget() throws Exception {
                // Given - locked budget
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("300.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify locked budget")));
        }

        @Test
        void shouldRejectSavingsForNonExistentBudget() throws Exception {
                // Given - non-existent budget ID
                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + nonExistentBudgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error")
                                                .value(org.hamcrest.Matchers.containsString("Budget not found")));
        }

        @Test
        void shouldRejectSavingsForNonExistentBankAccount() throws Exception {
                // Given - budget exists but bank account doesn't
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                java.util.UUID nonExistentAccountId = java.util.UUID.randomUUID();

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", nonExistentAccountId.toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error")
                                                .value(org.hamcrest.Matchers.containsString("Bank account not found")));
        }

        @Test
        void shouldRejectSavingsWithDeletedBankAccount() throws Exception {
                // Given - budget and soft-deleted bank account
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Deleted Account", "Test", new BigDecimal("1000.00"));
                bankAccount.setDeletedAt(java.time.LocalDateTime.now());
                bankAccountRepository.save(bankAccount);

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error")
                                                .value(org.hamcrest.Matchers.containsString("Bank account not found")));
        }

        @Test
        void shouldRejectSavingsWithNegativeAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("-50.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectSavingsWithZeroAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", BigDecimal.ZERO);
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectSavingsWithNullAmount() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", null);
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectSavingsWithNullBankAccountId() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", null);

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectSavingsWithBlankName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "   ");
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectSavingsWithNullName() throws Exception {
                // Given
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", null);
                savingsRequest.put("amount", new BigDecimal("100.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldPreventAccountDeletionWhenUsedInUnlockedBudgetSavings() throws Exception {
                // Given - bank account used in unlocked budget savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Savings Account", "Test", new BigDecimal("5000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", new BigDecimal("500.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When & Then - attempt to delete bank account should fail
                mockMvc.perform(delete("/api/bank-accounts/" + bankAccount.getId()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot delete account used in unlocked budget")));
        }

        @Test
        void shouldAllowAccountDeletionWhenUsedInLockedBudgetSavings() throws Exception {
                // Given - bank account used in locked budget savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Savings Account", "Test", new BigDecimal("5000.00"));

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", new BigDecimal("500.00"));
                savingsRequest.put("bankAccountId", bankAccount.getId().toString());

                // Add savings while budget is unlocked
                mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Now lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                // When & Then - deleting bank account should succeed (soft delete) because
                // budget is locked
                mockMvc.perform(delete("/api/bank-accounts/" + bankAccount.getId()))
                                .andExpect(status().isNoContent());

                // Verify soft delete
                var deletedAccount = bankAccountRepository.findById(bankAccount.getId());
                assert deletedAccount.isPresent();
                assert deletedAccount.get().getDeletedAt() != null;
        }

        // ============================================
        // Update Savings in Budget Tests (Story 19)
        // ============================================

        @Test
        void shouldUpdateSavingsInUnlockedBudget() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "First", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Second", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original Savings");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Savings");
                updateRequest.put("amount", new BigDecimal("350.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(savingsId)))
                                .andExpect(jsonPath("$.budgetId", is(budget.getId().toString())))
                                .andExpect(jsonPath("$.name", is("Updated Savings")))
                                .andExpect(jsonPath("$.amount", is(350.00)))
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")))
                                .andExpect(jsonPath("$.createdAt").exists())
                                .andExpect(jsonPath("$.updatedAt").exists());
        }

        @Test
        void shouldUpdateOnlySavingsName() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated Name");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Updated Name")))
                                .andExpect(jsonPath("$.amount", is(200.00)));
        }

        @Test
        void shouldUpdateOnlySavingsAmount() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Emergency Fund");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Emergency Fund");
                updateRequest.put("amount", new BigDecimal("500.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name", is("Emergency Fund")))
                                .andExpect(jsonPath("$.amount", is(500.00)));
        }

        @Test
        void shouldUpdateOnlySavingsBankAccount() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "Test 1", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Test 2", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Vacation Fund");
                createRequest.put("amount", new BigDecimal("300.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Vacation Fund");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.bankAccount.id", is(bankAccount2.getId().toString())))
                                .andExpect(jsonPath("$.bankAccount.name", is("Account 2")));
        }

        @Test
        void shouldReturnUpdatedTimestampAfterSavingsUpdate() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();
                String originalCreatedAt = objectMapper.readTree(createResponse).get("createdAt").asText();

                Thread.sleep(100); // Ensure timestamp difference

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("150.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)))
                                .andExpect(jsonPath("$.updatedAt").exists())
                                .andExpect(jsonPath("$.updatedAt").isNotEmpty());
        }

        @Test
        void shouldRejectUpdateForLockedBudgetSavings() throws Exception {
                // Given - locked budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Original");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("300.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectUpdateForNonExistentSavings() throws Exception {
                // Given - budget without savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                UUID nonExistentId = UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Test");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + nonExistentId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error",
                                                is("Budget savings not found with id: " + nonExistentId)));
        }

        @Test
        void shouldRejectUpdateForNonExistentBudgetSavings() throws Exception {
                // Given - two budgets, savings in one
                createBudget(6, 2024);
                var budget1 = budgetRepository.findAll().get(0);
                budget1.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget1);

                createBudget(7, 2024);
                var budget2 = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 7).findFirst().get();

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget2.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then - try to update with wrong budget ID
                mockMvc.perform(put("/api/budgets/" + budget1.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", is("Budget savings not found with id: " + savingsId)));
        }

        @Test
        void shouldRejectUpdateWithNonExistentBankAccount() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                UUID nonExistentBankAccountId = UUID.randomUUID();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", nonExistentBankAccountId.toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error",
                                                is("Bank account not found with id: " + nonExistentBankAccountId)));
        }

        @Test
        void shouldRejectUpdateWithDeletedBankAccount() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount1 = createBankAccountEntity("Account 1", "Test", new BigDecimal("1000.00"));
                var bankAccount2 = createBankAccountEntity("Account 2", "Test", new BigDecimal("2000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount1.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock budget and soft-delete bank account 2
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);
                mockMvc.perform(delete("/api/bank-accounts/" + bankAccount2.getId()))
                                .andExpect(status().isNoContent());

                // Unlock budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.UNLOCKED);
                budgetRepository.save(budget);

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Updated");
                updateRequest.put("amount", new BigDecimal("200.00"));
                updateRequest.put("bankAccountId", bankAccount2.getId().toString());

                // When & Then - should reject deleted account
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error",
                                                is("Bank account not found with id: " + bankAccount2.getId())));
        }

        @Test
        void shouldRejectUpdateWithNegativeAmount() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Savings");
                updateRequest.put("amount", new BigDecimal("-50.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectUpdateWithBlankName() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("100.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "");
                updateRequest.put("amount", new BigDecimal("100.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // When & Then
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldUpdateSavingsAmountSuccessfully() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("10000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Emergency Fund");
                createRequest.put("amount", new BigDecimal("500.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();
                BigDecimal originalAmount = objectMapper.readTree(createResponse).get("amount").decimalValue();

                // When - update savings amount
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("name", "Emergency Fund");
                updateRequest.put("amount", new BigDecimal("1000.00"));
                updateRequest.put("bankAccountId", bankAccount.getId().toString());

                // Then - verify update was successful
                mockMvc.perform(put("/api/budgets/" + budget.getId() + "/savings/" + savingsId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(savingsId)))
                                .andExpect(jsonPath("$.amount", is(1000.00)));

                // Verify the amount was actually changed
                assert !new BigDecimal("1000.00").equals(originalAmount);
        }

        // ============================================
        // Delete Savings from Budget Tests (Story 20)
        // ============================================

        @Test
        void shouldDeleteSavingsFromUnlockedBudget() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings to Delete");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // When & Then - delete should return 204 No Content
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/savings/" + savingsId))
                                .andExpect(status().isNoContent());
        }

        @Test
        void shouldActuallyRemoveSavingsFromDatabase() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings to Delete");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // When - delete savings
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/savings/" + savingsId))
                                .andExpect(status().isNoContent());

                // Then - verify savings is actually removed
                var remainingSavings = budgetSavingsRepository.findById(java.util.UUID.fromString(savingsId));
                assert remainingSavings.isEmpty();
        }

        @Test
        void shouldRejectDeleteForLockedBudgetSavings() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock the budget
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget);

                // When & Then - should reject with 400
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/savings/" + savingsId))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot modify items in locked budget")));
        }

        @Test
        void shouldRejectDeleteForNonExistentSavings() throws Exception {
                // Given - budget without the savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);

                java.util.UUID nonExistentSavingsId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + budget.getId() + "/savings/" + nonExistentSavingsId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteForNonExistentBudgetSavings() throws Exception {
                // Given - budget with savings
                createBudget(6, 2024);
                var budget = budgetRepository.findAll().get(0);
                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                java.util.UUID nonExistentBudgetId = java.util.UUID.randomUUID();

                // When & Then - should reject with 404
                mockMvc.perform(delete("/api/budgets/" + nonExistentBudgetId + "/savings/" + savingsId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void shouldRejectDeleteWhenSavingsDoesNotBelongToBudget() throws Exception {
                // Given - two budgets, savings belongs to first budget
                createBudget(6, 2024);
                var budget1 = budgetRepository.findAll().get(0);

                var bankAccount = createBankAccountEntity("Account", "Test", new BigDecimal("1000.00"));

                Map<String, Object> createRequest = new HashMap<>();
                createRequest.put("name", "Savings");
                createRequest.put("amount", new BigDecimal("200.00"));
                createRequest.put("bankAccountId", bankAccount.getId().toString());

                String createResponse = mockMvc.perform(post("/api/budgets/" + budget1.getId() + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String savingsId = objectMapper.readTree(createResponse).get("id").asText();

                // Lock budget1 and create budget2
                budget1.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budgetRepository.save(budget1);

                createBudget(7, 2024);
                var budget2 = budgetRepository.findAll().stream()
                                .filter(b -> b.getMonth() == 7).findFirst().get();

                // When & Then - try to delete savings using budget2's ID (savings belongs to
                // budget1)
                mockMvc.perform(delete("/api/budgets/" + budget2.getId() + "/savings/" + savingsId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").exists());
        }

        // Story 22: Delete Unlocked Budget Tests

        @Test
        void shouldDeleteUnlockedBudgetSuccessfully() throws Exception {
                // Given
                Map<String, Object> request = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                // When & Then
                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // Verify budget is actually deleted
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isNotFound());
        }

        @Test
        void shouldCascadeDeleteBudgetIncomeWhenBudgetDeleted() throws Exception {
                // Given - Create budget with income
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity(
                                "Checking", "Primary account", new BigDecimal("1000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                // Add income to budget
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 5000.00);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Verify income exists
                long incomeCountBefore = budgetIncomeRepository.count();

                // When - Delete budget
                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // Then - Verify income was cascade deleted
                long incomeCountAfter = budgetIncomeRepository.count();
                assertThat(incomeCountAfter).isLessThan(incomeCountBefore);
                assertThat(budgetIncomeRepository.findAll()).isEmpty();
        }

        @Test
        void shouldCascadeDeleteBudgetExpensesWhenBudgetDeleted() throws Exception {
                // Given - Create budget with expense
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity(
                                "Checking", "Primary account", new BigDecimal("1000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                // Add expense to budget
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 1500.00);
                expenseRequest.put("isManual", true);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Verify expense exists
                long expenseCountBefore = budgetExpenseRepository.count();

                // When - Delete budget
                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // Then - Verify expense was cascade deleted
                long expenseCountAfter = budgetExpenseRepository.count();
                assertThat(expenseCountAfter).isLessThan(expenseCountBefore);
                assertThat(budgetExpenseRepository.findAll()).isEmpty();
        }

        @Test
        void shouldCascadeDeleteBudgetSavingsWhenBudgetDeleted() throws Exception {
                // Given - Create budget with savings
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity(
                                "Savings", "Savings account", new BigDecimal("5000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                // Add savings to budget
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Verify savings exists
                long savingsCountBefore = budgetSavingsRepository.count();

                // When - Delete budget
                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // Then - Verify savings was cascade deleted
                long savingsCountAfter = budgetSavingsRepository.count();
                assertThat(savingsCountAfter).isLessThan(savingsCountBefore);
                assertThat(budgetSavingsRepository.findAll()).isEmpty();
        }

        @Test
        void shouldCascadeDeleteAllBudgetItemsWhenBudgetDeleted() throws Exception {
                // Given - Create budget with income, expenses, and savings
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity(
                                "Checking", "Primary account", new BigDecimal("10000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 5000.00);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 1500.00);
                expenseRequest.put("isManual", true);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);

                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Verify all items exist
                long incomeCountBefore = budgetIncomeRepository.count();
                long expenseCountBefore = budgetExpenseRepository.count();
                long savingsCountBefore = budgetSavingsRepository.count();

                // When - Delete budget
                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // Then - Verify all items were cascade deleted
                assertThat(budgetIncomeRepository.count()).isLessThan(incomeCountBefore);
                assertThat(budgetExpenseRepository.count()).isLessThan(expenseCountBefore);
                assertThat(budgetSavingsRepository.count()).isLessThan(savingsCountBefore);
                assertThat(budgetIncomeRepository.findAll()).isEmpty();
                assertThat(budgetExpenseRepository.findAll()).isEmpty();
                assertThat(budgetSavingsRepository.findAll()).isEmpty();
        }

        @Test
        void shouldAllowCreatingSameBudgetAfterDeletion() throws Exception {
                // Given - Create and delete budget for June 2024
                Map<String, Object> request = createBudgetRequest(6, 2024);
                String createResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(createResponse).get("id").asText();

                mockMvc.perform(delete("/api/budgets/" + budgetId))
                                .andExpect(status().isNoContent());

                // When - Create new budget for same month/year
                Map<String, Object> newRequest = createBudgetRequest(6, 2024);

                // Then - Should succeed
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newRequest)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.month", is(6)))
                                .andExpect(jsonPath("$.year", is(2024)))
                                .andExpect(jsonPath("$.status", is("UNLOCKED")));
        }

        @Test
        void shouldRejectDeleteLockedBudget() throws Exception {
                // Given - Create budget and lock it
                org.example.axelnyman.main.domain.model.Budget budget = new org.example.axelnyman.main.domain.model.Budget(
                                6, 2024);
                budget.setStatus(org.example.axelnyman.main.domain.model.BudgetStatus.LOCKED);
                budget.setLockedAt(java.time.LocalDateTime.now());
                budget = budgetRepository.save(budget);

                // When & Then - Attempt to delete locked budget
                mockMvc.perform(delete("/api/budgets/" + budget.getId()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Cannot delete locked budget. Unlock it first.")));
        }

        @Test
        void shouldRejectDeleteNonExistentBudget() throws Exception {
                // Given
                UUID nonExistentId = UUID.randomUUID();

                // When & Then
                mockMvc.perform(delete("/api/budgets/" + nonExistentId))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", is("Budget not found")));
        }

        // Story 24: Lock Budget Tests

        @Test
        void shouldLockBudgetWhenBalanceIsZero() throws Exception {
                // Given - Create budget with balanced income, expenses, and savings
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income: 3000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 2000.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings: 1000.00
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify budget is locked
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(budgetId)))
                                .andExpect(jsonPath("$.status", is("LOCKED")))
                                .andExpect(jsonPath("$.lockedAt").exists())
                                .andExpect(jsonPath("$.totals.income", is(3000.00)))
                                .andExpect(jsonPath("$.totals.expenses", is(2000.00)))
                                .andExpect(jsonPath("$.totals.savings", is(1000.00)))
                                .andExpect(jsonPath("$.totals.balance", is(0.00)));
        }

        @Test
        void shouldRejectLockWhenBalanceIsPositive() throws Exception {
                // Given - Create budget with positive balance
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income: 3000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 2000.00 (leaving 1000.00 positive balance)
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // When & Then - Attempt to lock should fail
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error",
                                                is("Budget must have zero balance. Current balance: 1000.00")));
        }

        @Test
        void shouldRejectLockWhenBalanceIsNegative() throws Exception {
                // Given - Create budget with negative balance
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income: 2000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 2000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 2500.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2500.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings: 500.00 (total outflow 3000, income 2000, balance -1000)
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 500.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When & Then - Attempt to lock should fail
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error",
                                                is("Budget must have zero balance. Current balance: -1000.00")));
        }

        @Test
        void shouldRejectLockWhenBudgetAlreadyLocked() throws Exception {
                // Given - Create and lock a budget
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Lock the budget (zero balance is acceptable)
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // When & Then - Attempt to lock again should fail
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error", is("Budget is already locked")));
        }

        @Test
        void shouldUpdateRecurringExpenseLastUsedDateOnLock() throws Exception {
                // Given - Create budget with expense linked to recurring expense template
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account and recurring expense
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));
                org.example.axelnyman.main.domain.model.RecurringExpense recurringExpense = createRecurringExpenseEntity(
                                "Netflix", new BigDecimal("15.99"));

                // Add income to balance the expense
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 15.99);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense linked to recurring expense template
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Netflix");
                expenseRequest.put("amount", 15.99);
                expenseRequest.put("recurringExpenseId", recurringExpense.getId().toString());
                expenseRequest.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify recurring expense's lastUsedDate and lastUsedBudgetId are
                // updated
                org.example.axelnyman.main.domain.model.RecurringExpense updatedExpense = recurringExpenseRepository
                                .findById(recurringExpense.getId()).orElseThrow();
                assertThat(updatedExpense.getLastUsedDate()).isNotNull();
                assertThat(updatedExpense.getLastUsedBudgetId()).isEqualTo(UUID.fromString(budgetId));
        }

        @Test
        void shouldUpdateMultipleRecurringExpensesOnLock() throws Exception {
                // Given - Create budget with 3 expenses, 2 linked to different recurring
                // templates
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account and recurring expenses
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));
                org.example.axelnyman.main.domain.model.RecurringExpense netflix = createRecurringExpenseEntity(
                                "Netflix", new BigDecimal("15.99"));
                org.example.axelnyman.main.domain.model.RecurringExpense spotify = createRecurringExpenseEntity(
                                "Spotify", new BigDecimal("9.99"));

                // Add income to balance all expenses (15.99 + 9.99 + 50.00 = 75.98)
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 75.98);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense 1 (linked to Netflix)
                Map<String, Object> expense1Request = new HashMap<>();
                expense1Request.put("bankAccountId", account.getId().toString());
                expense1Request.put("name", "Netflix");
                expense1Request.put("amount", 15.99);
                expense1Request.put("recurringExpenseId", netflix.getId().toString());
                expense1Request.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense1Request)))
                                .andExpect(status().isCreated());

                // Add expense 2 (linked to Spotify)
                Map<String, Object> expense2Request = new HashMap<>();
                expense2Request.put("bankAccountId", account.getId().toString());
                expense2Request.put("name", "Spotify");
                expense2Request.put("amount", 9.99);
                expense2Request.put("recurringExpenseId", spotify.getId().toString());
                expense2Request.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense2Request)))
                                .andExpect(status().isCreated());

                // Add expense 3 (manual, no recurring expense)
                Map<String, Object> expense3Request = new HashMap<>();
                expense3Request.put("bankAccountId", account.getId().toString());
                expense3Request.put("name", "One-time purchase");
                expense3Request.put("amount", 50.00);
                expense3Request.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense3Request)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify both recurring expenses are updated
                org.example.axelnyman.main.domain.model.RecurringExpense updatedNetflix = recurringExpenseRepository
                                .findById(netflix.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.RecurringExpense updatedSpotify = recurringExpenseRepository
                                .findById(spotify.getId()).orElseThrow();

                assertThat(updatedNetflix.getLastUsedDate()).isNotNull();
                assertThat(updatedNetflix.getLastUsedBudgetId()).isEqualTo(UUID.fromString(budgetId));
                assertThat(updatedSpotify.getLastUsedDate()).isNotNull();
                assertThat(updatedSpotify.getLastUsedBudgetId()).isEqualTo(UUID.fromString(budgetId));
        }

        @Test
        void shouldHandleLatestLockWinsForSameRecurringExpense() throws Exception {
                // Given - Create first budget with recurring expense
                Map<String, Object> budget1Request = createBudgetRequest(6, 2024);
                String budget1Response = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budget1Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budget1Id = objectMapper.readTree(budget1Response).get("id").asText();

                // Create bank account and recurring expense
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));
                org.example.axelnyman.main.domain.model.RecurringExpense netflix = createRecurringExpenseEntity(
                                "Netflix", new BigDecimal("15.99"));

                // Add income to first budget to balance the expense
                Map<String, Object> income1Request = new HashMap<>();
                income1Request.put("bankAccountId", account.getId().toString());
                income1Request.put("name", "Salary");
                income1Request.put("amount", 15.99);
                mockMvc.perform(post("/api/budgets/" + budget1Id + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(income1Request)))
                                .andExpect(status().isCreated());

                // Add expense to first budget
                Map<String, Object> expense1Request = new HashMap<>();
                expense1Request.put("bankAccountId", account.getId().toString());
                expense1Request.put("name", "Netflix");
                expense1Request.put("amount", 15.99);
                expense1Request.put("recurringExpenseId", netflix.getId().toString());
                expense1Request.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budget1Id + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense1Request)))
                                .andExpect(status().isCreated());

                // Lock first budget
                mockMvc.perform(put("/api/budgets/" + budget1Id + "/lock"))
                                .andExpect(status().isOk());

                // Create second budget
                Map<String, Object> budget2Request = createBudgetRequest(7, 2024);
                String budget2Response = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budget2Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budget2Id = objectMapper.readTree(budget2Response).get("id").asText();

                // Add income to second budget to balance the expense
                Map<String, Object> income2Request = new HashMap<>();
                income2Request.put("bankAccountId", account.getId().toString());
                income2Request.put("name", "Salary");
                income2Request.put("amount", 15.99);
                mockMvc.perform(post("/api/budgets/" + budget2Id + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(income2Request)))
                                .andExpect(status().isCreated());

                // Add same recurring expense to second budget
                Map<String, Object> expense2Request = new HashMap<>();
                expense2Request.put("bankAccountId", account.getId().toString());
                expense2Request.put("name", "Netflix");
                expense2Request.put("amount", 15.99);
                expense2Request.put("recurringExpenseId", netflix.getId().toString());
                expense2Request.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budget2Id + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense2Request)))
                                .andExpect(status().isCreated());

                // When - Lock second budget
                mockMvc.perform(put("/api/budgets/" + budget2Id + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify recurring expense tracks the second budget's lock
                org.example.axelnyman.main.domain.model.RecurringExpense updatedNetflix = recurringExpenseRepository
                                .findById(netflix.getId()).orElseThrow();
                assertThat(updatedNetflix.getLastUsedBudgetId()).isEqualTo(UUID.fromString(budget2Id));
        }

        @Test
        void shouldNotUpdateRecurringExpenseForManualExpenses() throws Exception {
                // Given - Create budget with only manual expense (no recurringExpenseId)
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income to balance the expense
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add manual expense (no recurringExpenseId)
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "One-time expense");
                expenseRequest.put("amount", 100.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // When & Then - Lock should succeed without errors
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("LOCKED")));
        }

        @Test
        void shouldRejectLockForNonExistentBudget() throws Exception {
                // Given
                UUID nonExistentId = UUID.randomUUID();

                // When & Then
                mockMvc.perform(put("/api/budgets/" + nonExistentId + "/lock"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error", is("Budget not found")));
        }

        // ========== Story 28: Mark Todo Item Complete ==========

        @Test
        void shouldMarkTodoItemAsCompleted() throws Exception {
                // Given - Create locked budget with todo list
                Map<String, Object> budgetRequest = createBudgetRequest(11, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add manual expense
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Lock budget (generates todo list)
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Get todo list to find an item
                String todoListResponse = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String todoItemId = objectMapper.readTree(todoListResponse)
                                .get("items").get(0).get("id").asText();

                // When - Mark item as completed
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("status", "COMPLETED");

                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(todoItemId))
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.completedAt").exists())
                                .andExpect(jsonPath("$.completedAt").isNotEmpty());
        }

        @Test
        void shouldMarkTodoItemAsPending() throws Exception {
                // Given - Create locked budget with completed todo item
                Map<String, Object> budgetRequest = createBudgetRequest(12, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking2",
                                "Main account", new BigDecimal("5000.00"));

                // Add income, expense, and savings
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Lock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Get todo item
                String todoListResponse = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String todoItemId = objectMapper.readTree(todoListResponse)
                                .get("items").get(0).get("id").asText();

                // Mark as completed first
                Map<String, Object> completeRequest = new HashMap<>();
                completeRequest.put("status", "COMPLETED");
                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(completeRequest)))
                                .andExpect(status().isOk());

                // When - Mark item as pending
                Map<String, Object> pendingRequest = new HashMap<>();
                pendingRequest.put("status", "PENDING");

                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pendingRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(todoItemId))
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.completedAt").doesNotExist());
        }

        @Test
        void shouldToggleTodoItemStatusMultipleTimes() throws Exception {
                // Given - Create locked budget with todo item
                Map<String, Object> budgetRequest = createBudgetRequest(1, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking3",
                                "Main account", new BigDecimal("5000.00"));

                // Add income, expense, and savings
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                String todoListResponse = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String todoItemId = objectMapper.readTree(todoListResponse)
                                .get("items").get(0).get("id").asText();

                // When/Then - Toggle: PENDING → COMPLETED
                Map<String, Object> completeRequest = new HashMap<>();
                completeRequest.put("status", "COMPLETED");
                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(completeRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.completedAt").exists());

                // Toggle: COMPLETED → PENDING
                Map<String, Object> pendingRequest = new HashMap<>();
                pendingRequest.put("status", "PENDING");
                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(pendingRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.completedAt").doesNotExist());

                // Toggle: PENDING → COMPLETED again
                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(completeRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status").value("COMPLETED"))
                                .andExpect(jsonPath("$.completedAt").exists());
        }

        @Test
        void shouldReturn404WhenTodoItemNotFound() throws Exception {
                // Given - Create locked budget
                Map<String, Object> budgetRequest = createBudgetRequest(2, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create a non-existent todo item ID
                UUID nonExistentTodoItemId = UUID.randomUUID();

                // When/Then - Try to update non-existent todo item
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("status", "COMPLETED");

                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + nonExistentTodoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("Todo item not found"));
        }

        @Test
        void shouldRejectUpdateWhenBudgetNotLocked() throws Exception {
                // Given - Create unlocked budget with todo list manually created
                Map<String, Object> budgetRequest = createBudgetRequest(3, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking4",
                                "Main account", new BigDecimal("5000.00"));

                // Add income, expense, and savings to balance the budget
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Lock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Get todo item
                String todoListResponse = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String todoItemId = objectMapper.readTree(todoListResponse)
                                .get("items").get(0).get("id").asText();

                // Unlock budget (deletes todo list and items)
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk());

                // When/Then - Try to update deleted todo item, expect 404 since item no longer exists
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("status", "COMPLETED");

                mockMvc.perform(put("/api/budgets/" + budgetId + "/todo-list/items/" + todoItemId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("Todo item not found"));
        }

        @Test
        void shouldValidateTodoItemBelongsToBudget() throws Exception {
                // Given - Create two locked budgets with separate todo lists
                org.example.axelnyman.main.domain.model.BankAccount account1 = createBankAccountEntity("Checking5",
                                "Main", new BigDecimal("5000.00"));
                org.example.axelnyman.main.domain.model.BankAccount account2 = createBankAccountEntity("Checking6",
                                "Main", new BigDecimal("5000.00"));

                // Budget 1
                Map<String, Object> budget1Request = createBudgetRequest(4, 2025);
                String budget1Response = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budget1Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budget1Id = objectMapper.readTree(budget1Response).get("id").asText();

                Map<String, Object> income1Request = new HashMap<>();
                income1Request.put("bankAccountId", account1.getId().toString());
                income1Request.put("name", "Salary");
                income1Request.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budget1Id + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(income1Request)))
                                .andExpect(status().isCreated());

                Map<String, Object> expense1Request = new HashMap<>();
                expense1Request.put("bankAccountId", account1.getId().toString());
                expense1Request.put("name", "Rent");
                expense1Request.put("amount", 2000.00);
                expense1Request.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budget1Id + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense1Request)))
                                .andExpect(status().isCreated());

                Map<String, Object> savings1Request = new HashMap<>();
                savings1Request.put("bankAccountId", account1.getId().toString());
                savings1Request.put("name", "Emergency");
                savings1Request.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budget1Id + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savings1Request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(put("/api/budgets/" + budget1Id + "/lock"))
                                .andExpect(status().isOk());

                // Budget 2
                Map<String, Object> budget2Request = createBudgetRequest(5, 2025);
                String budget2Response = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budget2Request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budget2Id = objectMapper.readTree(budget2Response).get("id").asText();

                Map<String, Object> income2Request = new HashMap<>();
                income2Request.put("bankAccountId", account2.getId().toString());
                income2Request.put("name", "Salary");
                income2Request.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budget2Id + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(income2Request)))
                                .andExpect(status().isCreated());

                Map<String, Object> expense2Request = new HashMap<>();
                expense2Request.put("bankAccountId", account2.getId().toString());
                expense2Request.put("name", "Rent");
                expense2Request.put("amount", 2000.00);
                expense2Request.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budget2Id + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expense2Request)))
                                .andExpect(status().isCreated());

                Map<String, Object> savings2Request = new HashMap<>();
                savings2Request.put("bankAccountId", account2.getId().toString());
                savings2Request.put("name", "Emergency");
                savings2Request.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budget2Id + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savings2Request)))
                                .andExpect(status().isCreated());

                mockMvc.perform(put("/api/budgets/" + budget2Id + "/lock"))
                                .andExpect(status().isOk());

                // Get todo item from budget 1
                String todoList1Response = mockMvc.perform(get("/api/budgets/" + budget1Id + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                String todoItem1Id = objectMapper.readTree(todoList1Response)
                                .get("items").get(0).get("id").asText();

                // When/Then - Try to update budget 1's todo item using budget 2's ID
                Map<String, Object> updateRequest = new HashMap<>();
                updateRequest.put("status", "COMPLETED");

                mockMvc.perform(put("/api/budgets/" + budget2Id + "/todo-list/items/" + todoItem1Id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Todo item does not belong to this budget"));
        }

        // Helper method to create budget request
        private Map<String, Object> createBudgetRequest(Integer month, Integer year) {
                Map<String, Object> request = new HashMap<>();
                request.put("month", month);
                request.put("year", year);
                return request;
        }

        // Helper method to create budget via API
        private void createBudget(Integer month, Integer year) throws Exception {
                Map<String, Object> request = createBudgetRequest(month, year);
                mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        // Helper method to create bank account entity directly
        private org.example.axelnyman.main.domain.model.BankAccount createBankAccountEntity(String name,
                        String description, BigDecimal initialBalance) {
                org.example.axelnyman.main.domain.model.BankAccount account = new org.example.axelnyman.main.domain.model.BankAccount();
                account.setName(name);
                account.setDescription(description);
                account.setCurrentBalance(initialBalance);
                return bankAccountRepository.save(account);
        }

        // Helper method to create recurring expense entity directly
        private org.example.axelnyman.main.domain.model.RecurringExpense createRecurringExpenseEntity(String name,
                        BigDecimal amount) {
                org.example.axelnyman.main.domain.model.RecurringExpense expense = new org.example.axelnyman.main.domain.model.RecurringExpense(
                                name,
                                amount,
                                org.example.axelnyman.main.domain.model.RecurrenceInterval.MONTHLY,
                                false);
                return recurringExpenseRepository.save(expense);
        }

        // ========== Story 26: Update Account Balances on Lock ==========

        @Test
        void shouldUpdateBalanceOnLockWithSingleAccount() throws Exception {
                // Given - Create budget with income, expenses, and savings to single account
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account with initial balance
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("1000.00"));
                BigDecimal initialBalance = account.getCurrentBalance();

                // Add income: 500.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 500.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 300.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 300.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings: 200.00 (500 - 300 - 200 = 0)
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 200.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify account balance increased by savings amount only
                org.example.axelnyman.main.domain.model.BankAccount updatedAccount = bankAccountRepository
                                .findById(account.getId()).orElseThrow();
                BigDecimal expectedBalance = initialBalance.add(new BigDecimal("200.00"));
                assertThat(updatedAccount.getCurrentBalance()).isEqualByComparingTo(expectedBalance);
        }

        @Test
        void shouldUpdateBalancesWithMultipleAccounts() throws Exception {
                // Given - Create budget with savings distributed across 3 accounts
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create three bank accounts with initial balances
                org.example.axelnyman.main.domain.model.BankAccount accountA = createBankAccountEntity("Account A",
                                "First account", new BigDecimal("1000.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountB = createBankAccountEntity("Account B",
                                "Second account", new BigDecimal("2000.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountC = createBankAccountEntity("Account C",
                                "Third account", new BigDecimal("3000.00"));

                BigDecimal initialBalanceA = accountA.getCurrentBalance();
                BigDecimal initialBalanceB = accountB.getCurrentBalance();
                BigDecimal initialBalanceC = accountC.getCurrentBalance();

                // Add income from Account A: 600.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountA.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 600.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense from Account B: 200.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", accountB.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 200.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings to Account A: 100.00
                Map<String, Object> savingsRequestA = new HashMap<>();
                savingsRequestA.put("bankAccountId", accountA.getId().toString());
                savingsRequestA.put("name", "Emergency Fund");
                savingsRequestA.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequestA)))
                                .andExpect(status().isCreated());

                // Add savings to Account B: 150.00
                Map<String, Object> savingsRequestB = new HashMap<>();
                savingsRequestB.put("bankAccountId", accountB.getId().toString());
                savingsRequestB.put("name", "Vacation");
                savingsRequestB.put("amount", 150.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequestB)))
                                .andExpect(status().isCreated());

                // Add savings to Account C: 150.00 (600 - 200 - 100 - 150 - 150 = 0)
                Map<String, Object> savingsRequestC = new HashMap<>();
                savingsRequestC.put("bankAccountId", accountC.getId().toString());
                savingsRequestC.put("name", "Investment");
                savingsRequestC.put("amount", 150.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequestC)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify each account balance increased by its respective savings amount
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountA = bankAccountRepository
                                .findById(accountA.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountB = bankAccountRepository
                                .findById(accountB.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountC = bankAccountRepository
                                .findById(accountC.getId()).orElseThrow();

                assertThat(updatedAccountA.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceA.add(new BigDecimal("100.00")));
                assertThat(updatedAccountB.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceB.add(new BigDecimal("150.00")));
                assertThat(updatedAccountC.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceC.add(new BigDecimal("150.00")));
        }

        @Test
        void shouldSumMultipleSavingsItemsToSameAccount() throws Exception {
                // Given - Create budget with multiple savings items to the same account
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Savings",
                                "Savings account", new BigDecimal("5000.00"));
                BigDecimal initialBalance = account.getCurrentBalance();

                // Add income: 1000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 400.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Bills");
                expenseRequest.put("amount", 400.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add three savings items to the same account
                Map<String, Object> savings1 = new HashMap<>();
                savings1.put("bankAccountId", account.getId().toString());
                savings1.put("name", "Emergency Fund");
                savings1.put("amount", 200.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savings1)))
                                .andExpect(status().isCreated());

                Map<String, Object> savings2 = new HashMap<>();
                savings2.put("bankAccountId", account.getId().toString());
                savings2.put("name", "Vacation Fund");
                savings2.put("amount", 250.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savings2)))
                                .andExpect(status().isCreated());

                Map<String, Object> savings3 = new HashMap<>();
                savings3.put("bankAccountId", account.getId().toString());
                savings3.put("name", "Investment");
                savings3.put("amount", 150.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savings3)))
                                .andExpect(status().isCreated());
                // Total: 1000 - 400 - 200 - 250 - 150 = 0

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify account balance increased by sum of all savings (200 + 250 +
                // 150 = 600)
                org.example.axelnyman.main.domain.model.BankAccount updatedAccount = bankAccountRepository
                                .findById(account.getId()).orElseThrow();
                BigDecimal expectedBalance = initialBalance.add(new BigDecimal("600.00"));
                assertThat(updatedAccount.getCurrentBalance()).isEqualByComparingTo(expectedBalance);
        }

        @Test
        void shouldNotAffectAccountsWithoutSavings() throws Exception {
                // Given - Create budget with 3 accounts, but savings only on 2 of them
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create three bank accounts
                org.example.axelnyman.main.domain.model.BankAccount accountA = createBankAccountEntity("Account A",
                                "Has income and savings", new BigDecimal("1000.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountB = createBankAccountEntity("Account B",
                                "Has savings only", new BigDecimal("2000.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountC = createBankAccountEntity("Account C",
                                "No savings", new BigDecimal("3000.00"));

                BigDecimal initialBalanceA = accountA.getCurrentBalance();
                BigDecimal initialBalanceB = accountB.getCurrentBalance();
                BigDecimal initialBalanceC = accountC.getCurrentBalance();

                // Add income from Account A: 400.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountA.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 400.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense from Account C: 200.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", accountC.getId().toString());
                expenseRequest.put("name", "Bills");
                expenseRequest.put("amount", 200.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings to Account A: 100.00
                Map<String, Object> savingsA = new HashMap<>();
                savingsA.put("bankAccountId", accountA.getId().toString());
                savingsA.put("name", "Emergency Fund");
                savingsA.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsA)))
                                .andExpect(status().isCreated());

                // Add savings to Account B: 100.00
                Map<String, Object> savingsB = new HashMap<>();
                savingsB.put("bankAccountId", accountB.getId().toString());
                savingsB.put("name", "Investment");
                savingsB.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsB)))
                                .andExpect(status().isCreated());
                // Total: 400 - 200 - 100 - 100 = 0
                // Account C has no savings, should not be affected

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify only accounts with savings are updated
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountA = bankAccountRepository
                                .findById(accountA.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountB = bankAccountRepository
                                .findById(accountB.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountC = bankAccountRepository
                                .findById(accountC.getId()).orElseThrow();

                // Accounts A and B should have increased balances
                assertThat(updatedAccountA.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceA.add(new BigDecimal("100.00")));
                assertThat(updatedAccountB.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceB.add(new BigDecimal("100.00")));
                // Account C should remain unchanged
                assertThat(updatedAccountC.getCurrentBalance())
                                .isEqualByComparingTo(initialBalanceC);
        }

        @Test
        void shouldCreateBalanceHistoryEntriesWithCorrectMetadata() throws Exception {
                // Given - Create budget with savings to multiple accounts
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create two bank accounts
                org.example.axelnyman.main.domain.model.BankAccount accountA = createBankAccountEntity("Account A",
                                "First account", new BigDecimal("1000.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountB = createBankAccountEntity("Account B",
                                "Second account", new BigDecimal("2000.00"));

                // Add income: 500.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountA.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 500.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 200.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", accountA.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 200.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings to Account A: 150.00
                Map<String, Object> savingsA = new HashMap<>();
                savingsA.put("bankAccountId", accountA.getId().toString());
                savingsA.put("name", "Emergency Fund");
                savingsA.put("amount", 150.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsA)))
                                .andExpect(status().isCreated());

                // Add savings to Account B: 150.00
                Map<String, Object> savingsB = new HashMap<>();
                savingsB.put("bankAccountId", accountB.getId().toString());
                savingsB.put("name", "Investment");
                savingsB.put("amount", 150.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsB)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify BalanceHistory entries were created with correct metadata
                java.util.List<org.example.axelnyman.main.domain.model.BalanceHistory> historyEntries = balanceHistoryRepository
                                .findAll();

                // Filter to AUTOMATIC entries (initial balance entries will be MANUAL)
                java.util.List<org.example.axelnyman.main.domain.model.BalanceHistory> automaticEntries = historyEntries
                                .stream()
                                .filter(h -> h.getSource() == org.example.axelnyman.main.domain.model.BalanceHistorySource.AUTOMATIC)
                                .toList();

                // Should have 2 automatic entries (one per account with savings)
                assertThat(automaticEntries).hasSize(2);

                // Verify metadata for Account A
                org.example.axelnyman.main.domain.model.BalanceHistory historyA = automaticEntries.stream()
                                .filter(h -> h.getBankAccountId().equals(accountA.getId()))
                                .findFirst()
                                .orElseThrow();

                assertThat(historyA.getSource())
                                .isEqualTo(org.example.axelnyman.main.domain.model.BalanceHistorySource.AUTOMATIC);
                assertThat(historyA.getBudgetId()).isEqualTo(UUID.fromString(budgetId));
                assertThat(historyA.getChangeAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
                assertThat(historyA.getBalance()).isEqualByComparingTo(new BigDecimal("1150.00"));
                assertThat(historyA.getComment()).isEqualTo("Budget lock for 6/2024");
                assertThat(historyA.getChangeDate()).isNotNull();

                // Verify metadata for Account B
                org.example.axelnyman.main.domain.model.BalanceHistory historyB = automaticEntries.stream()
                                .filter(h -> h.getBankAccountId().equals(accountB.getId()))
                                .findFirst()
                                .orElseThrow();

                assertThat(historyB.getSource())
                                .isEqualTo(org.example.axelnyman.main.domain.model.BalanceHistorySource.AUTOMATIC);
                assertThat(historyB.getBudgetId()).isEqualTo(UUID.fromString(budgetId));
                assertThat(historyB.getChangeAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
                assertThat(historyB.getBalance()).isEqualByComparingTo(new BigDecimal("2150.00"));
                assertThat(historyB.getComment()).isEqualTo("Budget lock for 6/2024");
                assertThat(historyB.getChangeDate()).isNotNull();
        }

        @Test
        void shouldMatchStoryExampleScenario() throws Exception {
                // Given - Exact scenario from Story 26
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create three bank accounts with starting balances
                org.example.axelnyman.main.domain.model.BankAccount accountA = createBankAccountEntity("Account A",
                                "Account A", new BigDecimal("500.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountB = createBankAccountEntity("Account B",
                                "Account B", new BigDecimal("300.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountC = createBankAccountEntity("Account C",
                                "Account C", new BigDecimal("1000.00"));

                // Income: Account A: $500
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountA.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 500.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Expenses: Account B: $100
                Map<String, Object> expenseB = new HashMap<>();
                expenseB.put("bankAccountId", accountB.getId().toString());
                expenseB.put("name", "Expense B");
                expenseB.put("amount", 100.00);
                expenseB.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseB)))
                                .andExpect(status().isCreated());

                // Expenses: Account C: $100
                Map<String, Object> expenseC = new HashMap<>();
                expenseC.put("bankAccountId", accountC.getId().toString());
                expenseC.put("name", "Expense C");
                expenseC.put("amount", 100.00);
                expenseC.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseC)))
                                .andExpect(status().isCreated());

                // Savings: Account A: $100
                Map<String, Object> savingsA = new HashMap<>();
                savingsA.put("bankAccountId", accountA.getId().toString());
                savingsA.put("name", "Savings A");
                savingsA.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsA)))
                                .andExpect(status().isCreated());

                // Savings: Account B: $100
                Map<String, Object> savingsB = new HashMap<>();
                savingsB.put("bankAccountId", accountB.getId().toString());
                savingsB.put("name", "Savings B");
                savingsB.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsB)))
                                .andExpect(status().isCreated());

                // Savings: Account C: $100
                Map<String, Object> savingsC = new HashMap<>();
                savingsC.put("bankAccountId", accountC.getId().toString());
                savingsC.put("name", "Savings C");
                savingsC.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsC)))
                                .andExpect(status().isCreated());
                // Budget Balance: 500 - 100 - 100 - 100 - 100 - 100 = 0 ✓

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify balances match story example
                // Account A: $500 + $100 = $600
                // Account B: $300 + $100 = $400
                // Account C: $1000 + $100 = $1100
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountA = bankAccountRepository
                                .findById(accountA.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountB = bankAccountRepository
                                .findById(accountB.getId()).orElseThrow();
                org.example.axelnyman.main.domain.model.BankAccount updatedAccountC = bankAccountRepository
                                .findById(accountC.getId()).orElseThrow();

                assertThat(updatedAccountA.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
                assertThat(updatedAccountB.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("400.00"));
                assertThat(updatedAccountC.getCurrentBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        }

        // ========== Story 25: Generate Todo List on Lock ==========

        @Test
        void shouldGenerateTodoListWhenBudgetIsLocked() throws Exception {
                // Given - Create budget with balanced income, expenses (manual + auto), and
                // savings
                Map<String, Object> budgetRequest = createBudgetRequest(6, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create two bank accounts
                org.example.axelnyman.main.domain.model.BankAccount checkingAccount = createBankAccountEntity(
                                "Checking", "Main account", new BigDecimal("5000.00"));
                org.example.axelnyman.main.domain.model.BankAccount savingsAccount = createBankAccountEntity("Savings",
                                "Savings account", new BigDecimal("3000.00"));

                // Add income to checking: 3000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", checkingAccount.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add manual expense to checking: 1500.00
                Map<String, Object> manualExpenseRequest = new HashMap<>();
                manualExpenseRequest.put("bankAccountId", checkingAccount.getId().toString());
                manualExpenseRequest.put("name", "Rent");
                manualExpenseRequest.put("amount", 1500.00);
                manualExpenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(manualExpenseRequest)))
                                .andExpect(status().isCreated());

                // Add automatic expense to savings: 500.00
                Map<String, Object> autoExpenseRequest = new HashMap<>();
                autoExpenseRequest.put("bankAccountId", savingsAccount.getId().toString());
                autoExpenseRequest.put("name", "Insurance");
                autoExpenseRequest.put("amount", 500.00);
                autoExpenseRequest.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(autoExpenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings to checking: 1000.00
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", checkingAccount.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When - Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Fetch todo list and verify it was generated
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").exists())
                                .andExpect(jsonPath("$.budgetId").value(budgetId))
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items.length()").value(2)) // 1 PAYMENT + 1 TRANSFER
                                .andExpect(jsonPath("$.summary.totalItems").value(2))
                                .andExpect(jsonPath("$.summary.pendingItems").value(2))
                                .andExpect(jsonPath("$.summary.completedItems").value(0));
        }

        @Test
        void shouldIncludeCorrectAccountDetailsInTodoItems() throws Exception {
                // Given - Create budget with income, manual expense, and savings requiring
                // transfer
                Map<String, Object> budgetRequest = createBudgetRequest(7, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create accounts
                org.example.axelnyman.main.domain.model.BankAccount checkingAccount = createBankAccountEntity(
                                "Checking", "Main", new BigDecimal("1000.00"));
                org.example.axelnyman.main.domain.model.BankAccount savingsAccount = createBankAccountEntity("Savings",
                                "Savings", new BigDecimal("500.00"));

                // Income to checking: 2000
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", checkingAccount.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 2000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Manual expense from checking: 1000
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", checkingAccount.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 1000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Savings from savings account: 1000 (requires transfer from checking)
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", savingsAccount.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // When - Lock budget and fetch todo list
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify TRANSFER item has both fromAccount and toAccount
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.type=='TRANSFER')].fromAccount.id").exists())
                                .andExpect(jsonPath("$.items[?(@.type=='TRANSFER')].fromAccount.name")
                                                .value("Checking"))
                                .andExpect(jsonPath("$.items[?(@.type=='TRANSFER')].toAccount.id").exists())
                                .andExpect(jsonPath("$.items[?(@.type=='TRANSFER')].toAccount.name").value("Savings"))
                                .andExpect(jsonPath("$.items[?(@.type=='TRANSFER')].amount").value(1000.00));

                // Verify PAYMENT item has fromAccount only (toAccount should be null)
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items[?(@.type=='PAYMENT')].fromAccount.id").exists())
                                .andExpect(jsonPath("$.items[?(@.type=='PAYMENT')].fromAccount.name").value("Checking"))
                                .andExpect(jsonPath("$.items[?(@.type=='PAYMENT')].toAccount[0]").doesNotExist())
                                .andExpect(jsonPath("$.items[?(@.type=='PAYMENT')].amount").value(1000.00));
        }

        @Test
        void shouldCalculateTodoSummaryCorrectly() throws Exception {
                // Given - Create budget with multiple manual expenses
                Map<String, Object> budgetRequest = createBudgetRequest(8, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main", new BigDecimal("5000.00"));

                // Income: 3000
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Three manual expenses totaling 3000
                for (int i = 1; i <= 3; i++) {
                        Map<String, Object> expenseRequest = new HashMap<>();
                        expenseRequest.put("bankAccountId", account.getId().toString());
                        expenseRequest.put("name", "Expense " + i);
                        expenseRequest.put("amount", 1000.00);
                        expenseRequest.put("isManual", true);
                        mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(expenseRequest)))
                                        .andExpect(status().isCreated());
                }

                // When - Lock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Then - Verify summary shows 3 total items, all pending
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.summary.totalItems").value(3))
                                .andExpect(jsonPath("$.summary.pendingItems").value(3))
                                .andExpect(jsonPath("$.summary.completedItems").value(0))
                                .andExpect(jsonPath("$.items").isArray())
                                .andExpect(jsonPath("$.items.length()").value(3))
                                .andExpect(jsonPath("$.items[0].status").value("PENDING"))
                                .andExpect(jsonPath("$.items[1].status").value("PENDING"))
                                .andExpect(jsonPath("$.items[2].status").value("PENDING"));
        }

        @Test
        void shouldReturn404WhenTodoListNotFound() throws Exception {
                // Given - Create an unlocked budget (no todo list generated)
                Map<String, Object> budgetRequest = createBudgetRequest(9, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // When/Then - Try to fetch todo list, expect 404
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("Todo list not found for this budget"));
        }

        // Story 27: Unlock Budget Tests

        @Test
        void shouldUnlockMostRecentBudgetWhenLocked() throws Exception {
                // Given - Create budget with balanced income, expenses, and savings
                Map<String, Object> budgetRequest = createBudgetRequest(10, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("Checking",
                                "Main account", new BigDecimal("5000.00"));

                // Add income: 3000.00
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Salary");
                incomeRequest.put("amount", 3000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense: 2000.00
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 2000.00);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings: 1000.00
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", account.getId().toString());
                savingsRequest.put("name", "Emergency Fund");
                savingsRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());

                // Lock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("LOCKED")))
                                .andExpect(jsonPath("$.lockedAt").exists());

                // When - Unlock the budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id", is(budgetId)))
                                .andExpect(jsonPath("$.status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.lockedAt").doesNotExist());

                // Then - Verify budget is unlocked
                mockMvc.perform(get("/api/budgets/" + budgetId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("UNLOCKED")))
                                .andExpect(jsonPath("$.lockedAt").doesNotExist());
        }

        @Test
        void shouldReturn400WhenUnlockingUnlockedBudget() throws Exception {
                // Given - Create an unlocked budget
                Map<String, Object> budgetRequest = createBudgetRequest(11, 2024);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // When/Then - Attempt to unlock already unlocked budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Budget is not locked"));
        }

        @Test
        void shouldReturn404WhenUnlockingNonExistentBudget() throws Exception {
                // Given - Random UUID
                UUID randomId = UUID.randomUUID();

                // When/Then - Attempt to unlock non-existent budget
                mockMvc.perform(put("/api/budgets/" + randomId + "/unlock"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("Budget not found"));
        }

        @Test
        void shouldReturn400WhenUnlockingNonMostRecentBudget() throws Exception {
                // Given - Create and lock January budget
                Map<String, Object> janRequest = createBudgetRequest(1, 2024);
                String janResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(janRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String janBudgetId = objectMapper.readTree(janResponse).get("id").asText();

                // Create bank account
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("TestAccount",
                                "Test", new BigDecimal("1000.00"));

                // Add balanced budget to January and lock it
                addBalancedBudgetItems(janBudgetId, account.getId().toString(), 1000.00);
                mockMvc.perform(put("/api/budgets/" + janBudgetId + "/lock"))
                                .andExpect(status().isOk());

                // Create and lock February budget (now most recent)
                Map<String, Object> febRequest = createBudgetRequest(2, 2024);
                String febResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(febRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String febBudgetId = objectMapper.readTree(febResponse).get("id").asText();

                addBalancedBudgetItems(febBudgetId, account.getId().toString(), 1000.00);
                mockMvc.perform(put("/api/budgets/" + febBudgetId + "/lock"))
                                .andExpect(status().isOk());

                // When/Then - Attempt to unlock January (not most recent)
                mockMvc.perform(put("/api/budgets/" + janBudgetId + "/unlock"))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.error").value("Only the most recent budget can be unlocked"));
        }

        @Test
        void shouldReverseBalanceUpdatesWhenUnlocking() throws Exception {
                // Given - Create three accounts with initial balances
                org.example.axelnyman.main.domain.model.BankAccount accountA = createBankAccountEntity("Account A", "A",
                                new BigDecimal("500.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountB = createBankAccountEntity("Account B", "B",
                                new BigDecimal("300.00"));
                org.example.axelnyman.main.domain.model.BankAccount accountC = createBankAccountEntity("Account C", "C",
                                new BigDecimal("1000.00"));

                // Create budget
                Map<String, Object> budgetRequest = createBudgetRequest(3, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Add income: 300.00 to account A
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountA.getId().toString());
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", 300.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add savings to distribute: A+100, B+100, C+100 (total 300)
                Map<String, Object> savingsA = new HashMap<>();
                savingsA.put("bankAccountId", accountA.getId().toString());
                savingsA.put("name", "Savings A");
                savingsA.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsA)))
                                .andExpect(status().isCreated());

                Map<String, Object> savingsB = new HashMap<>();
                savingsB.put("bankAccountId", accountB.getId().toString());
                savingsB.put("name", "Savings B");
                savingsB.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsB)))
                                .andExpect(status().isCreated());

                Map<String, Object> savingsC = new HashMap<>();
                savingsC.put("bankAccountId", accountC.getId().toString());
                savingsC.put("name", "Savings C");
                savingsC.put("amount", 100.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsC)))
                                .andExpect(status().isCreated());

                // Lock budget (balances should become: A=600, B=400, C=1100)
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Verify balances after lock
                mockMvc.perform(get("/api/bank-accounts"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account A')].currentBalance").value(600.00))
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account B')].currentBalance").value(400.00))
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account C')].currentBalance")
                                                .value(1100.00));

                // When - Unlock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk());

                // Then - Verify balances restored to original
                mockMvc.perform(get("/api/bank-accounts"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account A')].currentBalance").value(500.00))
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account B')].currentBalance").value(300.00))
                                .andExpect(jsonPath("$.accounts[?(@.name=='Account C')].currentBalance")
                                                .value(1000.00));
        }

        @Test
        void shouldDeleteTodoListWhenUnlocking() throws Exception {
                // Given - Create budget with income/expenses/savings and lock it
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("TodoTest",
                                "Test", new BigDecimal("5000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(4, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Add balanced budget items
                addBalancedBudgetItems(budgetId, account.getId().toString(), 3000.00);

                // Lock budget (generates todo list)
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Verify todo list exists
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray());

                // When - Unlock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk());

                // Then - Verify todo list is deleted
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.error").value("Todo list not found for this budget"));
        }

        @Test
        void shouldRestoreRecurringExpenseToNullWhenNoOtherLockedBudgets() throws Exception {
                // Given - Create recurring expense template
                Map<String, Object> templateRequest = new HashMap<>();
                templateRequest.put("name", "Rent Template");
                templateRequest.put("amount", 1000.00);
                templateRequest.put("recurrenceInterval", "MONTHLY");
                templateRequest.put("isManual", false);

                String templateResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String templateId = objectMapper.readTree(templateResponse).get("id").asText();

                // Verify initial state (lastUsedDate should be null)
                mockMvc.perform(get("/api/recurring-expenses/" + templateId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.lastUsedDate").doesNotExist());

                // Create budget using template
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("RecurringTest1",
                                "Test", new BigDecimal("5000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(5, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", account.getId().toString());
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", 1000.00);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense from recurring template
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", account.getId().toString());
                expenseRequest.put("recurringExpenseId", templateId);
                expenseRequest.put("name", "Rent");
                expenseRequest.put("amount", 1000.00);
                expenseRequest.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Lock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());

                // Verify template has lastUsedDate set
                mockMvc.perform(get("/api/recurring-expenses/" + templateId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.lastUsedDate").exists());

                // When - Unlock budget
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk());

                // Then - Verify lastUsedDate is null again
                mockMvc.perform(get("/api/recurring-expenses/" + templateId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.lastUsedDate").doesNotExist());
        }

        @Test
        void shouldRestoreRecurringExpenseToPreviousLockedBudget() throws Exception {
                // Given - Create recurring expense template
                Map<String, Object> templateRequest = new HashMap<>();
                templateRequest.put("name", "Subscription Template");
                templateRequest.put("amount", 50.00);
                templateRequest.put("recurrenceInterval", "MONTHLY");
                templateRequest.put("isManual", false);

                String templateResponse = mockMvc.perform(post("/api/recurring-expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(templateRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String templateId = objectMapper.readTree(templateResponse).get("id").asText();

                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("RecurringTest2",
                                "Test", new BigDecimal("10000.00"));

                // Create and lock January budget using template
                Map<String, Object> janRequest = createBudgetRequest(1, 2026);
                String janResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(janRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String janBudgetId = objectMapper.readTree(janResponse).get("id").asText();

                addIncomeAndExpenseWithRecurring(janBudgetId, account.getId().toString(), templateId, 1000.00, 50.00);
                mockMvc.perform(put("/api/budgets/" + janBudgetId + "/lock"))
                                .andExpect(status().isOk());

                // Get January's lockedAt timestamp
                String janBudgetDetails = mockMvc.perform(get("/api/budgets/" + janBudgetId))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                @SuppressWarnings("unused")
                String janLockedAt = objectMapper.readTree(janBudgetDetails).get("lockedAt").asText();

                // Create and lock February budget using same template
                Map<String, Object> febRequest = createBudgetRequest(2, 2026);
                String febResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(febRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String febBudgetId = objectMapper.readTree(febResponse).get("id").asText();

                addIncomeAndExpenseWithRecurring(febBudgetId, account.getId().toString(), templateId, 1000.00, 50.00);
                mockMvc.perform(put("/api/budgets/" + febBudgetId + "/lock"))
                                .andExpect(status().isOk());

                // Get February's lockedAt timestamp
                String febBudgetDetails = mockMvc.perform(get("/api/budgets/" + febBudgetId))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                String febLockedAt = objectMapper.readTree(febBudgetDetails).get("lockedAt").asText();

                // Create and lock March budget using same template
                Map<String, Object> marRequest = createBudgetRequest(3, 2026);
                String marResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(marRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String marBudgetId = objectMapper.readTree(marResponse).get("id").asText();

                addIncomeAndExpenseWithRecurring(marBudgetId, account.getId().toString(), templateId, 1000.00, 50.00);
                mockMvc.perform(put("/api/budgets/" + marBudgetId + "/lock"))
                                .andExpect(status().isOk());

                // Verify template has March's lockedAt
                mockMvc.perform(get("/api/recurring-expenses/" + templateId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.lastUsedDate").exists());

                // When - Unlock March (most recent)
                mockMvc.perform(put("/api/budgets/" + marBudgetId + "/unlock"))
                                .andExpect(status().isOk());

                // Then - Verify lastUsedDate restored to February's lockedAt
                String templateDetails = mockMvc.perform(get("/api/recurring-expenses/" + templateId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.lastUsedDate").exists())
                                .andReturn().getResponse().getContentAsString();

                String restoredLastUsedDate = objectMapper.readTree(templateDetails).get("lastUsedDate").asText();
                assertThat(restoredLastUsedDate).isEqualTo(febLockedAt);
        }

        @Test
        void shouldAllowRelockAfterUnlock() throws Exception {
                // Given - Create and lock a budget
                org.example.axelnyman.main.domain.model.BankAccount account = createBankAccountEntity("RelockTest",
                                "Test", new BigDecimal("5000.00"));

                Map<String, Object> budgetRequest = createBudgetRequest(6, 2025);
                String budgetResponse = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(budgetRequest)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();

                String budgetId = objectMapper.readTree(budgetResponse).get("id").asText();

                addBalancedBudgetItems(budgetId, account.getId().toString(), 2000.00);

                // First lock
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("LOCKED")));

                // Get account balance after first lock
                @SuppressWarnings("unused")
                String accountsAfterFirstLock = mockMvc.perform(get("/api/bank-accounts"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                // Unlock
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("UNLOCKED")));

                // When - Lock again
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.status", is("LOCKED")))
                                .andExpect(jsonPath("$.lockedAt").exists());

                // Then - Verify todo list regenerated
                mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.items").isArray());

                // Verify balances updated correctly again
                mockMvc.perform(get("/api/bank-accounts"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.accounts[?(@.name=='RelockTest')].currentBalance")
                                                .value(6000.00));
        }

        // Helper methods for unlock tests

        private void addBalancedBudgetItems(String budgetId, String accountId, double amount) throws Exception {
                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountId);
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", amount);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense (half of income)
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", accountId);
                expenseRequest.put("name", "Expense");
                expenseRequest.put("amount", amount / 2);
                expenseRequest.put("isManual", true);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings (half of income)
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", accountId);
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", amount / 2);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());
        }

        private void addIncomeAndExpenseWithRecurring(String budgetId, String accountId, String recurringExpenseId,
                        double income, double expense) throws Exception {
                // Add income
                Map<String, Object> incomeRequest = new HashMap<>();
                incomeRequest.put("bankAccountId", accountId);
                incomeRequest.put("name", "Income");
                incomeRequest.put("amount", income);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(incomeRequest)))
                                .andExpect(status().isCreated());

                // Add expense from recurring template
                Map<String, Object> expenseRequest = new HashMap<>();
                expenseRequest.put("bankAccountId", accountId);
                expenseRequest.put("recurringExpenseId", recurringExpenseId);
                expenseRequest.put("name", "Recurring Expense");
                expenseRequest.put("amount", expense);
                expenseRequest.put("isManual", false);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(expenseRequest)))
                                .andExpect(status().isCreated());

                // Add savings to balance
                Map<String, Object> savingsRequest = new HashMap<>();
                savingsRequest.put("bankAccountId", accountId);
                savingsRequest.put("name", "Savings");
                savingsRequest.put("amount", income - expense);
                mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(savingsRequest)))
                                .andExpect(status().isCreated());
        }
}
