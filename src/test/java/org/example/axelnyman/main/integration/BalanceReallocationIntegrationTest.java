package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.CreateBankAccountRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.AllocateRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.CreateSavingsGoalRequest;
import org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository;
import org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository;
import org.example.axelnyman.main.infrastructure.data.context.GoalAllocationChangeRepository;
import org.example.axelnyman.main.infrastructure.data.context.GoalAllocationRepository;
import org.example.axelnyman.main.infrastructure.data.context.SavingsGoalRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for manual balance updates that interact with savings-goal
 * allocations (item 070d).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class BalanceReallocationIntegrationTest {

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

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

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

    private void allocate(UUID goalId, UUID accountId, String amount) throws Exception {
        mockMvc.perform(post("/api/savings-goals/" + goalId + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AllocateRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk());
    }

    private record Entry(UUID savingsGoalId, String changeBy) {}

    private Entry entry(UUID goalId, String changeBy) {
        return new Entry(goalId, changeBy);
    }

    private ResultActions updateBalance(UUID accountId, String newBalance, Entry... reallocation) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("newBalance", new BigDecimal(newBalance));
        body.put("date", LocalDate.now().toString());
        body.put("comment", "manual correction");
        if (reallocation.length > 0) {
            List<Map<String, Object>> entries = new ArrayList<>();
            for (Entry e : reallocation) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("savingsGoalId", e.savingsGoalId());
                m.put("changeBy", new BigDecimal(e.changeBy()));
                entries.add(m);
            }
            body.put("reallocation", entries);
        }
        return mockMvc.perform(post("/api/bank-accounts/" + accountId + "/balance")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private BigDecimal goalTotalAllocated(UUID goalId) throws Exception {
        String response = mockMvc.perform(get("/api/savings-goals/" + goalId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new BigDecimal(objectMapper.readTree(response).get("totalAllocated").asText());
    }

    private String latestHistorySource(UUID goalId) throws Exception {
        String response = mockMvc.perform(get("/api/savings-goals/" + goalId + "/history"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("changes").get(0).get("source").asText();
    }

    // ---------- decrease: within slack ----------

    @Test
    void shouldNotChangeAllocationsWhenDecreaseStaysWithinSlack() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal("Buffer");
        allocate(goal, account, "500.00");

        updateBalance(account, "900.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(900.00)))
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(0)));

        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("500.00");
        // No reallocation row was written (only the original MANUAL allocation change).
        assertThat(latestHistorySource(goal)).isEqualTo("MANUAL");
    }

    // ---------- decrease: single-goal deficit (auto) ----------

    @Test
    void shouldAutoReduceSingleGoalAllocationOnDeficit() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal("Buffer");
        allocate(goal, account, "800.00");

        updateBalance(account, "600.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(600.00)))
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(1)))
                .andExpect(jsonPath("$.allocationAdjustments[0].savingsGoalId", is(goal.toString())))
                .andExpect(jsonPath("$.allocationAdjustments[0].changeAmount", is(-200.00)))
                .andExpect(jsonPath("$.allocationAdjustments[0].resultingAmount", is(600.00)));

        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("600.00");
        assertThat(latestHistorySource(goal)).isEqualTo("BALANCE_REALLOCATION");
        // Invariant: allocations (600) == balance (600), fully backed.
        assertAccountAllocated(account, "600.00", "0");
    }

    // ---------- decrease: multi-goal deficit ----------

    @Test
    void shouldRejectMultiGoalDeficitWithoutSplit() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goalA = createGoal("House");
        UUID goalB = createGoal("Car");
        allocate(goalA, account, "400.00");
        allocate(goalB, account, "400.00");

        updateBalance(account, "600.00")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", not(emptyOrNullString())))
                .andExpect(jsonPath("$.accountId", is(account.toString())))
                .andExpect(jsonPath("$.newBalance", is(600.00)))
                .andExpect(jsonPath("$.totalAllocated", is(800.00)))
                .andExpect(jsonPath("$.requiredReduction", is(200.00)))
                .andExpect(jsonPath("$.goals", hasSize(2)))
                .andExpect(jsonPath("$.goals[*].savingsGoalId",
                        containsInAnyOrder(goalA.toString(), goalB.toString())));

        // Nothing changed: balance and both allocations intact (no partial writes).
        assertAccountBalance(account, "1000.00");
        assertThat(goalTotalAllocated(goalA)).isEqualByComparingTo("400.00");
        assertThat(goalTotalAllocated(goalB)).isEqualByComparingTo("400.00");
    }

    @Test
    void shouldApplyMultiGoalDeficitSplitWhenProvided() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goalA = createGoal("House");
        UUID goalB = createGoal("Car");
        allocate(goalA, account, "400.00");
        allocate(goalB, account, "400.00");

        updateBalance(account, "600.00", entry(goalA, "-150.00"), entry(goalB, "-50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(2)));

        assertThat(goalTotalAllocated(goalA)).isEqualByComparingTo("250.00");
        assertThat(goalTotalAllocated(goalB)).isEqualByComparingTo("350.00");
        // Invariant: 250 + 350 == 600 balance.
        assertAccountAllocated(account, "600.00", "0");
        assertThat(latestHistorySource(goalA)).isEqualTo("BALANCE_REALLOCATION");
        assertThat(latestHistorySource(goalB)).isEqualTo("BALANCE_REALLOCATION");
    }

    @Test
    void shouldRejectDeficitSplitNotSummingToDeficit() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goalA = createGoal("House");
        UUID goalB = createGoal("Car");
        allocate(goalA, account, "400.00");
        allocate(goalB, account, "400.00");

        // Deficit is 200 but reductions sum to 150.
        updateBalance(account, "600.00", entry(goalA, "-100.00"), entry(goalB, "-50.00"))
                .andExpect(status().isBadRequest());

        assertAccountBalance(account, "1000.00");
        assertThat(goalTotalAllocated(goalA)).isEqualByComparingTo("400.00");
        assertThat(goalTotalAllocated(goalB)).isEqualByComparingTo("400.00");
    }

    @Test
    void shouldRejectDeficitSplitDrivingAllocationBelowZero() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goalA = createGoal("House");
        UUID goalB = createGoal("Car");
        allocate(goalA, account, "100.00");
        allocate(goalB, account, "700.00");

        // Deficit is 200; reductions sum to 200 but goalA only holds 100.
        updateBalance(account, "600.00", entry(goalA, "-150.00"), entry(goalB, "-50.00"))
                .andExpect(status().isBadRequest());

        assertAccountBalance(account, "1000.00");
        assertThat(goalTotalAllocated(goalA)).isEqualByComparingTo("100.00");
        assertThat(goalTotalAllocated(goalB)).isEqualByComparingTo("700.00");
    }

    @Test
    void shouldClampSingleGoalReductionToZeroWhenBalanceGoesNegative() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal("Buffer");
        allocate(goal, account, "400.00");

        updateBalance(account, "-50.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(-50.00)))
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(1)))
                .andExpect(jsonPath("$.allocationAdjustments[0].resultingAmount", is(0.00)));

        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("0");
    }

    // ---------- increase ----------

    @Test
    void shouldNotChangeAllocationsWhenIncreaseWithoutReallocation() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal("Buffer");
        allocate(goal, account, "500.00");

        updateBalance(account, "1200.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(0)));

        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("500.00");
        assertAccountAllocated(account, "500.00", "700.00");
    }

    @Test
    void shouldEarmarkIncreaseToSingleGoalWhenRequested() throws Exception {
        UUID account = createAccount("Savings", "1000.00");
        UUID goal = createGoal("House deposit");
        allocate(goal, account, "1000.00"); // fully allocated

        updateBalance(account, "1200.00", entry(goal, "200.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(1)))
                .andExpect(jsonPath("$.allocationAdjustments[0].changeAmount", is(200.00)))
                .andExpect(jsonPath("$.allocationAdjustments[0].resultingAmount", is(1200.00)));

        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("1200.00");
        assertAccountAllocated(account, "1200.00", "0");
        assertThat(latestHistorySource(goal)).isEqualTo("BALANCE_REALLOCATION");
    }

    @Test
    void shouldDistributeIncreaseAcrossMultipleGoalsWhenRequested() throws Exception {
        UUID account = createAccount("Savings", "1000.00");
        UUID goalA = createGoal("House");
        UUID goalB = createGoal("Car");
        allocate(goalA, account, "300.00");
        allocate(goalB, account, "200.00");

        updateBalance(account, "1300.00", entry(goalA, "100.00"), entry(goalB, "50.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(2)));

        assertThat(goalTotalAllocated(goalA)).isEqualByComparingTo("400.00");
        assertThat(goalTotalAllocated(goalB)).isEqualByComparingTo("250.00");
        assertAccountAllocated(account, "650.00", "650.00");
    }

    @Test
    void shouldRejectIncreaseReallocationExceedingIncrease() throws Exception {
        UUID account = createAccount("Savings", "1000.00");
        UUID goal = createGoal("House");
        allocate(goal, account, "500.00");

        // Increase is only 100 but the request earmarks 200.
        updateBalance(account, "1100.00", entry(goal, "200.00"))
                .andExpect(status().isBadRequest());

        // Whole transaction rolled back: balance and allocation unchanged.
        assertAccountBalance(account, "1000.00");
        assertThat(goalTotalAllocated(goal)).isEqualByComparingTo("500.00");
    }

    // ---------- legacy compatibility ----------

    @Test
    void shouldReturnEmptyAdjustmentsWhenAccountHasNoGoals() throws Exception {
        UUID account = createAccount("Checking", "1000.00");

        updateBalance(account, "750.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance", is(750.00)))
                .andExpect(jsonPath("$.allocationAdjustments", hasSize(0)));
    }

    // ---------- assertion helpers ----------

    private void assertAccountAllocated(UUID accountId, String allocated, String unallocated) throws Exception {
        String response = mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var accounts = objectMapper.readTree(response).get("accounts");
        for (var acc : accounts) {
            if (acc.get("id").asText().equals(accountId.toString())) {
                assertThat(new BigDecimal(acc.get("allocatedAmount").asText())).isEqualByComparingTo(allocated);
                assertThat(new BigDecimal(acc.get("unallocatedAmount").asText())).isEqualByComparingTo(unallocated);
                return;
            }
        }
        throw new AssertionError("Account not found: " + accountId);
    }

    private void assertAccountBalance(UUID accountId, String balance) throws Exception {
        String response = mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var accounts = objectMapper.readTree(response).get("accounts");
        for (var acc : accounts) {
            if (acc.get("id").asText().equals(accountId.toString())) {
                assertThat(new BigDecimal(acc.get("currentBalance").asText())).isEqualByComparingTo(balance);
                return;
            }
        }
        throw new AssertionError("Account not found: " + accountId);
    }
}
