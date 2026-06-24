package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.CreateBankAccountRequest;
import org.example.axelnyman.main.domain.dtos.SavingsGoalDtos.*;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class SavingsGoalIntegrationTest {

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

    private UUID createGoal(CreateSavingsGoalRequest request) throws Exception {
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
                .content(objectMapper.writeValueAsString(new AllocateRequest(accountId, new BigDecimal(amount)))))
                .andExpect(status().isOk());
    }

    // ---------- create ----------

    @Test
    void shouldCreateGoalWithoutAllocations() throws Exception {
        CreateSavingsGoalRequest request = new CreateSavingsGoalRequest(
                "Trip to Japan", new BigDecimal("20000.00"), null, null);

        mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name", is("Trip to Japan")))
                .andExpect(jsonPath("$.targetAmount", is(20000.00)))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.completed", is(false)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));

        assert savingsGoalRepository.count() == 1;
    }

    @Test
    void shouldCreateGoalWithSeedAllocations() throws Exception {
        UUID checking = createAccount("Checking", "1000.00");
        UUID savings = createAccount("Savings", "500.00");

        CreateSavingsGoalRequest request = new CreateSavingsGoalRequest(
                "Buffer", new BigDecimal("2000.00"), null,
                List.of(new SeedAllocationRequest(checking, new BigDecimal("300.00")),
                        new SeedAllocationRequest(savings, new BigDecimal("200.00"))));

        mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAllocated", is(500.00)))
                .andExpect(jsonPath("$.allocations", hasSize(2)));

        // Two MANUAL history rows written
        assert goalAllocationChangeRepository.count() == 2;
    }

    // ---------- invariant ----------

    @Test
    void shouldRejectSeedAllocationExceedingBalanceAndRollBack() throws Exception {
        UUID account = createAccount("Small", "100.00");

        CreateSavingsGoalRequest request = new CreateSavingsGoalRequest(
                "Too big", null, null,
                List.of(new SeedAllocationRequest(account, new BigDecimal("200.00"))));

        mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        // Transaction rolled back: no goal, no allocation, no history
        assert savingsGoalRepository.count() == 0;
        assert goalAllocationRepository.count() == 0;
        assert goalAllocationChangeRepository.count() == 0;
    }

    @Test
    void shouldEnforceInvariantAcrossGoalsOnManualAllocate() throws Exception {
        UUID account = createAccount("Shared", "1000.00");
        UUID goalA = createGoal(new CreateSavingsGoalRequest("A", null, null, null));
        UUID goalB = createGoal(new CreateSavingsGoalRequest("B", null, null, null));

        allocate(goalA, account, "700.00");

        // 700 + 400 > 1000 → rejected
        mockMvc.perform(post("/api/savings-goals/" + goalB + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AllocateRequest(account, new BigDecimal("400.00")))))
                .andExpect(status().isConflict());

        // 700 + 300 == 1000 → allowed
        allocate(goalB, account, "300.00");

        assert goalAllocationRepository.sumAmountByBankAccountId(account).compareTo(new BigDecimal("1000.00")) == 0;
    }

    // ---------- allocate / adjust / zero ----------

    @Test
    void shouldAllocateAdjustAndRemoveOnZero() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));

        allocate(goal, account, "300.00");
        allocate(goal, account, "500.00"); // adjust upward
        allocate(goal, account, "0.00");   // remove

        // No active allocation remains, but all three changes are recorded
        assert goalAllocationRepository.findAllBySavingsGoalId(goal).isEmpty();

        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(0)))
                .andExpect(jsonPath("$.allocations", hasSize(0)));

        mockMvc.perform(get("/api/savings-goals/" + goal + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes", hasSize(3)))
                // newest first: -500 resulting 0, +200 resulting 500, +300 resulting 300
                .andExpect(jsonPath("$.changes[0].changeAmount", is(-500.00)))
                .andExpect(jsonPath("$.changes[0].resultingAmount", is(0.0)))
                .andExpect(jsonPath("$.changes[0].source", is("MANUAL")))
                .andExpect(jsonPath("$.changes[2].changeAmount", is(300.00)))
                .andExpect(jsonPath("$.changes[2].resultingAmount", is(300.00)));
    }

    @Test
    void shouldNotWriteHistoryForNoOpAllocation() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));

        allocate(goal, account, "0.00"); // allocate zero where none exists → no-op

        assert goalAllocationChangeRepository.count() == 0;
    }

    // ---------- unallocated computation ----------

    @Test
    void shouldExposeAllocatedAndUnallocatedOnBankAccountResponse() throws Exception {
        UUID checking = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));
        allocate(goal, checking, "300.00");

        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(1000.00)))
                .andExpect(jsonPath("$.accounts[0].allocatedAmount", is(300.00)))
                .andExpect(jsonPath("$.accounts[0].unallocatedAmount", is(700.00)));
    }

    // ---------- derived progress ----------

    @Test
    void shouldDeriveProgressAndCompletion() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", new BigDecimal("400.00"), null, null));

        allocate(goal, account, "200.00");
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.progressPercentage", is(50.00)))
                .andExpect(jsonPath("$.completed", is(false)));

        allocate(goal, account, "500.00"); // exceeds target → completed, >100%
        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.progressPercentage", is(125.00)))
                .andExpect(jsonPath("$.completed", is(true)));
    }

    @Test
    void shouldReturnNullProgressWhenNoTarget() throws Exception {
        UUID goal = createGoal(new CreateSavingsGoalRequest("No target", null, null, null));

        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(jsonPath("$.progressPercentage").doesNotExist())
                .andExpect(jsonPath("$.completed", is(false)));
    }

    // ---------- archive: false (no balance change) ----------

    @Test
    void shouldArchiveWithoutReleasingBalance() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));
        allocate(goal, account, "400.00");

        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")))
                .andExpect(jsonPath("$.archivedAt").exists());

        // Allocation freed, balance untouched, money fully unallocated again
        assert goalAllocationRepository.findAllBySavingsGoalId(goal).isEmpty();
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(1000.00)))
                .andExpect(jsonPath("$.accounts[0].unallocatedAmount", is(1000.00)));

        // Only the initial MANUAL balance-history row from account creation exists
        assert balanceHistoryRepository.count() == 1;
    }

    // ---------- archive: true (release to balance) ----------

    @Test
    void shouldArchiveReleasingBalanceAndWriteBalanceHistory() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));
        allocate(goal, account, "400.00");

        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ARCHIVED")));

        // Balance reduced by the freed amount; invariant preserved (unallocated unchanged)
        mockMvc.perform(get("/api/bank-accounts"))
                .andExpect(jsonPath("$.accounts[0].currentBalance", is(600.00)))
                .andExpect(jsonPath("$.accounts[0].allocatedAmount", is(0)))
                .andExpect(jsonPath("$.accounts[0].unallocatedAmount", is(600.00)));

        // Initial MANUAL + one AUTOMATIC release row
        assert balanceHistoryRepository.count() == 2;
        assert balanceHistoryRepository.findAllByBankAccountIdOrderByChangeDateDescCreatedAtDesc(
                account, org.springframework.data.domain.PageRequest.of(0, 10))
                .getContent().stream()
                .anyMatch(h -> h.getChangeAmount().compareTo(new BigDecimal("-400.00")) == 0
                        && h.getSource().name().equals("AUTOMATIC"));
    }

    @Test
    void shouldPreserveHistoryAfterArchiving() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));
        allocate(goal, account, "400.00");

        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ArchiveRequest(false))))
                .andExpect(status().isOk());

        // History survives archiving and is available for the archived goal
        mockMvc.perform(get("/api/savings-goals/" + goal + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes", hasSize(2)))
                .andExpect(jsonPath("$.changes[0].source", is("ARCHIVE")))
                .andExpect(jsonPath("$.changes[0].changeAmount", is(-400.00)))
                .andExpect(jsonPath("$.changes[1].source", is("MANUAL")));
    }

    // ---------- list excludes archived ----------

    @Test
    void shouldExcludeArchivedGoalsFromList() throws Exception {
        createGoal(new CreateSavingsGoalRequest("Active goal", null, null, null));
        UUID toArchive = createGoal(new CreateSavingsGoalRequest("Archived goal", null, null, null));

        mockMvc.perform(post("/api/savings-goals/" + toArchive + "/archive"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/savings-goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goalCount", is(1)))
                .andExpect(jsonPath("$.goals", hasSize(1)))
                .andExpect(jsonPath("$.goals[0].name", is("Active goal")));
    }

    // ---------- detail breakdown ----------

    @Test
    void shouldReturnGoalWithPerAccountBreakdown() throws Exception {
        UUID checking = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));
        allocate(goal, checking, "250.00");

        mockMvc.perform(get("/api/savings-goals/" + goal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocations", hasSize(1)))
                .andExpect(jsonPath("$.allocations[0].bankAccountId", is(checking.toString())))
                .andExpect(jsonPath("$.allocations[0].bankAccountName", is("Checking")))
                .andExpect(jsonPath("$.allocations[0].amount", is(250.00)));
    }

    // ---------- update ----------

    @Test
    void shouldUpdateGoalDetails() throws Exception {
        UUID goal = createGoal(new CreateSavingsGoalRequest("Old name", null, null, null));

        mockMvc.perform(put("/api/savings-goals/" + goal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateSavingsGoalRequest("New name", new BigDecimal("5000.00"), null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("New name")))
                .andExpect(jsonPath("$.targetAmount", is(5000.00)));
    }

    // ---------- error handling ----------

    @Test
    void shouldReturn404ForMissingGoal() throws Exception {
        mockMvc.perform(get("/api/savings-goals/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectMutationsOnArchivedGoal() throws Exception {
        UUID account = createAccount("Checking", "1000.00");
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));

        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/savings-goals/" + goal)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new UpdateSavingsGoalRequest("x", null, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/savings-goals/" + goal + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AllocateRequest(account, new BigDecimal("10.00")))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/savings-goals/" + goal + "/archive"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn404WhenAllocatingToMissingAccount() throws Exception {
        UUID goal = createGoal(new CreateSavingsGoalRequest("Goal", null, null, null));

        mockMvc.perform(post("/api/savings-goals/" + goal + "/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new AllocateRequest(UUID.randomUUID(), new BigDecimal("10.00")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldValidateRequiredGoalName() throws Exception {
        mockMvc.perform(post("/api/savings-goals")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateSavingsGoalRequest("", null, null, null))))
                .andExpect(status().isBadRequest());
    }
}
