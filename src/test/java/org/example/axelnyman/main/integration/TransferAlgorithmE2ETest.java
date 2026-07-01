package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.model.BankAccount;
import org.example.axelnyman.main.domain.model.Budget;
import org.example.axelnyman.main.domain.model.BudgetExpense;
import org.example.axelnyman.main.domain.model.BudgetIncome;
import org.example.axelnyman.main.domain.model.BudgetSavings;
import org.example.axelnyman.main.domain.model.TransferPlan;
import org.example.axelnyman.main.domain.utils.TransferCalculationUtils;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end correctness tests for the lock-time transfer algorithm
 * (item 100, promoting sprint-5 Story 32).
 *
 * <p>Locking a budget is the most safety-critical write in the app: the greedy
 * {@link TransferCalculationUtils} computes the inter-account transfers that
 * cover each account's planned net position, and those become the couple's
 * TRANSFER todo items. These tests drive the <em>real</em>
 * {@code POST budget → add lines → PUT /lock} path and then read the generated
 * todo list back through the API, asserting the transfer set is self-consistent
 * (no self-transfers, acyclic, conservation-respecting) and minimal.
 *
 * <p><b>Interpretation of the "conservation" acceptance criterion.</b> The spec
 * phrases conservation partly as "every account's post-lock balance equals its
 * pre-lock balance plus its planned net position." The app does <em>not</em>
 * behave that way by design: locking only credits each account's balance by the
 * savings set aside on it ({@code updateBalancesForBudget}); the transfers are
 * guidance the couple executes manually against their real bank, not an
 * automatic balance mutation. Asserting a literal post-lock balance would
 * therefore contradict the app's actual (correct) behaviour. Instead we assert
 * the substantive, testable property that makes the todo list correct: the
 * transfer set exactly rebalances every account, i.e. for each account
 * {@code (money transferred out − money transferred in) == its planned net
 * position}. Summed over all accounts this is zero, which is exactly
 * "total money moved out equals total moved in."
 *
 * <p>The all-deficit edge case cannot be locked (an unbalanced budget is
 * rejected at lock), so — as the original Story 32 prescribes — it exercises
 * {@link TransferCalculationUtils#calculateTransfers} directly.
 *
 * <p>No production code is changed by this item; these tests are pure
 * regression protection for the lock flow.
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
        private org.example.axelnyman.main.infrastructure.data.context.BankAccountRepository bankAccountRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetIncomeRepository budgetIncomeRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetExpenseRepository budgetExpenseRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetSavingsRepository budgetSavingsRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.TodoItemRepository todoItemRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.TodoListRepository todoListRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BalanceHistoryRepository balanceHistoryRepository;

        @Autowired
        private org.example.axelnyman.main.infrastructure.data.context.BudgetRepository budgetRepository;

        @Autowired
        private ObjectMapper objectMapper;

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
                mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

                // Clean database state between tests (order matters due to foreign keys)
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

        // ==================== Story 32 Test 1: circular-transfer prevention ====================

        @Test
        void shouldNeverGenerateCircularTransfersInComplexMultiAccountScenarios() throws Exception {
                // Given: 6 accounts arranged to tempt a buggy graph algorithm into a cycle.
                // A: 1000 income − 200 expense − 600 savings = +200
                // B:  500 income − 300 expense               = +200
                // C: −150   D: −150   E: −50   F: −50   (sum of deficits = 400 = sum of surplus)
                String budgetId = createBudget(1, 2030);
                BankAccount a = account("A", "0.00");
                BankAccount b = account("B", "0.00");
                BankAccount c = account("C", "0.00");
                BankAccount d = account("D", "0.00");
                BankAccount e = account("E", "0.00");
                BankAccount f = account("F", "0.00");

                addIncome(budgetId, a, "A income", "1000.00");
                addExpense(budgetId, a, "A expense", "200.00");
                addSavings(budgetId, a, "A savings", "600.00");
                addIncome(budgetId, b, "B income", "500.00");
                addExpense(budgetId, b, "B expense", "300.00");
                addExpense(budgetId, c, "C expense", "150.00");
                addExpense(budgetId, d, "D expense", "150.00");
                addExpense(budgetId, e, "E expense", "50.00");
                addExpense(budgetId, f, "F expense", "50.00");

                // When
                lock(budgetId);
                List<Transfer> transfers = getTransfers(budgetId);

                // Then: valid, acyclic, conservation-respecting, and no more than n−1 transfers.
                assertNoSelfTransfers(transfers);
                assertAcyclicWithNoCancellingPair(transfers);
                assertConservation(transfers, Map.of(
                                a.getId(), new BigDecimal("200.00"),
                                b.getId(), new BigDecimal("200.00"),
                                c.getId(), new BigDecimal("-150.00"),
                                d.getId(), new BigDecimal("-150.00"),
                                e.getId(), new BigDecimal("-50.00"),
                                f.getId(), new BigDecimal("-50.00")));
                assertThat(transfers.size()).isLessThanOrEqualTo(5); // 6 non-zero accounts → ≤ n−1
        }

        // ==================== Story 32 Test 2: self-transfer prevention ====================

        @Test
        void shouldNeverGenerateSelfTransfersFromAccountToItself() throws Exception {
                // Given: A is balanced on itself (net 0); only B→C is required.
                // A: 500 income − 300 expense − 200 savings = 0
                // B: 1000 income − 600 savings = +400
                // C: −400 expense
                String budgetId = createBudget(2, 2030);
                BankAccount a = account("A", "0.00");
                BankAccount b = account("B", "0.00");
                BankAccount c = account("C", "0.00");

                addIncome(budgetId, a, "A income", "500.00");
                addExpense(budgetId, a, "A expense", "300.00");
                addSavings(budgetId, a, "A savings", "200.00");
                addIncome(budgetId, b, "B income", "1000.00");
                addSavings(budgetId, b, "B savings", "600.00");
                addExpense(budgetId, c, "C expense", "400.00");

                // When
                lock(budgetId);
                List<Transfer> transfers = getTransfers(budgetId);

                // Then: exactly one transfer B→C for 400; the net-zero account never appears.
                assertNoSelfTransfers(transfers);
                assertThat(transfers).hasSize(1);
                assertThat(transfers.get(0).from()).isEqualTo(b.getId());
                assertThat(transfers.get(0).to()).isEqualTo(c.getId());
                assertThat(transfers.get(0).amount()).isEqualByComparingTo("400.00");
                assertThat(referencedAccounts(transfers)).doesNotContain(a.getId());
                assertConservation(transfers, Map.of(
                                a.getId(), new BigDecimal("0.00"),
                                b.getId(), new BigDecimal("400.00"),
                                c.getId(), new BigDecimal("-400.00")));
        }

        // ==================== Story 32 Test 3: transfer-count minimization ====================

        @Test
        void shouldOptimizeTransfersToMinimumCountWithComplexMultiAccountWeb() throws Exception {
                // Given: 8 accounts, distinct net positions (no ties → deterministic greedy).
                // A: +5000  B: +500  C: +300   D: −2000  E: −1500  F: −1200  G: −600  H: −500
                String budgetId = createBudget(3, 2030);
                BankAccount a = account("A", "0.00");
                BankAccount b = account("B", "0.00");
                BankAccount c = account("C", "0.00");
                BankAccount d = account("D", "0.00");
                BankAccount e = account("E", "0.00");
                BankAccount f = account("F", "0.00");
                BankAccount g = account("G", "0.00");
                BankAccount h = account("H", "0.00");

                addIncome(budgetId, a, "A income", "5000.00");
                addIncome(budgetId, b, "B income", "500.00");
                addIncome(budgetId, c, "C income", "300.00");
                addExpense(budgetId, d, "D expense", "2000.00");
                addExpense(budgetId, e, "E expense", "1500.00");
                addExpense(budgetId, f, "F expense", "1200.00");
                addExpense(budgetId, g, "G expense", "600.00");
                addExpense(budgetId, h, "H expense", "500.00");

                // When
                lock(budgetId);
                List<Transfer> transfers = getTransfers(budgetId);

                // Then: a valid plan using at most n−1 = 7 transfers (never one-per-deficit
                // when a large surplus can cover several), balancing every account.
                assertNoSelfTransfers(transfers);
                assertAcyclicWithNoCancellingPair(transfers);
                assertThat(transfers.size()).isLessThanOrEqualTo(7);
                assertConservation(transfers, Map.of(
                                a.getId(), new BigDecimal("5000.00"),
                                b.getId(), new BigDecimal("500.00"),
                                c.getId(), new BigDecimal("300.00"),
                                d.getId(), new BigDecimal("-2000.00"),
                                e.getId(), new BigDecimal("-1500.00"),
                                f.getId(), new BigDecimal("-1200.00"),
                                g.getId(), new BigDecimal("-600.00"),
                                h.getId(), new BigDecimal("-500.00")));
        }

        // ==================== Story 32 Test 4: all-deficit edge case ====================

        @Test
        void shouldHandleTransferCalculationWhenAllAccountsAreDeficit() {
                // Given: an intentionally unbalanced, all-deficit set. This can never be
                // locked (the lock rejects unbalanced budgets), so the algorithm is exercised
                // directly, as Story 32 prescribes.
                UUID a = UUID.randomUUID();
                UUID b = UUID.randomUUID();
                UUID c = UUID.randomUUID();
                UUID budgetId = UUID.randomUUID();

                List<BudgetIncome> income = List.of();
                List<BudgetExpense> expenses = List.of(
                                new BudgetExpense(budgetId, a, "A", new BigDecimal("500.00"), null, null, true),
                                new BudgetExpense(budgetId, b, "B", new BigDecimal("300.00"), null, null, true),
                                new BudgetExpense(budgetId, c, "C", new BigDecimal("200.00"), null, null, true));
                List<BudgetSavings> savings = List.of();

                // When
                List<TransferPlan> transfers = TransferCalculationUtils.calculateTransfers(
                                new Budget(1, 2030), income, expenses, savings);

                // Then: no transfers are possible and the algorithm terminates without error.
                assertThat(transfers).isEmpty();
        }

        // ==================== Story 32 Test 5: dominant hub account ====================

        @Test
        void shouldCalculateCorrectTransfersWhenOneAccountDominatesAllActivity() throws Exception {
                // Given: one main account funds every other account (realistic pattern).
                // A: 10000 income − 2000 savings = +8000
                // B: −2000  C: −3000  D: −1500  E: −1500  (deficits sum to 8000)
                String budgetId = createBudget(4, 2030);
                BankAccount a = account("Main Checking", "0.00");
                BankAccount b = account("Bills", "0.00");
                BankAccount c = account("Rent", "0.00");
                BankAccount d = account("Groceries", "0.00");
                BankAccount e = account("Gas", "0.00");

                addIncome(budgetId, a, "Salary", "10000.00");
                addSavings(budgetId, a, "Emergency Fund", "2000.00");
                addExpense(budgetId, b, "Bills", "2000.00");
                addExpense(budgetId, c, "Rent", "3000.00");
                addExpense(budgetId, d, "Groceries", "1500.00");
                addExpense(budgetId, e, "Gas", "1500.00");

                // When
                lock(budgetId);
                List<Transfer> transfers = getTransfers(budgetId);

                // Then: exactly 4 transfers, all sourced from A, each covering one deficit.
                assertNoSelfTransfers(transfers);
                assertAcyclicWithNoCancellingPair(transfers);
                assertThat(transfers).hasSize(4);
                assertThat(transfers).allSatisfy(t -> assertThat(t.from()).isEqualTo(a.getId()));

                Map<UUID, BigDecimal> byDestination = transfers.stream()
                                .collect(Collectors.toMap(Transfer::to, Transfer::amount));
                assertThat(byDestination.get(b.getId())).isEqualByComparingTo("2000.00");
                assertThat(byDestination.get(c.getId())).isEqualByComparingTo("3000.00");
                assertThat(byDestination.get(d.getId())).isEqualByComparingTo("1500.00");
                assertThat(byDestination.get(e.getId())).isEqualByComparingTo("1500.00");

                BigDecimal totalTransferred = transfers.stream()
                                .map(Transfer::amount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                assertThat(totalTransferred).isEqualByComparingTo("8000.00");

                assertConservation(transfers, Map.of(
                                a.getId(), new BigDecimal("8000.00"),
                                b.getId(), new BigDecimal("-2000.00"),
                                c.getId(), new BigDecimal("-3000.00"),
                                d.getId(), new BigDecimal("-1500.00"),
                                e.getId(), new BigDecimal("-1500.00")));
        }

        // ==================== Helpers: drive the real API ====================

        private record Transfer(UUID from, UUID to, BigDecimal amount) {
        }

        private String createBudget(int month, int year) throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("month", month);
                request.put("year", year);
                String response = mockMvc.perform(post("/api/budgets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response).get("id").asText();
        }

        private BankAccount account(String name, String initialBalance) {
                BankAccount account = new BankAccount();
                account.setName(name);
                account.setDescription(name);
                account.setCurrentBalance(new BigDecimal(initialBalance));
                return bankAccountRepository.save(account);
        }

        private void addIncome(String budgetId, BankAccount account, String name, String amount) throws Exception {
                addLine(budgetId, "income", account, name, amount, null);
        }

        private void addExpense(String budgetId, BankAccount account, String name, String amount) throws Exception {
                // isManual=false keeps the todo list to TRANSFER items only; expenses count
                // toward net positions regardless of the manual flag.
                addLine(budgetId, "expenses", account, name, amount, false);
        }

        private void addSavings(String budgetId, BankAccount account, String name, String amount) throws Exception {
                addLine(budgetId, "savings", account, name, amount, null);
        }

        private void addLine(String budgetId, String path, BankAccount account, String name, String amount,
                        Boolean isManual) throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("bankAccountId", account.getId().toString());
                request.put("name", name);
                request.put("amount", new BigDecimal(amount));
                if (isManual != null) {
                        request.put("isManual", isManual);
                }
                mockMvc.perform(post("/api/budgets/" + budgetId + "/" + path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        private void lock(String budgetId) throws Exception {
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());
        }

        private List<Transfer> getTransfers(String budgetId) throws Exception {
                String response = mockMvc.perform(get("/api/budgets/" + budgetId + "/todo-list"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();

                List<Transfer> transfers = new ArrayList<>();
                for (JsonNode item : objectMapper.readTree(response).get("items")) {
                        if ("TRANSFER".equals(item.get("type").asText())) {
                                transfers.add(new Transfer(
                                                UUID.fromString(item.get("fromAccount").get("id").asText()),
                                                UUID.fromString(item.get("toAccount").get("id").asText()),
                                                item.get("amount").decimalValue()));
                        }
                }
                return transfers;
        }

        // ==================== Helpers: correctness assertions ====================

        private void assertNoSelfTransfers(List<Transfer> transfers) {
                assertThat(transfers).allSatisfy(t -> assertThat(t.from()).isNotEqualTo(t.to()));
        }

        /**
         * Asserts the transfer graph contains no cancelling pair (A→B and B→A) and no
         * directed cycle at all.
         */
        private void assertAcyclicWithNoCancellingPair(List<Transfer> transfers) {
                for (Transfer x : transfers) {
                        for (Transfer y : transfers) {
                                if (x != y && x.from().equals(y.to()) && x.to().equals(y.from())) {
                                        assertThat(false)
                                                        .as("cancelling pair between %s and %s", x.from(), x.to())
                                                        .isTrue();
                                }
                        }
                }

                Map<UUID, List<UUID>> adjacency = new HashMap<>();
                Set<UUID> nodes = new HashSet<>();
                for (Transfer t : transfers) {
                        adjacency.computeIfAbsent(t.from(), k -> new ArrayList<>()).add(t.to());
                        nodes.add(t.from());
                        nodes.add(t.to());
                }

                Set<UUID> visited = new HashSet<>();
                Set<UUID> inStack = new HashSet<>();
                for (UUID node : nodes) {
                        assertThat(hasCycle(node, adjacency, visited, inStack))
                                        .as("transfer graph must be acyclic")
                                        .isFalse();
                }
        }

        private boolean hasCycle(UUID node, Map<UUID, List<UUID>> adjacency, Set<UUID> visited, Set<UUID> inStack) {
                if (inStack.contains(node)) {
                        return true;
                }
                if (visited.contains(node)) {
                        return false;
                }
                visited.add(node);
                inStack.add(node);
                for (UUID next : adjacency.getOrDefault(node, List.of())) {
                        if (hasCycle(next, adjacency, visited, inStack)) {
                                return true;
                        }
                }
                inStack.remove(node);
                return false;
        }

        /**
         * Asserts the transfer set exactly rebalances every account: money out minus
         * money in equals the account's planned net position, and the whole set is
         * balance-preserving (total moved out == total moved in). See the class-level
         * note on the "conservation" acceptance criterion.
         */
        private void assertConservation(List<Transfer> transfers, Map<UUID, BigDecimal> netPositions) {
                Map<UUID, BigDecimal> movement = new HashMap<>();
                for (Transfer t : transfers) {
                        movement.merge(t.from(), t.amount(), BigDecimal::add);
                        movement.merge(t.to(), t.amount().negate(), BigDecimal::add);
                }

                netPositions.forEach((accountId, net) -> assertThat(
                                movement.getOrDefault(accountId, BigDecimal.ZERO))
                                .as("net movement for account %s", accountId)
                                .isEqualByComparingTo(net));

                // Every account touched by a transfer must be one we accounted for.
                assertThat(netPositions.keySet()).containsAll(movement.keySet());

                // Global conservation: total moved out equals total moved in.
                assertThat(movement.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                                .isEqualByComparingTo(BigDecimal.ZERO);
        }

        private Set<UUID> referencedAccounts(List<Transfer> transfers) {
                Set<UUID> accounts = new HashSet<>();
                for (Transfer t : transfers) {
                        accounts.add(t.from());
                        accounts.add(t.to());
                }
                return accounts;
        }
}
