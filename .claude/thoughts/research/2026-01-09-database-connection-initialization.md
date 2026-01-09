---
date: 2026-01-09T12:00:00+01:00
researcher: Claude
git_commit: f09f6d7f85a8de845699a906aae55b7bd1eaf5ea
branch: main
repository: balance-backend
topic: "Database Connection and Initialization"
tags: [research, database, postgresql, jpa, hibernate, schema-generation]
status: complete
last_updated: 2026-01-09
last_updated_by: Claude
---

# Research: Database Connection and Initialization

**Date**: 2026-01-09T12:00:00+01:00
**Researcher**: Claude
**Git Commit**: f09f6d7f85a8de845699a906aae55b7bd1eaf5ea
**Branch**: main
**Repository**: balance-backend

## Research Question
How does the database connection and initialization work for this app? How are database tables created? Is there a schema somewhere, or is it generated from domain models?

## Summary

This Spring Boot application uses **JPA/Hibernate entity-based schema generation** with PostgreSQL. There is **no separate schema file or migration tool** (no Flyway or Liquibase). The database schema is automatically generated from JPA entity classes using Hibernate's DDL auto-generation feature (`ddl-auto`). The exact behavior depends on the active Spring profile.

**Key findings:**
- Schema is generated from 9 JPA entity classes in `domain/model/`
- Hibernate's `ddl-auto` setting controls schema generation behavior per profile
- One `import.sql` file adds a custom partial unique index after schema creation
- PostgreSQL 15 is used, configured via Docker Compose for development
- Environment variables allow runtime configuration for production deployments

## Detailed Findings

### Schema Generation Approach

The project does **not** use traditional database migration tools. Instead:

1. **JPA entities define the schema**: Entity annotations (`@Entity`, `@Table`, `@Column`) specify table structure
2. **Hibernate generates DDL**: Based on `spring.jpa.hibernate.ddl-auto` setting
3. **Single SQL initialization file**: `import.sql` runs after schema creation for custom indexes

### DDL-Auto Settings by Profile

| Profile | DDL Setting | Behavior |
|---------|-------------|----------|
| Default | `create-drop` | Creates schema on startup, drops on shutdown |
| Local | `update` | Updates schema incrementally, preserves data |
| Docker | `create-drop` | Creates schema on startup, drops on shutdown |
| Test | `create` | Creates schema on startup (uses Testcontainers) |

### Database Configuration Files

**Main configuration**: `src/main/resources/application.yml` (lines 5-18)
- Database URL, username, password from environment variables with fallbacks
- Default `ddl-auto: create-drop`

**Local development**: `src/main/resources/application-local.yml` (lines 9-22)
- Hardcoded connection to `localhost:5432/mydatabase`
- Uses `ddl-auto: update` to preserve data between restarts
- Enables SQL logging for debugging

**Docker profile**: `src/main/resources/application-docker.yml` (lines 7-20)
- Requires environment variables (no defaults)
- Uses `ddl-auto: create-drop`

**Test profile**: `src/test/resources/application-test.yml` (lines 7-18)
- Uses Testcontainers JDBC URL: `jdbc:tc:postgresql:15-alpine:///testdb`
- Creates ephemeral PostgreSQL containers for tests

### JPA Entities (Schema Source)

Located in `src/main/java/org/example/axelnyman/main/domain/model/`:

| Entity | Table Name | Key Features |
|--------|------------|--------------|
| `BankAccount` | `bank_accounts` | Soft delete, auditing |
| `BalanceHistory` | `balance_history` | Immutable history records |
| `RecurringExpense` | `recurring_expenses` | Soft delete, tracks last usage |
| `Budget` | `budgets` | Unique constraint on (month, year, deleted_at) |
| `BudgetIncome` | `budget_income` | Foreign keys to Budget, BankAccount |
| `BudgetExpense` | `budget_expenses` | Links to RecurringExpense |
| `BudgetSavings` | `budget_savings` | Foreign keys to Budget, BankAccount |
| `TodoList` | `todo_lists` | One per budget (unique constraint) |
| `TodoItem` | `todo_items` | Transfer and payment tracking |

**Common patterns across entities:**
- UUID primary keys with `@GeneratedValue(strategy = GenerationType.UUID)`
- JPA auditing via `@EntityListeners(AuditingEntityListener.class)`
- `@CreatedDate` and `@LastModifiedDate` for timestamps
- Soft delete via `deletedAt` field (where applicable)
- BigDecimal with `precision=19, scale=2` for monetary values
- Enums stored as STRING via `@Enumerated(EnumType.STRING)`

### Custom SQL Initialization

**File**: `src/main/resources/import.sql`

```sql
-- Partial unique index: enforce unique names only for non-deleted bank accounts
CREATE UNIQUE INDEX IF NOT EXISTS idx_bank_accounts_name_active
ON bank_accounts (name) WHERE deleted_at IS NULL;
```

This file is automatically executed by Hibernate **after** schema generation. It creates a partial unique index to support the soft-delete pattern (allowing name reuse after deletion).

### Docker Database Setup

**File**: `docker-compose.dev.yml`

- **Image**: `postgres:15`
- **Container**: `budgeting-app-dev-db`
- **Port**: `5432:5432`
- **Credentials**: `user` / `password` / `mydatabase`
- **Persistence**: Named volume `postgres_dev_data`
- **Admin UI**: Adminer at `localhost:8081`

### JPA Auditing Configuration

**File**: `src/main/java/org/example/axelnyman/main/MainApplication.java` (line 8)

```java
@EnableJpaAuditing
@SpringBootApplication
public class MainApplication { ... }
```

This enables automatic population of `@CreatedDate` and `@LastModifiedDate` fields via the `AuditingEntityListener`.

## Code References

- `src/main/resources/application.yml:5-18` - Default database configuration
- `src/main/resources/application-local.yml:9-22` - Local profile configuration
- `src/main/resources/application-docker.yml:7-20` - Docker profile configuration
- `src/test/resources/application-test.yml:7-18` - Test profile with Testcontainers
- `src/main/resources/import.sql` - Custom index creation
- `src/main/java/org/example/axelnyman/main/domain/model/` - All JPA entities
- `src/main/java/org/example/axelnyman/main/MainApplication.java:8` - JPA auditing enablement
- `docker-compose.dev.yml:5-19` - PostgreSQL container configuration
- `pom.xml:32` - spring-boot-starter-data-jpa dependency

## Architecture Documentation

### Database Connection Flow

1. Spring Boot loads profile-specific configuration (`application-{profile}.yml`)
2. `spring.datasource.*` properties configure the HikariCP connection pool
3. PostgreSQL JDBC driver (`org.postgresql.Driver`) connects to the database
4. Hibernate dialect (`PostgreSQLDialect`) translates JPA operations to PostgreSQL SQL

### Schema Generation Flow

1. Application starts with active Spring profile
2. Hibernate reads `ddl-auto` setting from configuration
3. Entity classes are scanned for `@Entity` annotations
4. Hibernate generates DDL based on entity definitions
5. Schema is created/updated/validated based on `ddl-auto` setting
6. `import.sql` executes after schema generation (if present)

### Environment Variable Configuration

**Production (Docker profile):**
- `DATABASE_URL` - Full JDBC URL (required)
- `DATABASE_USERNAME` - Database username (required)
- `DATABASE_PASSWORD` - Database password (required)

**Development (Default profile):**
- `DATABASE_URL` - Falls back to `jdbc:postgresql://localhost:5432/mydatabase`
- `DATABASE_USERNAME` - Falls back to `user`
- `DATABASE_PASSWORD` - Falls back to `password`
- `DDL_AUTO` - Falls back to `create-drop`

## Historical Context (from thoughts/)

Related research documents:
- `.claude/thoughts/research/2025-12-28-timestamp-date-handling.md` - Timestamp precision considerations
- `.claude/thoughts/research/2025-12-28-balance-history-change-amount.md` - Balance history tracking

## Related Research

- [2025-12-28-timestamp-date-handling.md](.claude/thoughts/research/2025-12-28-timestamp-date-handling.md) - How timestamps are handled in the application

## Open Questions

None - the database initialization approach is well-defined through JPA entity annotations and profile-specific configuration.
