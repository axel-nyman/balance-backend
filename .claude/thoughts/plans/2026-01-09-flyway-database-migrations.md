# Flyway Database Migrations Implementation Plan

## Overview

Implement Flyway database migrations to replace Hibernate's `ddl-auto` schema generation, enabling production-ready database schema management with version control, rollback capability, and safe deployment practices.

## Current State Analysis

**Current Approach (Problematic):**
- Schema generated from JPA entities via Hibernate's `ddl-auto` setting
- Default profile uses `create-drop` (destroys data on restart)
- Local profile uses `update` (incremental changes, no version control)
- Custom `import.sql` creates a partial unique index after schema generation
- No migration history or version tracking
- No rollback capability
- Risk of data loss in production deployments

**Existing Entities (9 total):**
| Entity | Table | Key Features |
|--------|-------|--------------|
| BankAccount | `bank_accounts` | Soft delete, auditing |
| BalanceHistory | `balance_history` | Immutable records |
| RecurringExpense | `recurring_expenses` | Soft delete, tracks usage |
| Budget | `budgets` | Unique (month, year, deleted_at) |
| BudgetIncome | `budget_income` | FK to Budget, BankAccount |
| BudgetExpense | `budget_expenses` | FK to Budget, BankAccount, RecurringExpense |
| BudgetSavings | `budget_savings` | FK to Budget, BankAccount |
| TodoList | `todo_lists` | Unique budget_id |
| TodoItem | `todo_items` | FK to TodoList, BankAccount (from/to) |

**Key Discoveries:**
- All entities use UUID primary keys with `GenerationType.UUID`
- Monetary values use `NUMERIC(19,2)` (BigDecimal precision=19, scale=2)
- Enums stored as VARCHAR (STRING strategy)
- Auditing timestamps via `@CreatedDate` and `@LastModifiedDate`
- Soft delete pattern uses `deleted_at` column
- Custom partial unique index on `bank_accounts.name` WHERE `deleted_at IS NULL`

## Desired End State

After this plan is complete:
1. Flyway manages all database schema changes through versioned SQL migrations
2. Hibernate validates schema against entities but does not modify it (`ddl-auto=validate`)
3. All profiles (local, docker, test) use Flyway for schema creation
4. Migration history is tracked in `flyway_schema_history` table
5. Future schema changes are made through new migration files, not entity modifications
6. Existing `import.sql` functionality is integrated into the baseline migration

**Verification:**
- Application starts successfully with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- Tests pass: `./mvnw test`
- Flyway history table exists: `SELECT * FROM flyway_schema_history`
- All 9 tables created with correct schema
- Partial unique index exists on `bank_accounts.name`

## What We're NOT Doing

- **Not implementing rollback scripts** - Flyway Community edition doesn't support automatic rollbacks. Manual intervention required for rollbacks.
- **Not adding Flyway callbacks** - Keep configuration minimal for now
- **Not implementing data migrations** - This plan only handles schema, not data transformation
- **Not using Liquibase** - Flyway chosen for simplicity and SQL-native approach

## Implementation Approach

**Strategy:** Generate a baseline migration from current entity definitions, configure Flyway, and update all profiles to use `ddl-auto=validate`.

**Why Flyway over Liquibase:**
- SQL-based migrations (simpler, matches existing `import.sql` approach)
- Excellent Spring Boot auto-configuration
- Simpler learning curve for team
- Sufficient for single-database PostgreSQL application

---

## Phase 1: Add Flyway Dependency

### Overview
Add Flyway Maven dependency with Spring Boot auto-configuration support.

### Changes Required:

#### 1. Maven Dependency
**File**: `pom.xml`
**Changes**: Add Flyway starter dependency after the JPA dependency

```xml
<!-- After spring-boot-starter-data-jpa -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>

<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Note:** `flyway-core` version is managed by Spring Boot parent (3.4.11). The `flyway-database-postgresql` module is required for PostgreSQL-specific features.

### Success Criteria:

#### Automated Verification:
- [x] Maven resolves dependencies: `./mvnw dependency:resolve`
- [x] No compilation errors: `./mvnw compile`

#### Manual Verification:
- [x] None required for this phase

---

## Phase 2: Create Baseline Migration

### Overview
Create the initial migration script that establishes the complete database schema matching current JPA entities.

### Changes Required:

#### 1. Create Migration Directory
**Path**: `src/main/resources/db/migration/`

#### 2. Baseline Migration Script
**File**: `src/main/resources/db/migration/V1__baseline_schema.sql`
**Changes**: Complete schema creation matching all JPA entities

```sql
-- V1__baseline_schema.sql
-- Baseline migration: Creates all tables for the budgeting application
-- Generated from JPA entity definitions on 2026-01-09

-- ============================================
-- STANDALONE TABLES (No Foreign Key Dependencies)
-- ============================================

-- Bank Accounts
CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500),
    current_balance NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Partial unique index: enforce unique names only for non-deleted bank accounts
CREATE UNIQUE INDEX idx_bank_accounts_name_active
    ON bank_accounts (name)
    WHERE deleted_at IS NULL;

-- Recurring Expenses
CREATE TABLE recurring_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    recurrence_interval VARCHAR(50) NOT NULL,
    is_manual BOOLEAN NOT NULL,
    last_used_date TIMESTAMP,
    last_used_budget_id UUID,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP
);

-- Budgets
CREATE TABLE budgets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    month INTEGER NOT NULL,
    year INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT uk_budgets_month_year_deleted UNIQUE (month, year, deleted_at)
);

-- ============================================
-- DEPENDENT TABLES (Have Foreign Key References)
-- ============================================

-- Balance History (references bank_accounts, budgets)
CREATE TABLE balance_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id UUID NOT NULL,
    balance NUMERIC(19, 2) NOT NULL,
    change_amount NUMERIC(19, 2) NOT NULL,
    change_date DATE NOT NULL,
    comment VARCHAR(500),
    source VARCHAR(50) NOT NULL,
    budget_id UUID,
    created_at TIMESTAMP NOT NULL
);

-- Budget Income (references budgets, bank_accounts)
CREATE TABLE budget_income (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_income_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_income_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
);

-- Budget Expenses (references budgets, bank_accounts, recurring_expenses)
CREATE TABLE budget_expenses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    recurring_expense_id UUID,
    deducted_at DATE,
    is_manual BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_expenses_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_expenses_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT fk_budget_expenses_recurring_expense FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expenses(id)
);

-- Budget Savings (references budgets, bank_accounts)
CREATE TABLE budget_savings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL,
    bank_account_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_budget_savings_budget FOREIGN KEY (budget_id) REFERENCES budgets(id),
    CONSTRAINT fk_budget_savings_bank_account FOREIGN KEY (bank_account_id) REFERENCES bank_accounts(id)
);

-- Todo Lists (references budgets)
CREATE TABLE todo_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_todo_lists_budget FOREIGN KEY (budget_id) REFERENCES budgets(id)
);

-- Todo Items (references todo_lists, bank_accounts)
CREATE TABLE todo_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    todo_list_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    from_account_id UUID,
    to_account_id UUID,
    created_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_todo_items_todo_list FOREIGN KEY (todo_list_id) REFERENCES todo_lists(id),
    CONSTRAINT fk_todo_items_from_account FOREIGN KEY (from_account_id) REFERENCES bank_accounts(id),
    CONSTRAINT fk_todo_items_to_account FOREIGN KEY (to_account_id) REFERENCES bank_accounts(id)
);

-- ============================================
-- INDEXES FOR PERFORMANCE
-- ============================================

-- Foreign key indexes (PostgreSQL doesn't auto-index FKs)
CREATE INDEX idx_balance_history_bank_account ON balance_history(bank_account_id);
CREATE INDEX idx_balance_history_budget ON balance_history(budget_id);
CREATE INDEX idx_budget_income_budget ON budget_income(budget_id);
CREATE INDEX idx_budget_income_bank_account ON budget_income(bank_account_id);
CREATE INDEX idx_budget_expenses_budget ON budget_expenses(budget_id);
CREATE INDEX idx_budget_expenses_bank_account ON budget_expenses(bank_account_id);
CREATE INDEX idx_budget_expenses_recurring ON budget_expenses(recurring_expense_id);
CREATE INDEX idx_budget_savings_budget ON budget_savings(budget_id);
CREATE INDEX idx_budget_savings_bank_account ON budget_savings(bank_account_id);
CREATE INDEX idx_todo_items_todo_list ON todo_items(todo_list_id);
CREATE INDEX idx_todo_items_from_account ON todo_items(from_account_id);
CREATE INDEX idx_todo_items_to_account ON todo_items(to_account_id);

-- Soft delete queries optimization
CREATE INDEX idx_bank_accounts_deleted ON bank_accounts(deleted_at);
CREATE INDEX idx_recurring_expenses_deleted ON recurring_expenses(deleted_at);
CREATE INDEX idx_budgets_deleted ON budgets(deleted_at);
```

### Success Criteria:

#### Automated Verification:
- [x] SQL syntax is valid (will be verified when Flyway runs)
- [x] File exists at correct path: `ls src/main/resources/db/migration/V1__baseline_schema.sql`

#### Manual Verification:
- [x] Review SQL matches entity definitions

---

## Phase 3: Update Configuration Files

### Overview
Configure Flyway in all application profiles and change `ddl-auto` from `create-drop`/`update` to `validate`.

### Changes Required:

#### 1. Default Configuration
**File**: `src/main/resources/application.yml`
**Changes**: Add Flyway configuration, change `ddl-auto` to `validate`

```yaml
spring:
  application:
    name: budgeting-app-backend
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/mydatabase}
    username: ${DATABASE_USERNAME:user}
    password: ${DATABASE_PASSWORD:password}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
```

#### 2. Local Development Configuration
**File**: `src/main/resources/application-local.yml`
**Changes**: Update `ddl-auto` to `validate`, add Flyway configuration

```yaml
spring:
  config:
    activate:
      on-profile: local

  datasource:
    url: jdbc:postgresql://localhost:5432/mydatabase
    username: user
    password: password
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    clean-disabled: false  # Allow clean in local dev (NEVER enable in production)

logging:
  level:
    org.springframework.web: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.flywaydb: DEBUG

server:
  port: 8080
```

#### 3. Docker Configuration
**File**: `src/main/resources/application-docker.yml`
**Changes**: Update `ddl-auto` to `validate`, add Flyway configuration

```yaml
spring:
  config:
    activate:
      on-profile: docker

  application:
    name: budgeting-app-backend

  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    clean-disabled: true  # NEVER allow clean in Docker/production
```

#### 4. Test Configuration
**File**: `src/test/resources/application-test.yml`
**Changes**: Configure Flyway for test containers

```yaml
server:
  port: 8080

spring:
  application:
    name: budgeting-app-backend

  datasource:
    url: jdbc:tc:postgresql:15-alpine:///testdb
    driver-class-name: org.testcontainers.jdbc.ContainerDatabaseDriver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
    clean-disabled: false  # Allow clean for test isolation

logging:
  level:
    org.example.axelnyman.main: DEBUG
    org.springframework.security: DEBUG
```

#### 5. Remove Legacy Import SQL
**File**: `src/main/resources/import.sql`
**Changes**: Delete this file (its content has been integrated into V1__baseline_schema.sql)

### Success Criteria:

#### Automated Verification:
- [x] Application starts with local profile: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- [x] All tests pass: `./mvnw test`
- [x] Flyway migration runs: Check logs for "Successfully applied 1 migration"

#### Manual Verification:
- [x] `flyway_schema_history` table exists in database
- [x] All 9 entity tables created correctly
- [x] Partial unique index on `bank_accounts.name` exists

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation from the human that the database schema was created correctly before proceeding.

---

## Phase 4: Clean Up Local Development Database

### Overview
For existing local development environments, the database needs to be reset to use Flyway-managed schema.

### Changes Required:

#### 1. Reset Local Database
**Commands** (run via terminal):

```bash
# Stop any running application
# Then reset the Docker database
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d

# Wait for database to be ready
sleep 5

# Start application - Flyway will create schema
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Success Criteria:

#### Automated Verification:
- [x] Application starts without errors
- [x] Database contains `flyway_schema_history` table with one entry

#### Manual Verification:
- [x] Verify via Adminer (http://localhost:8081) that all tables exist
- [x] Confirm `flyway_schema_history` shows version "1" as applied

---

## Phase 5: Update Documentation

### Overview
Update CLAUDE.md with the new database migration workflow.

### Changes Required:

#### 1. Update CLAUDE.md
**File**: `CLAUDE.md`
**Changes**: Add Database Migrations section, update Configuration section

Add new section after "## Configuration":

```markdown
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
```

Update the "Configuration" section to reflect new DDL behavior:

```markdown
**Profiles:**
- `local`: Development mode, verbose logging, `ddl-auto=validate`, Flyway enabled
- Default: Environment variables, `ddl-auto=validate`, Flyway enabled
- Docker: Environment variables, `ddl-auto=validate`, Flyway clean disabled
- Test: Testcontainers, `ddl-auto=validate`, Flyway enabled
```

### Success Criteria:

#### Automated Verification:
- [x] Documentation file updated correctly

#### Manual Verification:
- [ ] Review documentation accuracy
- [ ] Verify workflow instructions are clear

---

## Phase 6: Update Entity Annotations (Optional Cleanup)

### Overview
Since Flyway now manages schema, entity column name annotations should match the migration exactly to ensure validation passes.

### Changes Required:

#### 1. Review and Fix Column Name Mappings
Several entities use camelCase field names that Hibernate auto-converts to snake_case. Explicitly mapping ensures consistency.

**Files to review** (if validation errors occur):
- `BudgetIncome.java` - `budgetId` → `budget_id`, `bankAccountId` → `bank_account_id`
- `BudgetExpense.java` - `budgetId` → `budget_id`, `bankAccountId` → `bank_account_id`, `recurringExpenseId` → `recurring_expense_id`
- `BudgetSavings.java` - `budgetId` → `budget_id`, `bankAccountId` → `bank_account_id`
- `TodoList.java` - `budgetId` → `budget_id`
- `TodoItem.java` - `todoListId` → `todo_list_id`, `fromAccountId` → `from_account_id`, `toAccountId` → `to_account_id`
- `BalanceHistory.java` - `bankAccountId` → `bank_account_id`, `changeAmount` → `change_amount`, `changeDate` → `change_date`, `budgetId` → `budget_id`
- `BankAccount.java` - `currentBalance` → `current_balance`, `createdAt` → `created_at`, `updatedAt` → `updated_at`, `deletedAt` → `deleted_at`
- `RecurringExpense.java` - `recurrenceInterval` → `recurrence_interval`, `isManual` → `is_manual`, `lastUsedDate` → `last_used_date`, `lastUsedBudgetId` → `last_used_budget_id`

**Example fix** for `BudgetIncome.java`:
```java
@Column(name = "budget_id", nullable = false)
private UUID budgetId;

@Column(name = "bank_account_id", nullable = false)
private UUID bankAccountId;
```

**Note:** This phase is conditional. If `ddl-auto=validate` passes without changes, no entity modifications are needed. Hibernate's default naming strategy should handle camelCase to snake_case conversion.

### Success Criteria:

#### Automated Verification:
- [x] Application starts without validation errors: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- [x] All tests pass: `./mvnw test`

#### Manual Verification:
- [x] None required if automated verification passes (no entity changes needed)

---

## Testing Strategy

### Unit Tests:
- No new unit tests required (configuration change, not business logic)

### Integration Tests:
- All existing integration tests validate schema correctness
- Tests use Testcontainers which will run Flyway migrations

### Manual Testing Steps:
1. Stop any running application instances
2. Reset local database: `docker-compose -f docker-compose.dev.yml down -v && docker-compose -f docker-compose.dev.yml up -d`
3. Start application: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
4. Verify Flyway ran: Check logs for "Successfully applied 1 migration"
5. Verify tables via Adminer (http://localhost:8081):
   - 9 entity tables exist
   - `flyway_schema_history` table exists with 1 row
   - Partial unique index on `bank_accounts.name` exists
6. Run full test suite: `./mvnw test`
7. Create a test bank account via API to verify CRUD operations work

## Performance Considerations

- Flyway migrations run once on application startup
- Migration execution adds ~1-2 seconds to startup time
- No runtime performance impact after startup
- Consider adding migration checksums validation in production

## Migration Notes

### For Existing Local Development:
1. Drop existing database (data will be lost)
2. Restart database container
3. Start application (Flyway creates schema)

### For Production Deployment (Future):
1. Ensure `clean-disabled: true` in production config
2. First deployment: Flyway creates all tables from V1
3. Subsequent deployments: Flyway applies only new migrations
4. Consider blue-green deployment for zero-downtime migrations

### Rollback Strategy:
Flyway Community doesn't support automatic rollbacks. For each migration:
1. Document manual rollback SQL in comments
2. Create a new "fix" migration (V{n+1}__fix_xxx.sql) to correct issues
3. For critical failures, restore from database backup

## References

- Original research: `.claude/thoughts/research/2026-01-09-database-connection-initialization.md`
- Entity classes: `src/main/java/org/example/axelnyman/main/domain/model/`
- Configuration files: `src/main/resources/application*.yml`
- Spring Boot Flyway documentation: https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool
- Flyway documentation: https://documentation.red-gate.com/fd

## Sources

- [Liquibase vs Flyway | Baeldung](https://www.baeldung.com/liquibase-vs-flyway)
- [Flyway vs Liquibase 2025 | Bytebase](https://www.bytebase.com/blog/flyway-vs-liquibase/)
- [Database Migrations with Liquibase and Flyway | Medium](https://danianepg.medium.com/database-migrations-with-liquibase-and-flyway-5946379c7738)
