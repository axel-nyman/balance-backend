package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.dtos.BankAccountDtos.CreateBankAccountRequest;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.TransferPlan;
import org.example.axelnyman.main.domain.utils.TransferCalculationUtils;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end correctness suite for the lock-time transfer algorithm (item 100,
 * promoted from sprint-5 Story 32).
 *
 * <p>Locking a balanced budget runs the greedy {@link TransferCalculationUtils}
 * to compute the minimal set of inter-account transfers that settle each
 * account's planned net position (income − expenses − savings), and emits them
 * as {@code TRANSFER} todo items. This suite drives the real
 * {@code POST budget → add lines → PUT /lock → GET todo-list} path and asserts
 * the emitted plan is self-consistent: no self-transfers, no cancelling cycles,
 * conservation (per-account out − in equals the account's net position, and
 * total out equals total in), and transfer-count minimality (≤ n−1 for n
 * non-zero accounts, asserted exactly for a dominant-hub fixture).
 *
 * <p><b>Scope note (interpretation recorded in PR for item 100):</b> the lock
 * flow does <em>not</em> auto-apply transfers to account balances — the
 * transfers are a manual todo list the couple executes in their real bank. The
 * only balance mutation on lock is crediting each account's savings (written as
 * AUTOMATIC balance history; covered by other suites). Conservation is therefore
 * asserted on the transfer <em>plan</em>, which is the mathematically meaningful
 * invariant and exactly what "cover each account's planned net position" means.
 * No production code is changed by this suite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class TransferAlgorithmE2ETest {

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
        balanceHistoryRepository.deleteAll();
        bankAccountRepository.deleteAll();
    }

    @AfterAll
    static void cleanup() {
        if (postgreSQLContainer != null && postgreSQLContainer.isRunning()) {
            postgreSQLContainer.stop();
        }
    }

    // ==================== Dominant hub (Story 32 Test 5) ====================

    @Test
    void shouldCalculateCorrectTransfersWhenOneAccountDominatesAllActivity() throws Exception {
        // Main account funds four spending accounts; the classic single-hub layout.
        // Net: A = 10000 income − 2000 savings = +8000; B −2000; C −3000; D −1500; E −1500.
        UUID main = createAccount("Main");
        UUID bills = createAccount("Bills");
        UUID rent = createAccount("Rent");
        UUID groceries = createAccount("Groceries");
        UUID gas = createAccount("Gas");
        UUID budgetId = createBudget(6, 2024);

        addIncome(budgetId, main, "Salary", "10000.00");
        addSavings(budgetId, main, "Emergency", "2000.00");
        addExpense(budgetId, bills, "Bills", "2000.00");
        addExpense(budgetId, rent, "Rent", "3000.00");
        addExpense(budgetId, groceries, "Groceries", "1500.00");
        addExpense(budgetId, gas, "Gas", "1500.00");

        List<Transfer> transfers = lockAndGetTransfers(budgetId);

        Map<UUID, BigDecimal> net = new HashMap<>();
        net.put(main, new BigDecimal("8000.00"));
        net.put(bills, new BigDecimal("-2000.00"));
        net.put(rent, new BigDecimal("-3000.00"));
        net.put(groceries, new BigDecimal("-1500.00"));
        net.put(gas, new BigDecimal("-1500.00"));

        assertNoSelfTransfers(transfers);
        assertNoCancellingPairs(transfers);
        assertConservation(transfers, net);

        // Exactly n−1 = 4 transfers, all sourced from the hub account.
        assertThat(transfers).hasSize(4);
        assertThat(transfers).allMatch(t -> t.from().equals(main));
        assertThat(amountTo(transfers, bills)).isEqualByComparingTo("2000.00");
        assertThat(amountTo(transfers, rent)).isEqualByComparingTo("3000.00");
        assertThat(amountTo(transfers, groceries)).isEqualByComparingTo("1500.00");
        assertThat(amountTo(transfers, gas)).isEqualByComparingTo("1500.00");
    }

    // ==================== Net-zero / self-transfer (Story 32 Test 2) ====================

    @Test
    void shouldNeverGenerateSelfTransfersWhenAnAccountIsNetZero() throws Exception {
        // A settles on itself (net 0) and must not appear in any transfer.
        // Net: A = 500 − 300 − 200 = 0; B = 1000 − 600 = +400; C = −400.
        UUID a = createAccount("A");
        UUID b = createAccount("B");
        UUID c = createAccount("C");
        UUID budgetId = createBudget(6, 2024);

        addIncome(budgetId, a, "A income", "500.00");
        addExpense(budgetId, a, "A expense", "300.00");
        addSavings(budgetId, a, "A savings", "200.00");
        addIncome(budgetId, b, "B income", "1000.00");
        addSavings(budgetId, b, "B savings", "600.00");
        addExpense(budgetId, c, "C expense", "400.00");

        List<Transfer> transfers = lockAndGetTransfers(budgetId);

        Map<UUID, BigDecimal> net = new HashMap<>();
        net.put(a, new BigDecimal("0.00"));
        net.put(b, new BigDecimal("400.00"));
        net.put(c, new BigDecimal("-400.00"));

        assertNoSelfTransfers(transfers);
        assertNoCancellingPairs(transfers);
        assertConservation(transfers, net);

        // Only B→C $400; the net-zero account A is excluded entirely.
        assertThat(transfers).hasSize(1);
        assertThat(transfers.get(0).from()).isEqualTo(b);
        assertThat(transfers.get(0).to()).isEqualTo(c);
        assertThat(transfers.get(0).amount()).isEqualByComparingTo("400.00");
        assertThat(transfers).noneMatch(t -> t.from().equals(a) || t.to().equals(a));
    }

    // ============ Circular prevention / complex web (Story 32 Test 1) ============

    @Test
    void shouldNeverGenerateCircularTransfersInComplexMultiAccountScenarios() throws Exception {
        // Two surplus, four deficit accounts with tie amounts — a layout that a
        // buggy graph algorithm could turn circular. Net: A +200, B +200, C −150,
        // D −150, E −50, F −50 (total surplus 400 = total deficit 400).
        UUID a = createAccount("A");
        UUID b = createAccount("B");
        UUID c = createAccount("C");
        UUID d = createAccount("D");
        UUID e = createAccount("E");
        UUID f = createAccount("F");
        UUID budgetId = createBudget(6, 2024);

        addIncome(budgetId, a, "A income", "1000.00");
        addExpense(budgetId, a, "A expense", "200.00");
        addSavings(budgetId, a, "A savings", "600.00");
        addIncome(budgetId, b, "B income", "500.00");
        addExpense(budgetId, b, "B expense", "300.00");
        addExpense(budgetId, c, "C expense", "150.00");
        addExpense(budgetId, d, "D expense", "150.00");
        addExpense(budgetId, e, "E expense", "50.00");
        addExpense(budgetId, f, "F expense", "50.00");

        List<Transfer> transfers = lockAndGetTransfers(budgetId);

        Map<UUID, BigDecimal> net = new HashMap<>();
        net.put(a, new BigDecimal("200.00"));
        net.put(b, new BigDecimal("200.00"));
        net.put(c, new BigDecimal("-150.00"));
        net.put(d, new BigDecimal("-150.00"));
        net.put(e, new BigDecimal("-50.00"));
        net.put(f, new BigDecimal("-50.00"));

        assertNoSelfTransfers(transfers);
        assertNoCancellingPairs(transfers);
        assertConservation(transfers, net);
        assertAcyclic(transfers);

        // 6 accounts with non-zero net → at most n−1 = 5 transfers.
        assertThat(transfers.size()).isLessThanOrEqualTo(5);
        // Every transfer flows surplus → deficit (never the reverse).
        assertThat(transfers).allMatch(t ->
                net.get(t.from()).compareTo(BigDecimal.ZERO) > 0
                        && net.get(t.to()).compareTo(BigDecimal.ZERO) < 0);
    }

    // ==================== All-deficit edge (Story 32 Test 4) ====================

    @Test
    void shouldReturnEmptyPlanWhenAllAccountsAreDeficit() {
        // An all-deficit budget can never balance to zero, so it cannot be locked;
        // exercise the algorithm directly (as Story 32 Test 4 specifies) to prove
        // it degrades gracefully instead of looping or throwing.
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<BudgetExpense> expenses = List.of(
                new BudgetExpense(null, a, "A", new BigDecimal("500.00"), null, null, false),
                new BudgetExpense(null, b, "B", new BigDecimal("300.00"), null, null, false),
                new BudgetExpense(null, c, "C", new BigDecimal("200.00"), null, null, false));

        List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                new Budget(6, 2024), List.of(), expenses, List.of());

        // No surplus exists to source any transfer — empty plan, no exception.
        assertThat(transfers).isEmpty();
    }

    // ---------- helpers: transfer model ----------

    private record Transfer(UUID from, UUID to, BigDecimal amount) {}

    private List<Transfer> lockAndGetTransfers(UUID budgetId) throws Exception {
        mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Transfer> transfers = new ArrayList<>();
        for (JsonNode item : objectMapper.readTree(response).get("items")) {
            if ("TRANSFER".equals(item.get("type").asText())) {
                transfers.add(new Transfer(
                        UUID.fromString(item.get("fromAccount").get("id").asText()),
                        UUID.fromString(item.get("toAccount").get("id").asText()),
                        new BigDecimal(item.get("amount").asText())));
            }
        }
        return transfers;
    }

    private static BigDecimal amountTo(List<Transfer> transfers, UUID account) {
        return transfers.stream()
                .filter(t -> t.to().equals(account))
                .map(Transfer::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------- helpers: correctness assertions ----------

    private static void assertNoSelfTransfers(List<Transfer> transfers) {
        assertThat(transfers).noneMatch(t -> t.from().equals(t.to()));
    }

    /** No pair of transfers cancels (A→B and B→A) — a trivial 2-cycle. */
    private static void assertNoCancellingPairs(List<Transfer> transfers) {
        for (Transfer x : transfers) {
            assertThat(transfers).noneMatch(y -> y.from().equals(x.to()) && y.to().equals(x.from()));
        }
    }

    /**
     * The greedy plan must exactly settle every account: for each account the
     * money it sends out minus what it receives equals its net position, and
     * globally total-out equals total-in (money is neither created nor lost).
     */
    private static void assertConservation(List<Transfer> transfers, Map<UUID, BigDecimal> net) {
        Map<UUID, BigDecimal> delta = new HashMap<>();
        BigDecimal totalOut = BigDecimal.ZERO;
        BigDecimal totalIn = BigDecimal.ZERO;
        for (Transfer t : transfers) {
            delta.merge(t.from(), t.amount(), BigDecimal::add);
            delta.merge(t.to(), t.amount().negate(), BigDecimal::add);
            totalOut = totalOut.add(t.amount());
            totalIn = totalIn.add(t.amount());
        }
        assertThat(totalOut).isEqualByComparingTo(totalIn);
        for (Map.Entry<UUID, BigDecimal> entry : net.entrySet()) {
            BigDecimal settled = delta.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            assertThat(settled)
                    .as("account %s should settle out−in = net position", entry.getKey())
                    .isEqualByComparingTo(entry.getValue());
        }
    }

    /**
     * The transfer graph must be acyclic. The greedy algorithm only ever sends
     * from surplus to deficit accounts, so no account is both a source and a
     * destination — which alone forbids any cycle.
     */
    private static void assertAcyclic(List<Transfer> transfers) {
        for (Transfer x : transfers) {
            assertThat(transfers)
                    .as("account %s is both a source and a destination", x.from())
                    .noneMatch(y -> y.to().equals(x.from()));
        }
    }

    // ---------- helpers: REST fixtures ----------

    private UUID createAccount(String name) throws Exception {
        String response = mockMvc.perform(post("/api/bank-accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateBankAccountRequest(name, null, new BigDecimal("0.00")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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

    private void addExpense(UUID budgetId, UUID accountId, String name, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("bankAccountId", accountId.toString());
        body.put("name", name);
        body.put("amount", new BigDecimal(amount));
        body.put("isManual", false);
        mockMvc.perform(post("/api/budgets/" + budgetId + "/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }

    private void addSavings(UUID budgetId, UUID accountId, String name, String amount) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("bankAccountId", accountId.toString());
        body.put("name", name);
        body.put("amount", new BigDecimal(amount));
        mockMvc.perform(post("/api/budgets/" + budgetId + "/savings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());
    }
}
