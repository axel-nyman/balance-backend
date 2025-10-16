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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();

        // Clean database state between tests (order matters due to foreign keys)
        budgetIncomeRepository.deleteAll();
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
}
