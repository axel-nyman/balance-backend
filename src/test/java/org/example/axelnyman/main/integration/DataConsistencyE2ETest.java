package org.example.axelnyman.main.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.axelnyman.main.domain.model.BalanceHistory;
import org.example.axelnyman.main.domain.model.BalanceHistorySource;
import org.example.axelnyman.main.domain.model.BankAccount;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Data-consistency and audit-trail integrity E2E tests (item 130, promoting
 * sprint-5 Story 30).
 *
 * <p>Every money write lands on {@code BankAccount.currentBalance} plus the
 * {@code BalanceHistory} audit trail, and the risky spots are the interleaved
 * ones: manual corrections between lock and unlock, repeated lock/unlock
 * cycles, and accounts deleted while a locked budget still references them.
 * These tests drive the real REST endpoints and pin down the actual observable
 * behaviour as regression protection. No production code is changed by this
 * item; divergences from Story 30's imagined implementation are documented
 * here and reported in the PR, not fixed (item 100's contract).
 *
 * <p><b>Interpretation decisions</b> (Story 30 was written before the unlock
 * design settled):
 *
 * <ul>
 * <li><b>Unlock deletes lock history instead of appending reversal entries.</b>
 * Story 30 Test 3 expects a four-entry history ending in an AUTOMATIC
 * "unlock −100" reversal. The app instead <em>removes</em> the AUTOMATIC lock
 * entries on unlock ({@code reverseBalanceChanges} + delete-by-budgetId), by
 * design, so a relock cannot duplicate them. The money-critical property (the
 * balance itself) matches Story 30 exactly; the history assertions here pin
 * the actual delete-on-unlock shape. Consequence worth knowing: immediately
 * after an unlock that had an interleaved manual correction, the newest
 * remaining history entry predates the reversal and its {@code balance} no
 * longer equals {@code currentBalance} — asserted below as current behaviour
 * and reported in the PR as an audit-trail gap for the maintainer to judge.
 * <li><b>Future-dated entries cannot be created through the API.</b> A manual
 * balance update with a future date is rejected (403, {@code
 * FutureDateException}), so Story 30 Test 4's future-dated rows are seeded
 * directly through the repository; the API-level ordering/pagination contract
 * is then asserted over them, plus the 403 itself.
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
public class DataConsistencyE2ETest {

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

        // ==================== Story 30 Test 1: no balance/history divergence ====================

        @Test
        void shouldKeepBalanceConsistentWithHistoryWhenManyMixedOperationsOccur() throws Exception {
                // Given: 11 mixed operations on one account — creation, 7 manual updates,
                // two budget locks and one unlock interleaved between them.
                UUID account = createAccount("Checking", "1000.00");

                updateBalance(account, "1100.00", "Correction 1");
                updateBalance(account, "1050.00", "Correction 2");
                updateBalance(account, "1200.00", "Correction 3");

                String budget1 = createBudget(1, 2031);
                addIncome(budget1, account, "Salary", "100.00");
                addSavings(budget1, account, "Buffer", "100.00");
                lock(budget1); // 1200 → 1300

                updateBalance(account, "1250.00", "Correction 4");
                updateBalance(account, "1400.00", "Correction 5");

                String budget2 = createBudget(2, 2031);
                addIncome(budget2, account, "Salary", "200.00");
                addSavings(budget2, account, "Buffer", "200.00");
                lock(budget2); // 1400 → 1600
                unlock(budget2); // 1600 → 1400, lock entry removed

                updateBalance(account, "1500.00", "Correction 6");
                updateBalance(account, "1450.00", "Correction 7");

                // When
                BigDecimal currentBalance = getCurrentBalance(account);
                List<JsonNode> history = getAllHistory(account);

                // Then: the balance equals the newest history entry and the whole chain
                // is coherent — unlock restored exactly the state the deleted lock entry
                // had added, so the surviving chain still adds up.
                assertThat(currentBalance).isEqualByComparingTo("1450.00");
                assertThat(history).hasSize(9); // 11 operations − unlock deletes budget2's lock entry
                assertThat(history.get(0).get("balance").decimalValue()).isEqualByComparingTo("1450.00");
                assertChainCoherent(history);
        }

        // ==================== Story 30 Test 2: unlock with a deleted account ====================

        @Test
        void shouldUnlockCleanlyWhenReferencedAccountWasDeletedAfterLock() throws Exception {
                // Given: a locked budget saving to three accounts, one deleted after lock
                // (allowed — the delete guard only blocks accounts in UNLOCKED budgets).
                UUID a = createAccount("A", "1000.00");
                UUID b = createAccount("B", "1000.00");
                UUID c = createAccount("C", "1000.00");

                String budgetId = createBudget(3, 2031);
                addIncome(budgetId, a, "Salary", "600.00");
                addSavings(budgetId, a, "A savings", "200.00");
                addSavings(budgetId, b, "B savings", "200.00");
                addSavings(budgetId, c, "C savings", "200.00");
                lock(budgetId); // all three: 1000 → 1200

                mockMvc.perform(delete("/api/bank-accounts/" + b))
                                .andExpect(status().isNoContent());

                // When: unlock succeeds despite the soft-deleted account.
                unlock(budgetId);

                // Then: the surviving accounts are restored through the API...
                assertThat(getCurrentBalance(a)).isEqualByComparingTo("1000.00");
                assertThat(getCurrentBalance(c)).isEqualByComparingTo("1000.00");

                // ...and the soft-deleted account's balance is reversed too (visible
                // only via the repository — the API hides deleted accounts).
                BankAccount deletedB = bankAccountRepository.findById(b).orElseThrow();
                assertThat(deletedB.getDeletedAt()).isNotNull();
                assertThat(deletedB.getCurrentBalance()).isEqualByComparingTo("1000.00");

                // No orphaned automatic entries remain for the unlocked budget, on any
                // account; B keeps only its initial MANUAL entry.
                assertThat(balanceHistoryRepository.findAllByBudgetIdAndSource(
                                UUID.fromString(budgetId), BalanceHistorySource.AUTOMATIC)).isEmpty();
                List<BalanceHistory> bHistory = balanceHistoryRepository.findAll().stream()
                                .filter(h -> h.getBankAccountId().equals(b))
                                .toList();
                assertThat(bHistory).hasSize(1);
                assertThat(bHistory.get(0).getSource()).isEqualTo(BalanceHistorySource.MANUAL);
        }

        // ==================== Story 30 Test 3: manual correction between lock and unlock ====================

        @Test
        void shouldPreserveManualCorrectionWhenUnlockingAfterInterleavedManualUpdate() throws Exception {
                // Given: 500 locked with 100 savings → 600, then a manual correction to 700.
                UUID account = createAccount("Checking", "500.00");

                String budgetId = createBudget(4, 2031);
                addIncome(budgetId, account, "Salary", "100.00");
                addSavings(budgetId, account, "Buffer", "100.00");
                lock(budgetId); // 500 → 600

                updateBalance(account, "700.00", "Bonus payment");

                // When
                unlock(budgetId);

                // Then: unlock subtracts only its own automatic 100 → 600; the manual
                // +100 correction survives.
                assertThat(getCurrentBalance(account)).isEqualByComparingTo("600.00");

                // Actual history shape (see class javadoc): the AUTOMATIC lock entry is
                // deleted on unlock and no reversal entry is written, so two MANUAL
                // entries remain — not Story 30's imagined four-entry sequence.
                List<JsonNode> history = getAllHistory(account);
                assertThat(history).hasSize(2);
                assertThat(history.get(0).get("source").asText()).isEqualTo("MANUAL");
                assertThat(history.get(0).get("comment").asText()).isEqualTo("Bonus payment");
                assertThat(history.get(0).get("balance").decimalValue()).isEqualByComparingTo("700.00");
                assertThat(history.get(0).get("changeAmount").decimalValue()).isEqualByComparingTo("100.00");
                assertThat(history.get(1).get("comment").asText()).isEqualTo("Initial balance");
                assertThat(history.get(1).get("balance").decimalValue()).isEqualByComparingTo("500.00");

                // Known audit-trail gap, pinned deliberately: right after this unlock the
                // newest surviving entry still reads 700 while the balance is 600. If
                // this assertion starts failing, the unlock history design changed —
                // update the divergence note in the class javadoc and STATE.md.
                assertThat(history.get(0).get("balance").decimalValue())
                                .isNotEqualByComparingTo(getCurrentBalance(account));
        }

        // ==================== Story 30 Test 4: future-dated ordering across pages ====================

        @Test
        void shouldMaintainChangeDateOrderingAcrossPaginationWhenFutureDatedEntriesExist() throws Exception {
                // Given: an account whose history contains future-dated entries. The API
                // refuses to create them (asserted below), so they are seeded directly.
                UUID account = createAccount("Checking", "1000.00");

                Map<String, Object> futureUpdate = new HashMap<>();
                futureUpdate.put("newBalance", new BigDecimal("1100.00"));
                futureUpdate.put("date", LocalDate.now().plusDays(1).toString());
                futureUpdate.put("comment", "Planned deposit");
                mockMvc.perform(post("/api/bank-accounts/" + account + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(futureUpdate)))
                                .andExpect(status().isForbidden());

                balanceHistoryRepository.save(new BalanceHistory(account, new BigDecimal("1100.00"),
                                new BigDecimal("100.00"), "Seeded +1d", BalanceHistorySource.MANUAL, null,
                                LocalDate.now().plusDays(1)));
                balanceHistoryRepository.save(new BalanceHistory(account, new BigDecimal("1200.00"),
                                new BigDecimal("100.00"), "Seeded +2d", BalanceHistorySource.MANUAL, null,
                                LocalDate.now().plusDays(2)));

                String budgetId = createBudget(5, 2031);
                addIncome(budgetId, account, "Salary", "50.00");
                addSavings(budgetId, account, "Buffer", "50.00");
                lock(budgetId); // AUTOMATIC entry dated today

                // When: paginate with size 2 → two pages of two.
                JsonNode page0 = getHistoryPage(account, 0, 2);
                JsonNode page1 = getHistoryPage(account, 1, 2);

                // Then: ordering is changeDate DESC (createdAt DESC within a date),
                // intact across the page boundary.
                assertThat(page0.get("page").get("totalElements").asLong()).isEqualTo(4);
                assertThat(page0.get("page").get("totalPages").asInt()).isEqualTo(2);

                List<JsonNode> all = new ArrayList<>();
                page0.get("content").forEach(all::add);
                page1.get("content").forEach(all::add);
                assertThat(all).hasSize(4);
                assertThat(all.get(0).get("comment").asText()).isEqualTo("Seeded +2d");
                assertThat(all.get(1).get("comment").asText()).isEqualTo("Seeded +1d");
                assertThat(all.get(2).get("source").asText()).isEqualTo("AUTOMATIC"); // today's lock
                assertThat(all.get(3).get("comment").asText()).isEqualTo("Initial balance");

                for (int i = 1; i < all.size(); i++) {
                        LocalDate previous = LocalDate.parse(all.get(i - 1).get("changeDate").asText());
                        LocalDate current = LocalDate.parse(all.get(i).get("changeDate").asText());
                        assertThat(current).isBeforeOrEqualTo(previous);
                }
        }

        // ==================== Story 30 Test 5: budget linkage of automatic entries ====================

        @Test
        void shouldLinkAutomaticHistoryToOwningBudgetWhenMultipleBudgetsAffectSameAccount() throws Exception {
                // Given: three sequentially locked budgets all saving to the same account.
                UUID account = createAccount("Savings", "500.00");

                String january = createBudget(1, 2032);
                addIncome(january, account, "Salary", "100.00");
                addSavings(january, account, "Buffer", "100.00");
                lock(january); // 500 → 600

                String february = createBudget(2, 2032);
                addIncome(february, account, "Salary", "200.00");
                addSavings(february, account, "Buffer", "200.00");
                lock(february); // 600 → 800

                String march = createBudget(3, 2032);
                addIncome(march, account, "Salary", "150.00");
                addSavings(march, account, "Buffer", "150.00");
                lock(march); // 800 → 950

                // Then: three AUTOMATIC entries, each carrying its own budgetId.
                Map<String, JsonNode> automaticByBudget = new HashMap<>();
                for (JsonNode entry : getAllHistory(account)) {
                        if ("AUTOMATIC".equals(entry.get("source").asText())) {
                                automaticByBudget.put(entry.get("budgetId").asText(), entry);
                        }
                }
                assertThat(automaticByBudget).containsOnlyKeys(january, february, march);
                assertThat(automaticByBudget.get(january).get("changeAmount").decimalValue())
                                .isEqualByComparingTo("100.00");
                assertThat(automaticByBudget.get(february).get("changeAmount").decimalValue())
                                .isEqualByComparingTo("200.00");
                assertThat(automaticByBudget.get(march).get("changeAmount").decimalValue())
                                .isEqualByComparingTo("150.00");

                // When: unlocking the newest budget reverses only its own entry.
                unlock(march);

                assertThat(getCurrentBalance(account)).isEqualByComparingTo("800.00");
                List<JsonNode> remainingAutomatic = getAllHistory(account).stream()
                                .filter(e -> "AUTOMATIC".equals(e.get("source").asText()))
                                .toList();
                assertThat(remainingAutomatic).hasSize(2);
                assertThat(remainingAutomatic.stream().map(e -> e.get("budgetId").asText()))
                                .containsExactlyInAnyOrder(january, february);
        }

        // ==================== Helpers: drive the real API ====================

        private UUID createAccount(String name, String initialBalance) throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("name", name);
                request.put("description", name);
                request.put("initialBalance", new BigDecimal(initialBalance));
                String response = mockMvc.perform(post("/api/bank-accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andReturn().getResponse().getContentAsString();
                return UUID.fromString(objectMapper.readTree(response).get("id").asText());
        }

        private void updateBalance(UUID accountId, String newBalance, String comment) throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("newBalance", new BigDecimal(newBalance));
                request.put("date", LocalDate.now().toString());
                request.put("comment", comment);
                mockMvc.perform(post("/api/bank-accounts/" + accountId + "/balance")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk());
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

        private void addIncome(String budgetId, UUID accountId, String name, String amount) throws Exception {
                addLine(budgetId, "income", accountId, name, amount);
        }

        private void addSavings(String budgetId, UUID accountId, String name, String amount) throws Exception {
                addLine(budgetId, "savings", accountId, name, amount);
        }

        private void addLine(String budgetId, String path, UUID accountId, String name, String amount)
                        throws Exception {
                Map<String, Object> request = new HashMap<>();
                request.put("bankAccountId", accountId.toString());
                request.put("name", name);
                request.put("amount", new BigDecimal(amount));
                mockMvc.perform(post("/api/budgets/" + budgetId + "/" + path)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        private void lock(String budgetId) throws Exception {
                mockMvc.perform(put("/api/budgets/" + budgetId + "/lock"))
                                .andExpect(status().isOk());
        }

        private void unlock(String budgetId) throws Exception {
                mockMvc.perform(put("/api/budgets/" + budgetId + "/unlock"))
                                .andExpect(status().isOk());
        }

        private BigDecimal getCurrentBalance(UUID accountId) throws Exception {
                String response = mockMvc.perform(get("/api/bank-accounts"))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                for (JsonNode account : objectMapper.readTree(response).get("accounts")) {
                        if (accountId.toString().equals(account.get("id").asText())) {
                                return account.get("currentBalance").decimalValue();
                        }
                }
                throw new AssertionError("Account not found in list response: " + accountId);
        }

        private JsonNode getHistoryPage(UUID accountId, int page, int size) throws Exception {
                String response = mockMvc.perform(
                                get("/api/bank-accounts/" + accountId + "/balance-history")
                                                .param("page", String.valueOf(page))
                                                .param("size", String.valueOf(size)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                return objectMapper.readTree(response);
        }

        /** Full history, newest first, through the paginated API. */
        private List<JsonNode> getAllHistory(UUID accountId) throws Exception {
                List<JsonNode> entries = new ArrayList<>();
                getHistoryPage(accountId, 0, 100).get("content").forEach(entries::add);
                return entries;
        }

        // ==================== Helpers: correctness assertions ====================

        /**
         * Asserts the audit chain is coherent, oldest → newest: the first entry's
         * balance equals its changeAmount (initial entry) and every later entry's
         * balance equals the previous balance plus its changeAmount.
         */
        private void assertChainCoherent(List<JsonNode> newestFirst) {
                List<JsonNode> chain = new ArrayList<>(newestFirst);
                java.util.Collections.reverse(chain);

                assertThat(chain.get(0).get("balance").decimalValue())
                                .isEqualByComparingTo(chain.get(0).get("changeAmount").decimalValue());

                for (int i = 1; i < chain.size(); i++) {
                        BigDecimal expected = chain.get(i - 1).get("balance").decimalValue()
                                        .add(chain.get(i).get("changeAmount").decimalValue());
                        assertThat(chain.get(i).get("balance").decimalValue())
                                        .as("history entry %d must equal previous balance + changeAmount", i)
                                        .isEqualByComparingTo(expected);
                }
        }
}
