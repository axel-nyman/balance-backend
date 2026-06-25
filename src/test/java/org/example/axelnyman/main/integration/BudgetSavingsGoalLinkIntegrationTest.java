package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.CreateBankAccountRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.*;
import org.example.axelnyman.main.infrastructure.data.context.*;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for item 070c: linking budget savings lines to savings
 * goals and allocating/reversing those goals on budget lock/unlock.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class BudgetSavingsGoalLinkIntegrationTest {

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
    private BudgetRepository budgetRepository;
    @Autowired
    private BudgetIncomeRepository budgetIncomeRepository;
    @Autowired
    private BudgetExpenseRepository budgetExpenseRepository;
    @Autowired
    private BudgetSavingsRepository budgetSavingsRepository;
    @Autowired
    private TodoItemRepository todoItemRepository;
    @Autowired
    private TodoListRepository todoListRepository;
    @Autowired
    private GoalAllocationChangeRepository goalAllocationChangeRepository;
    @Autowired
    private GoalAllocationRepository goalAllocationRepository;
    @Autowired
    private SavingsGoalRepository savingsGoalRepository;
    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;
    @Autowired
    private BankAccountRepository bankAccountRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        todoItemRepository.deleteAll();
        todoListRepository.deleteAll();
        budgetIncomeRepository.deleteAll();
        budgetExpenseRepository.deleteAll();
        budgetSavingsRepository.deleteAll();
        budgetRepository.deleteAll();
        goalAllocationChangeRepository.deleteAll();
        goalAllocationRepository.deleteAll();
        savingsGoalRepository.deleteAll();
        balanceHistoryRepository.deleteAll();
        bankAccountRepository.deleteAll();
    }

    @AfterAll
    static void cleanup() {
        if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
            postgreSQLContainer.stop();
        }
    }

    // ==================== Linking on add / update ====================

    @Test
    void shouldPersistGoalLinkWhenAddingSavingsWithGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);

        UUID savingsId = addSavings(budgetId, accountId, "Trip", "500.00", goalId);

        mockMvc.perform(get("/api/budgets/" + budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings[0].id", is(savingsId.toString())))
                .andExpect(jsonPath("$.savings[0].savingsGoalId", is(goalId.toString())));
    }

    @Test
    void shouldKeepNullGoalLinkWhenAddingSavingsWithoutGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID budgetId = createBudget(6, 2024);

        addSavings(budgetId, accountId, "Trip", "500.00", null);

        mockMvc.perform(get("/api/budgets/" + budgetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savings[0].savingsGoalId").doesNotExist());
    }

    @Test
    void shouldRejectLinkingSavingsToNonexistentGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID budgetId = createBudget(6, 2024);

        mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(savingsBody(accountId, "Trip", "500.00", UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectLinkingSavingsToArchivedGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        archiveGoal(goalId, false);
        UUID budgetId = createBudget(6, 2024);

        mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(savingsBody(accountId, "Trip", "500.00", goalId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUnlinkGoalWhenUpdatingSavingsWithoutGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        UUID savingsId = addSavings(budgetId, accountId, "Trip", "500.00", goalId);

        mockMvc.perform(put("/api/budgets/" + budgetId + "/savings/" + savingsId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(savingsBody(accountId, "Trip", "500.00", null)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/budgets/" + budgetId))
                .andExpect(jsonPath("$.savings[0].savingsGoalId").doesNotExist());
    }

    // ==================== Allocation on lock ====================

    @Test
    void shouldAllocateToGoalOnLockForLinkedSavings() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", "2000.00");
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Trip", "500.00", goalId);

        lock(budgetId);

        // Goal now holds the saved amount on the account.
        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(500.00)))
                .andExpect(jsonPath("$.allocations[0].bankAccountId", is(accountId.toString())))
                .andExpect(jsonPath("$.allocations[0].amount", is(500.00)));

        // A BUDGET_LOCK ledger row records the change.
        mockMvc.perform(get("/api/savings-goals/" + goalId + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[0].source", is("BUDGET_LOCK")))
                .andExpect(jsonPath("$.changes[0].changeAmount", is(500.00)))
                .andExpect(jsonPath("$.changes[0].resultingAmount", is(500.00)));

        // Balance was credited by savings (1500); 500 is earmarked, 1000 free.
        assertAccountAllocations(accountId, "500.00", "1000.00");
    }

    @Test
    void shouldAddToExistingAllocationOnLock() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        // Goal already earmarks 200 of the account's balance.
        UUID goalId = createGoalWithSeed("Vacation", accountId, "200.00");
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "300.00");
        addSavings(budgetId, accountId, "Trip", "300.00", goalId);

        lock(budgetId);

        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(500.00)))
                .andExpect(jsonPath("$.allocations[0].amount", is(500.00)));
    }

    @Test
    void shouldAggregateMultipleSavingsLinesToSameGoalAndAccountOnLock() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Flights", "300.00", goalId);
        addSavings(budgetId, accountId, "Hotel", "200.00", goalId);

        lock(budgetId);

        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(500.00)))
                .andExpect(jsonPath("$.allocations", hasSize(1)))
                .andExpect(jsonPath("$.allocations[0].amount", is(500.00)));
    }

    @Test
    void shouldNotAllocateOnLockWhenSavingsHasNoGoal() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Trip", "500.00", null);

        lock(budgetId);

        // Goal untouched; account balance still credited as before (no earmark).
        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));
        assertAccountAllocations(accountId, "0.00", "1500.00");
    }

    @Test
    void shouldSkipArchivedGoalAllocationOnLock() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Trip", "500.00", goalId);

        // Goal archived after linking but before lock — lock must still succeed
        // and create no allocation for the archived goal.
        archiveGoal(goalId, false);

        lock(budgetId);

        mockMvc.perform(get("/api/budgets/" + budgetId))
                .andExpect(jsonPath("$.status", is("LOCKED")));
        assertAccountAllocations(accountId, "0.00", "1500.00");
    }

    @Test
    void shouldKeepUnallocatedInvariantWhenAccountFullyAllocatedBeforeLock() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        // Account starts fully earmarked by another goal.
        createGoalWithSeed("Buffer", accountId, "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "400.00");
        addSavings(budgetId, accountId, "Trip", "400.00", goalId);

        // The saving credits the account by 400, backing the new 400 earmark, so
        // the lock succeeds and leaves no unallocated money negative.
        lock(budgetId);

        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(400.00)));
        assertAccountAllocations(accountId, "1400.00", "0.00");
    }

    // ==================== Reversal on unlock ====================

    @Test
    void shouldReverseGoalAllocationOnUnlock() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Trip", "500.00", goalId);
        lock(budgetId);

        unlock(budgetId);

        // Allocation fully removed; account restored to pre-lock state.
        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));
        assertAccountAllocations(accountId, "0.00", "1000.00");

        // History keeps both the lock and the reversing rows (newest first).
        mockMvc.perform(get("/api/savings-goals/" + goalId + "/history"))
                .andExpect(jsonPath("$.changes", hasSize(2)))
                .andExpect(jsonPath("$.changes[0].source", is("BUDGET_LOCK")))
                .andExpect(jsonPath("$.changes[0].changeAmount", is(-500.00)))
                .andExpect(jsonPath("$.changes[0].resultingAmount", is(0.00)));
    }

    @Test
    void shouldRestoreOnlySeededAllocationWhenUnlockingGoalWithPriorAllocation() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoalWithSeed("Vacation", accountId, "200.00");
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "300.00");
        addSavings(budgetId, accountId, "Trip", "300.00", goalId);
        lock(budgetId);

        unlock(budgetId);

        // Lock added 300 on top of the seeded 200; unlock removes exactly 300.
        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(200.00)))
                .andExpect(jsonPath("$.allocations[0].amount", is(200.00)));
        assertAccountAllocations(accountId, "200.00", "800.00");
    }

    @Test
    void shouldPreserveManualAllocationMadeWhileLocked() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "300.00");
        addSavings(budgetId, accountId, "Trip", "300.00", goalId);
        lock(budgetId);

        // While locked the user manually bumps the same earmark by 100 (300 -> 400).
        allocate(goalId, accountId, "400.00");

        unlock(budgetId);

        // Only the lock's 300 is reversed; the manual +100 survives.
        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(100.00)))
                .andExpect(jsonPath("$.allocations[0].amount", is(100.00)));
    }

    @Test
    void shouldNotRecreateAllocationOnUnlockWhenGoalArchivedWhileLocked() throws Exception {
        UUID accountId = createAccount("Savings", "1000.00");
        UUID goalId = createGoal("Vacation", null);
        UUID budgetId = createBudget(6, 2024);
        addIncome(budgetId, accountId, "Salary", "500.00");
        addSavings(budgetId, accountId, "Trip", "500.00", goalId);
        lock(budgetId);

        // Archiving (releaseToBalance=false) frees the earmark while locked.
        archiveGoal(goalId, false);

        // Unlock must not resurrect the allocation for the archived goal.
        unlock(budgetId);

        mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));
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

    private UUID createGoal(String name, String targetAmount) throws Exception {
        return createGoalInternal(new CreateSavingsGoalRequest(
                name, targetAmount == null ? null : new BigDecimal(targetAmount), null, null));
    }

    private UUID createGoalWithSeed(String name, UUID accountId, String amount) throws Exception {
        return createGoalInternal(new CreateSavingsGoalRequest(name, null, null,
                List.of(new SeedAllocationRequest(accountId, new BigDecimal(amount)))));
    }

    private UUID createGoalInternal(CreateSavingsGoalRequest request) throws Exception {
        String response = mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void allocate(UUID goalId, UUID accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/savings-goals/" + goalId + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AllocateRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk());
    }

    private void archiveGoal(UUID goalId, boolean releaseToBalance) throws Exception {
        mockMvc.perform(post("/api/savings-goals/" + goalId + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(releaseToBalance))))
                .andExpect(status().isOk());
    }

    private UUID createBudget(int month, int year) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("month", month);
        body.put("year", year);
        String response = mockMvc.perform(post("/api/budgets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private void addIncome(UUID budgetId, UUID accountId, String name, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("bankAccountId", accountId.toString());
        body.put("name", name);
        body.put("amount", new BigDecimal(amount));
        mockMvc.perform(post("/api/budgets/" + budgetId + "/income")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private UUID addSavings(UUID budgetId, UUID accountId, String name, String amount, UUID goalId)
            throws Exception {
        String response = mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(savingsBody(accountId, name, amount, goalId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String savingsBody(UUID accountId, String name, String amount, UUID goalId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("bankAccountId", accountId.toString());
        body.put("name", name);
        body.put("amount", new BigDecimal(amount));
        if (goalId != null) {
            body.put("savingsGoalId", goalId.toString());
        }
        return objectMapper.writeValueAsString(body);
    }

    private void lock(UUID budgetId) throws Exception {
        mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                .andExpect(status().isOk());
    }

    private void unlock(UUID budgetId) throws Exception {
        mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                .andExpect(status().isOk());
    }

    private void assertAccountAllocations(UUID accountId, String allocated, String unallocated)
            throws Exception {
        String response = mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var accounts = objectMapper.readTree(response).get("accounts");
        var node = java.util.stream.StreamSupport.stream(accounts.spliterator(), false)
                .filter(a -> a.get("id").asText().equals(accountId.toString()))
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(new BigDecimal(node.get("allocatedAmount").asText()))
                .isEqualByComparingTo(allocated);
        org.assertj.core.api.Assertions.assertThat(new BigDecimal(node.get("unallocatedAmount").asText()))
                .isEqualByComparingTo(unallocated);
    }
}
