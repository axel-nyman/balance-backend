# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

Backend of **Balance**, a self-hosted budgeting app for a couple managing shared monthly finances. Spring Boot 3.4.x REST API using Java 17, PostgreSQL with JPA/Hibernate, Flyway migrations, and clean 3-layer service architecture. Maven-based project.

**There is no authentication — by design.** Balance runs on a trusted home network for two users. Do not add auth, tokens, or user entities (see Non-Goals in `product/STATE.md`).

**Product context lives in `product/`:** `STATE.md` (what the app contains today — read it first), `backlog/` (prioritized feature specs), `ROUTINE_PROMPT.md` (operating manual for the scheduled daily agent).

## Development Philosophy

This project embraces engineering excellence. Every line of code should reflect the mindset of a "1% engineer" - someone who takes complete ownership, thinks critically, and delivers exceptional quality.

**Core Principles:**
- **Excellence & Ownership**: Take full responsibility for quality and architectural integrity. Make it right, not just working.
- **Think Different**: Question assumptions. Seek optimal solutions, not just easy ones.
- **Test-Driven Development**: Tests define the contract. Write failing tests first, then implement.
- **Iterative Refinement**: Ship quickly, improve continuously.
- **Code is Communication**: Clear names and simple logic over clever tricks.
- **Continuous Learning**: Every story is an opportunity to improve.

## Development Commands

```bash
# Start database
docker-compose -f docker-compose.dev.yml up -d

# Run application (recommended for dev)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Run tests
./mvnw test
./mvnw test -Dtest=BudgetIntegrationTest
./mvnw test -Dtest=BankAccountIntegrationTest#shouldCreateBankAccountWhenValidDataProvided

# Coverage report
./mvnw clean test jacoco:report  # View at target/site/jacoco/index.html

# Adminer UI at http://localhost:8081
# API at http://localhost:8080/api
# Swagger UI at http://localhost:8080/swagger-ui.html
```

## User Story Workflow

**Ongoing product work lives in `product/backlog/`** (see `product/README.md` for the workflow). `todo/` is the historical initial build-out: sprints 0–4 are complete in `todo/done/`, and `todo/backlog/sprint-5/` holds remaining E2E-test stories. The TDD lifecycle below applies to specs from either place:

**Story Lifecycle:**
1. Select story from backlog (follow sprint order)
2. Read completely (user story, acceptance criteria, API spec, technical steps, DoD)
3. Plan test cases based on acceptance criteria
4. Implement with TDD (Red-Green-Refactor)
5. Verify DoD satisfied
6. Move story file to `todo/done/`

**TDD Implementation Steps:**
1. Write failing integration test
2. Implement following 3-layer architecture (entity → repository → DTOs → DataService → DomainService → controller)
3. Run tests until green
4. Verify all acceptance criteria
5. Move story to done: `mv todo/backlog/sprint-X/story.md todo/done/`

**Critical Rules:**
- Never partially implement - complete entire story or don't start
- Follow story order - dependencies exist within sprints
- Test everything - untested code isn't done
- One story at a time - no context switching

**Sprint Organization:**
- Sprint 0: Foundation - Bank Accounts (Stories 1-5)
- Sprint 1: Recurring Expenses (Stories 6-9)
- Sprint 2: Budget Core (Stories 10-22)
- Sprint 3: Locking & Automation (Stories 23-28)
- Sprint 4: Reporting (Story 29)

## Architecture

### 3-Layer Service Pattern (Strict)

1. **Infrastructure Layer (DataService)**: Database access only
   - Interface: `IDataService` in `domain/abstracts/`
   - Implementation: `DataService` in `infrastructure/data/services/`
   - Works with JPA entities only, returns `Entity` or `Optional<Entity>`
   - No business logic

2. **Domain Layer (DomainService)**: Business logic and DTO transformation
   - Interface: `IDomainService` in `domain/abstracts/`
   - Implementations in `domain/services/`
   - Returns DTOs (e.g. `BankAccountResponse`, `BudgetResponse`)
   - Contains business rules and validation

3. **API Layer (Controllers)**: HTTP handling only
   - Located in `api/endpoints/`
   - Delegate to the domain service
   - Handle HTTP status codes and responses

### Key Design Patterns

- **Service Separation**: DataService → DomainService → Controller. Never skip layers.
- **Entity ↔ DTO Mapping**: All mapping in extension classes (`domain/extensions/`). Use `BankAccountExtensions.toResponse(account)`, `BankAccountExtensions.toEntity(request)`.
- **Error Handling**: Specific domain exceptions caught by `GlobalExceptionHandler`.
- **Soft Deletes**: `deletedAt` field. Never hard-delete records.
- **JPA Auditing**: `@EntityListeners(AuditingEntityListener.class)` for timestamps.

### Security Model

**There is no authentication or user system — by design.** All `/api/**` endpoints are open; Spring Security is not on the classpath. Balance is deployed LAN-only for two trusted users. Do not introduce auth, JWT, or user entities — it is an explicit non-goal. (Earlier versions of this doc described a JWT flow inherited from the project template; that code never existed here.)

## Decision-Making Framework

**When to Add Abstraction:** Rule of Three - only with 3+ similar implementations.

**When to Optimize:** Measure first, optimize second. Document reasons.

**When to Refactor:**
- Code duplication required for new feature
- Pattern violation
- Method > 50 lines or cyclomatic complexity > 10
- Tests difficult to write/maintain

**When to Ask:**
- Architectural impact across stories
- Security concerns
- Performance-critical operations
- Unclear requirements
- Technical debt shortcuts

## Code Organization

```
src/main/java/org/example/axelnyman/main/
├── api/endpoints/              # REST controllers
├── domain/
│   ├── abstracts/              # Service interfaces
│   ├── dtos/                   # Request/Response DTOs with validation
│   ├── extensions/             # Entity ↔ DTO mappers
│   ├── model/                  # JPA entities
│   ├── services/               # Business logic
│   └── utils/                  # Pure utility functions (calculations, algorithms)
├── infrastructure/
│   └── data/
│       ├── context/            # JPA repositories
│       └── services/           # Data access layer (DataService)
└── shared/exceptions/          # Custom exceptions and global handler
```

**Note:** `domain/utils/` contains pure utility classes with static methods and no dependencies. Perfect for complex algorithms requiring independent unit testing.

## Testing Strategy

TDD is core practice. Tests define contracts before implementation.

**Red-Green-Refactor Cycle:**
1. **Red**: Write failing test defining desired behavior
2. **Green**: Write minimal code to pass
3. **Refactor**: Improve quality while keeping tests green

**Coverage Expectations:**
- Minimum: 80% overall
- Target: 90%+ for critical business logic
- Focus: Every acceptance criterion has test coverage

**Test Types:**
- **Integration Tests** (Primary): End-to-end behavior with `@SpringBootTest`, `@Testcontainers`, full database
- **Unit Tests**: Complex algorithms, validations, utilities (100% coverage for utils)

**Test Naming:** `should[ExpectedBehavior]When[StateUnderTest]`
- Good: `shouldCreateBankAccountWhenValidDataProvided()`
- Bad: `testCreate()`, `test1()`

**Test Structure (Given-When-Then):**
```java
@Test
void shouldCalculateTotalBalanceAcrossAllAccounts() {
    // Given - arrange test data
    createBankAccount("Checking", new BigDecimal("1000.00"));

    // When - perform action
    ResponseEntity<Response> response = restTemplate.getForEntity("/api/bank-accounts", Response.class);

    // Then - assert expectations
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().totalBalance()).isEqualByComparingTo("1000.00");
}
```

**Timestamp Comparisons:**
PostgreSQL stores timestamps with microsecond precision while Java LocalDateTime has nanosecond precision. When comparing timestamps in tests, use `matchesTimestampIgnoringNanos()` from `TestDateTimeMatchers` to avoid CI/local environment differences:
```java
.andExpect(jsonPath("$.createdAt", matchesTimestampIgnoringNanos(originalCreatedAt)));
```

## Configuration

**Profiles:**
- `local`: Development mode, verbose logging, `ddl-auto=validate`, Flyway enabled
- Default: Environment variables, `ddl-auto=validate`, Flyway enabled
- Docker: Environment variables, `ddl-auto=validate`, Flyway clean disabled
- Test: Testcontainers, `ddl-auto=validate`, Flyway enabled

**Environment Variables:**
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`

## Database Migrations

This project uses **Flyway** for database schema management. Schema changes are tracked through versioned SQL migration scripts.

### Migration File Location
`src/main/resources/db/migration/`

### Naming Convention
`V{version}__{description}.sql`
- Example: `V1__baseline_schema.sql`
- Example: `V2__add_user_preferences.sql`

### Creating New Migrations

1. Create a new file following the naming convention
2. Write idempotent SQL (use `IF NOT EXISTS` where applicable)
3. Test locally before committing
4. Migrations are automatically applied on application startup

### Commands

```bash
# View migration status (via application logs)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Reset local database (when needed)
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Important Rules

- **Never modify an applied migration** - Create a new migration instead
- **Migrations must be backward compatible** - Old code must work with new schema during deployment
- **Test migrations locally first** - Run against a fresh database
- **Include rollback comments** - Document how to manually reverse changes if needed

### Profile Behavior

| Profile | Flyway | ddl-auto | Notes |
|---------|--------|----------|-------|
| Default | enabled | validate | Production-safe |
| Local | enabled | validate | Allows `flyway:clean` |
| Docker | enabled | validate | Clean disabled |
| Test | enabled | validate | Fresh DB per test run |

## Quality Standards

**Code Review Checklist:**
- ✅ Follows 3-layer architecture (no layer skipping)
- ✅ Entities only in DataService, DTOs only in Domain/Controller
- ✅ Business logic only in Domain layer
- ✅ Custom domain exceptions, not generic RuntimeException
- ✅ DTO validation annotations present
- ✅ Names self-documenting, methods < 50 lines
- ✅ No N+1 queries (use @EntityGraph or JOIN FETCH)
- ✅ Pagination for large lists
- ✅ Input validation prevents injection

**Definition of Done:**
1. All acceptance criteria verified
2. All tests passing (80%+ coverage)
3. No compiler warnings
4. Follows project patterns
5. API documentation updated
6. Error scenarios handled
7. Spec moved to its done directory (`product/done/` for product backlog items, `todo/done/` for legacy stories) with completion notes

## Anti-Patterns to Avoid

**Architectural:**
- ❌ Controller calling DataService directly (skip DomainService)
- ❌ Business logic in Controllers or Repositories
- ❌ DTOs in DataService or Entities in Controllers
- ❌ God classes with too many responsibilities
- ❌ Validation mixed with mapping logic

**Error Handling:**
- ❌ Generic Exception catching (use specific exceptions)
- ❌ Magic numbers (use named constants)

**Testing:**
- ❌ Testing private methods (test public behavior)
- ❌ Fragile tests depending on implementation details

**Code Quality:**
- ❌ N+1 query problems (use JOIN FETCH)
- ❌ Individual saves in loops (use batch operations)

## Common Development Patterns

**Adding New Entity:**
1. Entity in `domain/model/` with `@Entity`, auditing, soft delete
2. Repository extending `JpaRepository` in `infrastructure/data/context/`
3. DTOs in `domain/dtos/` with validation
4. Extension class in `domain/extensions/` for mapping
5. Methods in `IDataService` interface and `DataService` implementation
6. Business methods in `IDomainService` and implementation
7. Controller in `api/endpoints/`
8. Integration tests

**Fetching with Error Handling:**
```java
BankAccount account = dataService.getBankAccountById(id)
    .orElseThrow(() -> new BankAccountNotFoundException("Bank account not found with id: " + id));
```

**Mapping Collections:**
```java
return dataService.getAllBankAccounts().stream()
    .map(BankAccountExtensions::toResponse)
    .toList();
```

**Controller Responses:**
```java
return ResponseEntity.status(HttpStatus.CREATED)
    .body(domainService.createBankAccount(request));

// For Optional results
return domainService.getBudget(id)
    .map(ResponseEntity::ok)
    .orElse(ResponseEntity.notFound().build());
```

**Transaction Management:**
```java
@Transactional  // For operations modifying multiple entities
public BudgetResponse lockBudget(UUID budgetId) {
    Budget budget = updateBudgetStatus(budgetId, BudgetStatus.LOCKED);
    generateTodoList(budgetId);
    updateBalancesForBudget(budgetId);
    return BudgetExtensions.toResponse(budget);  // All succeed or all fail
}
```

**Validation Layers:**
```java
// Layer 1: DTO validation (automatic)
public record CreateBankAccountRequest(
    @NotBlank(message = "Name is required") String name,
    @PositiveOrZero BigDecimal initialBalance
) {}

// Layer 2: Business validation (DomainService)
if (dataService.existsByName(request.name())) {
    throw new DuplicateBankAccountNameException("Account name already exists");
}

// Layer 3: Database constraints (Entity)
@Column(nullable = false, unique = true)
private String name;
```

**Avoiding N+1 Queries:**
```java
// Use @EntityGraph or JOIN FETCH
@EntityGraph(attributePaths = {"income", "expenses", "savings"})
List<Budget> findAll();

@Query("SELECT b FROM Budget b LEFT JOIN FETCH b.income LEFT JOIN FETCH b.expenses")
List<Budget> findAllWithDetails();
```

**BigDecimal for Money:**
```java
BigDecimal balance = new BigDecimal("1000.00");  // String constructor for precision
if (balance.compareTo(BigDecimal.ZERO) > 0) { }  // Comparisons
BigDecimal total = balance.add(amount);  // Arithmetic
BigDecimal rounded = value.setScale(2, RoundingMode.HALF_UP);  // Rounding
```

**Utility Classes (Pure Functions):**
```java
public final class TransferCalculationUtils {
    private TransferCalculationUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static List<TransferPlan> calculateTransfers(
        Budget budget, List<BudgetIncome> income,
        List<BudgetExpense> expenses, List<BudgetSavings> savings
    ) {
        // Pure function - no side effects, deterministic
    }
}
```

## Coding Style Preferences

**Favor concise, readable code with fluid chaining over verbose code with unnecessary intermediate variables.**

**Controllers:** Chain operations for single-use results
```java
// GOOD
@PostMapping
public ResponseEntity<BankAccountResponse> create(@Valid @RequestBody CreateBankAccountRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(domainService.createBankAccount(request));
}
```

**Services:** Minimize intermediate variables unless used multiple times or significantly improve clarity
```java
// GOOD - Concise
@Transactional
public BankAccountResponse createBankAccount(CreateBankAccountRequest request) {
    if (dataService.existsByBankAccountName(request.name())) {
        throw new DuplicateBankAccountNameException("Bank account name already exists");
    }

    BankAccount savedAccount = dataService.saveBankAccount(BankAccountExtensions.toEntity(request));

    dataService.saveBalanceHistory(new BalanceHistory(
        savedAccount.getId(), savedAccount.getCurrentBalance(),
        savedAccount.getCurrentBalance(), "Initial balance",
        BalanceHistorySource.MANUAL, null
    ));

    return BankAccountExtensions.toResponse(savedAccount);
}
```

**Guidelines:**
- Use intermediate variables when: used multiple times, adds clarity, aids debugging
- Avoid intermediate variables when: single-use, simple transformations, just for method arguments
- Readability first - use variables if chaining hurts comprehension
- Consistency - follow patterns in existing code
- Remove obvious comments - code should be self-documenting

## Current Package Structure

Project uses `org.example.axelnyman.main` as template. Refactor to match actual organization/project name when customizing.

---

## Summary: The 1% Engineer Approach

**Mindset:**
- Own the outcome - build systems that evolve and scale
- Think critically - question assumptions, seek optimal solutions
- Test first - define behavior through tests
- Refactor fearlessly - continuous improvement

**Process:**
1. Read entire story (acceptance criteria, API specs, technical details)
2. Write failing tests (TDD)
3. Implement methodically (3-layer architecture, clean code, error handling)
4. Verify completely (tests pass, coverage adequate, criteria met)
5. Move to done (only when DoD fully satisfied)

**Quality Gates:** All tests passing (80%+ coverage) • No warnings • Follows patterns • API documented • Edge cases handled • Criteria met

**Anti-Patterns:** Skip layers • Partial implementation • Tests after code • Broken state • Technical debt

**When in Doubt:** Check this doc • Review existing patterns • Write test to clarify • Ask if affects architecture

**Remember**: Code is read far more than written. Write for the next developer. Make it clear, correct, and excellent.
