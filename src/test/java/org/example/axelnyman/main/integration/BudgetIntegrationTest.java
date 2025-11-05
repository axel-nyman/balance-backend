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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();

        // Clean database state between tests (order matters due to foreign keys)
        budgetIncomeRepository.deleteAll();
        budgetExpenseRepository.deleteAll();
        budgetSavingsRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        budgetRepository.deleteAll();
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
                .andExpect(jsonPath("$.error", is("Another budget is currently unlocked. Lock or delete it before creating a new budget.")));
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
                .andExpect(jsonPath("$.error", is("Invalid year value. Must be between 2000 and 2100")));
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
                .andExpect(jsonPath("$.error", is("Invalid year value. Must be between 2000 and 2100")));
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
                .andExpect(jsonPath("$.expenses[0].bankAccount.id", is(bankAccount1.getId().toString())))
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
                .andExpect(jsonPath("$.expenses[?(@.name == 'Netflix Subscription')].recurringExpenseId",
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
        var bankAccount = createBankAccountEntity("Deleted Account", "Will be deleted", new BigDecimal("1000.00"));
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
        var bankAccount = createBankAccountEntity("Linked Account", "Used in budget", new BigDecimal("1000.00"));

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

        // When & Then - try to delete income using budget2's ID (income belongs to budget1)
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
        var bankAccount = createBankAccountEntity("Deleted Account", "Will be deleted", new BigDecimal("1000.00"));
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
        var bankAccount = createBankAccountEntity("Linked Account", "Used in budget", new BigDecimal("1000.00"));

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

        // When & Then - try to delete expense using budget2's ID (expense belongs to budget1)
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
        var bankAccount = createBankAccountEntity("Savings Account", "Emergency fund", new BigDecimal("10000.00"));

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
        var bankAccount = createBankAccountEntity("Investment Account", "Long-term savings", new BigDecimal("25000.50"));

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
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Budget not found")));
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
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Bank account not found")));
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
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Bank account not found")));
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

        // When & Then - deleting bank account should succeed (soft delete) because budget is locked
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
                .andExpect(jsonPath("$.error", is("Budget savings not found with id: " + nonExistentId)));
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
                .andExpect(jsonPath("$.error", is("Bank account not found with id: " + nonExistentBankAccountId)));
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
                .andExpect(jsonPath("$.error", is("Bank account not found with id: " + bankAccount2.getId())));
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

        // When & Then - try to delete savings using budget2's ID (savings belongs to budget1)
        mockMvc.perform(delete("/api/budgets/" + budget2.getId() + "/savings/" + savingsId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
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
        org.example.axelnyman.main.domain.model.RecurringExpense expense =
                new org.example.axelnyman.main.domain.model.RecurringExpense(
                        name,
                        amount,
                        org.example.axelnyman.main.domain.model.RecurrenceInterval.MONTHLY,
                        false
                );
        return recurringExpenseRepository.save(expense);
    }
}
