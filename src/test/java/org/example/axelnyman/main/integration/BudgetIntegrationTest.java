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

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .build();

        // Clean database state between tests
        budgetRepository.deleteAll();
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

    // Helper method to create budget request
    private Map<String, Object> createBudgetRequest(Integer month, Integer year) {
        Map<String, Object> request = new HashMap<>();
        request.put("month", month);
        request.put("year", year);
        return request;
    }
}
