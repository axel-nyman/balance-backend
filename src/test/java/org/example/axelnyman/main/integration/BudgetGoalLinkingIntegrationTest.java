package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.CreateBankAccountRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.ArchiveRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.CreateSavingsGoalRequest;
import org.example.axelnyman.main.domain.model.GoalAllocationChangeSource;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetExpenseRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetIncomeRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetRepository;
import org.example.axelnyman.main.infrastructure.data.context.BudgetSavingsRepository;
import org.example.axelnyman.main.infrastructure.data.context.GoalAllocationChangeRepository;
import org.example.axelnyman.main.infrastructure.data.context.GoalAllocationRepository;
import org.example.axelnyman.main.infrastructure.data.context.SavingsGoalRepository;
import org.example.axelnyman.main.infrastructure.data.context.TodoItemRepository;
import org.example.axelnyman.main.infrastructure.data.context.TodoListRepository;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for linking budget savings items to savings goals and the
 * allocation that happens on budget lock / reverses on unlock (item 070c).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class BudgetGoalLinkingIntegrationTest {

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
    private ObjectMapper objectMapper;

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private GoalAllocationRepository goalAllocationRepository;

    @Autowired
    private GoalAllocationChangeRepository goalAllocationChangeRepository;

    @Autowired
    private BankAccountRepository bankAccountRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private BudgetIncomeRepository budgetIncomeRepository;

    @Autowired
    private BudgetExpenseRepository budgetExpenseRepository;

    @Autowired
    private BudgetSavingsRepository budgetSavingsRepository;

    @Autowired
    private TodoListRepository todoListRepository;

    @Autowired
    private TodoItemRepository todoItemRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // Clean database state between tests (order matters due to foreign keys)
        todoItemRepository.deleteAll();
        todoListRepository.deleteAll();
        goalAllocationChangeRepository.deleteAll();
        goalAllocationRepository.deleteAll();
        budgetIncomeRepository.deleteAll();
        budgetExpenseRepository.deleteAll();
        budgetSavingsRepository.deleteAll();
        savingsGoalRepository.deleteAll();
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

    // ---------- helpers ----------

    private UUID createAccount(String name, String balance) throws Exception {
        String response = mockMvc.perform(post("/api/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateBankAccountRequest(name, null, new BigDecimal(balance)))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createGoal(String name) throws Exception {
        String response = mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateSavingsGoalRequest(name, null, null, null))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createBudget(int month, int year) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("month", month);
        request.put("year", year);
        String response = mockMvc.perform(post("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addIncome(UUID budgetId, UUID accountId, String name, String amount) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("bankAccountId", accountId.toString());
        request.put("name", name);
        request.put("amount", new BigDecimal(amount));
        mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    /** Adds a savings item; pass a goalId to link it, or null to leave it unlinked. */
    private String addSavings(UUID budgetId, UUID accountId, String name, String amount, UUID goalId)
            throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("bankAccountId", accountId.toString());
        request.put("name", name);
        request.put("amount", new BigDecimal(amount));
        if (goalId != null) {
            request.put("savingsGoalId", goalId.toString());
        }
        return mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private void lock(UUID budgetId) throws Exception {
        mockMvc.perform(put("/api/budgets/" + budgetId + "/lock")).andExpect(status().isOk());
    }

    private void unlock(UUID budgetId) throws Exception {
        mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock")).andExpect(status().isOk());
    }

    private void manualAllocate(UUID goalId, UUID accountId, String amount) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("bankAccountId", accountId.toString());
        request.put("amount", new BigDecimal(amount));
        mockMvc.perform(post("/api/savings-goals/" + goalId + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private long changeCount(UUID goalId, GoalAllocationChangeSource source) {
        return goalAllocationChangeRepository.findAllBySavingsGoalIdOrderByCreatedAtDesc(goalId).stream()
                .filter(change -> change.getSource() == source)
                .count();
    }

    // ---------- linking on the savings item ----------

    @Test
    void shouldPersistAndReturnSavingsGoalIdOnSavingsItem() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        String created = addSavings(budget, account, "Emergency Fund", "1000.00", goal);
        UUID savingsId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        // Response carries the link
        org.junit.jupiter.api.Assertions.assertEquals(
                goal.toString(), objectMapper.readTree(created).get("savingsGoalId").asText());

        // Budget detail exposes it on the savings line
        mockMvc.perform(get("/api/budgets/" + budget))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings[0].id", is(savingsId.toString())))
                .andExpect(jsonPath("$.savings[0].savingsGoalId", is(goal.toString())));
    }

    @Test
    void shouldLeaveSavingsGoalNullWhenNotProvided() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID budget = createBudget(6, 2024);

        String created = addSavings(budget, account, "Emergency Fund", "1000.00", null);

        org.junit.jupiter.api.Assertions.assertTrue(
                objectMapper.readTree(created).get("savingsGoalId").isNull());

        mockMvc.perform(get("/api/budgets/" + budget))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings[0].savingsGoalId").doesNotExist());
    }

    @Test
    void shouldRejectLinkingNonExistentGoal() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID budget = createBudget(6, 2024);

        Map<String, Object> request = new HashMap<>();
        request.put("bankAccountId", account.toString());
        request.put("name", "Emergency Fund");
        request.put("amount", 1000.00);
        request.put("savingsGoalId", UUID.randomUUID().toString());

        mockMvc.perform(post("/api/budgets/" + budget + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLinkingArchivedGoal() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Old goal");
        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(false))))
                .andExpect(status().isOk());
        UUID budget = createBudget(6, 2024);

        Map<String, Object> request = new HashMap<>();
        request.put("bankAccountId", account.toString());
        request.put("name", "Emergency Fund");
        request.put("amount", 1000.00);
        request.put("savingsGoalId", goal.toString());

        mockMvc.perform(post("/api/budgets/" + budget + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateSavingsGoalLinkViaUpdate() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        String created = addSavings(budget, account, "Emergency Fund", "1000.00", null);
        UUID savingsId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        Map<String, Object> update = new HashMap<>();
        update.put("bankAccountId", account.toString());
        update.put("name", "Emergency Fund");
        update.put("amount", 1000.00);
        update.put("savingsGoalId", goal.toString());

        mockMvc.perform(put("/api/budgets/" + budget + "/savings/" + savingsId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsGoalId", is(goal.toString())));

        // Clearing the link (omit the field) sets it back to null
        Map<String, Object> clear = new HashMap<>();
        clear.put("bankAccountId", account.toString());
        clear.put("name", "Emergency Fund");
        clear.put("amount", 1000.00);

        mockMvc.perform(put("/api/budgets/" + budget + "/savings/" + savingsId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(clear)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsGoalId").value(nullValue()));
    }

    // ---------- allocation on lock ----------

    @Test
    void shouldAllocateToLinkedGoalOnLock() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Emergency Fund", "1000.00", goal);

        lock(budget);

        // Account credited by the savings amount
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(6000.00)));

        // Goal now earmarks the saved amount on that account
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(1000.00)))
                .andExpect(jsonPath("$.allocations", hasSize(1)))
                .andExpect(jsonPath("$.allocations[0].bankAccountId", is(account.toString())))
                .andExpect(jsonPath("$.allocations[0].amount", is(1000.00)));

        // Exactly one BUDGET_LOCK ledger row
        org.junit.jupiter.api.Assertions.assertEquals(1, changeCount(goal, GoalAllocationChangeSource.BUDGET_LOCK));
        org.junit.jupiter.api.Assertions.assertEquals(1, goalAllocationRepository.count());
    }

    @Test
    void shouldAccumulateOntoExistingManualAllocationAndRestoreExactlyOnUnlock() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");

        // Pre-existing manual allocation of 500
        manualAllocate(goal, account, "500.00");

        UUID budget = createBudget(6, 2024);
        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Emergency Fund", "1000.00", goal);

        lock(budget);

        // 500 (manual) + 1000 (lock) = 1500
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.totalAllocated", is(1500.00)));

        unlock(budget);

        // Reversed exactly back to the pre-lock manual amount, not to zero
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.totalAllocated", is(500.00)))
                .andExpect(jsonPath("$.allocations", hasSize(1)));
        org.junit.jupiter.api.Assertions.assertEquals(1, goalAllocationRepository.count());
    }

    @Test
    void shouldAggregateMultipleLinkedSavingsToSameGoalAndAccount() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Part A", "400.00", goal);
        addSavings(budget, account, "Part B", "600.00", goal);

        lock(budget);

        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.totalAllocated", is(1000.00)))
                .andExpect(jsonPath("$.allocations", hasSize(1)));

        // Aggregated into a single BUDGET_LOCK ledger row
        org.junit.jupiter.api.Assertions.assertEquals(1, changeCount(goal, GoalAllocationChangeSource.BUDGET_LOCK));
    }

    @Test
    void shouldAllocateAcrossMultipleGoalsAndAccountsOnLock() throws Exception {
        UUID checking = createAccount("Checking", "5000.00");
        UUID savingsAcc = createAccount("Savings", "5000.00");
        UUID trip = createGoal("Trip");
        UUID buffer = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, checking, "Salary", "1500.00");
        addSavings(budget, checking, "Trip fund", "1000.00", trip);
        addSavings(budget, savingsAcc, "Buffer fund", "500.00", buffer);

        lock(budget);

        mockMvc.perform(get("/api/savings-goals/" + trip))
                .andExpect(jsonPath("$.totalAllocated", is(1000.00)));
        mockMvc.perform(get("/api/savings-goals/" + buffer))
                .andExpect(jsonPath("$.totalAllocated", is(500.00)));
    }

    // ---------- unchanged behaviour when nothing is linked ----------

    @Test
    void shouldNotCreateAllocationsWhenNoSavingsLinked() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        createGoal("Untouched goal");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Emergency Fund", "1000.00", null);

        lock(budget);

        // Balance still updated exactly as before this feature
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(6000.00)));

        // No allocations and no allocation history written
        org.junit.jupiter.api.Assertions.assertEquals(0, goalAllocationRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(0, goalAllocationChangeRepository.count());
    }

    // ---------- reversal on unlock ----------

    @Test
    void shouldReverseGoalAllocationOnUnlock() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Emergency Fund", "1000.00", goal);

        lock(budget);
        unlock(budget);

        // Allocation fully reversed; the row is removed
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));
        org.junit.jupiter.api.Assertions.assertEquals(0, goalAllocationRepository.count());

        // Both the lock and the reversing change are recorded in the ledger
        org.junit.jupiter.api.Assertions.assertEquals(2, changeCount(goal, GoalAllocationChangeSource.BUDGET_LOCK));

        // Account balance restored
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(5000.00)));
    }

    @Test
    void shouldSkipArchivedGoalOnLockAndStillLock() throws Exception {
        UUID account = createAccount("Checking", "5000.00");
        UUID goal = createGoal("Buffer");
        UUID budget = createBudget(6, 2024);

        addIncome(budget, account, "Salary", "1000.00");
        addSavings(budget, account, "Emergency Fund", "1000.00", goal);

        // Archive the linked goal while the budget is still unlocked
        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(false))))
                .andExpect(status().isOk());

        // Lock still succeeds; balance still updated
        lock(budget);
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(6000.00)));

        // Archived goal received no new allocation
        org.junit.jupiter.api.Assertions.assertEquals(0, goalAllocationRepository.count());

        // Unlock also succeeds with nothing to reverse
        unlock(budget);
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(5000.00)));
    }
}
