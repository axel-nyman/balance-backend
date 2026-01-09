---
date: 2026-01-09T14:22:59Z
researcher: Claude
git_commit: e110c1bb48b8f3040a100b2a746c278bd4c22795
branch: main
repository: balance-backend
topic: "Docker Compose Deployment Research"
tags: [research, docker, deployment, configuration, database]
status: complete
last_updated: 2026-01-09
last_updated_by: Claude
---

# Research: Docker Compose Deployment Configuration

**Date**: 2026-01-09T14:22:59Z
**Researcher**: Claude
**Git Commit**: e110c1bb48b8f3040a100b2a746c278bd4c22795
**Branch**: main
**Repository**: balance-backend

## Research Question
How can this codebase be deployed using Docker Compose? What environment variables are needed? What would a frontend need to connect to this backend? How should the database be configured?

## Summary
The balance-backend is a Spring Boot 3.4.11 REST API application (Java 17) that uses PostgreSQL 15 as its database. The Docker image is available on Docker Hub at `axelnyman/balance-backend:latest`. The application exposes a REST API on port 8080 with Swagger documentation and uses Flyway for database migrations. Currently, there is no authentication implemented (despite JWT being mentioned in the template docs) - all endpoints are public.

## Detailed Findings

### Docker Image
- **Docker Hub**: `axelnyman/balance-backend:latest`
- **Base Image**: `eclipse-temurin:17-jre` (runtime stage)
- **Exposed Port**: 8080
- **Entry Point**: `java -jar app.jar`
- **Non-root User**: Application runs as `appuser` (UID 1001) for security

### Environment Variables

#### Required for Backend Service

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `DATABASE_URL` | JDBC connection string to PostgreSQL | `jdbc:postgresql://db:5432/mydatabase` |
| `DATABASE_USERNAME` | Database user | `user` |
| `DATABASE_PASSWORD` | Database password | `password` |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `docker` |

#### Optional for Backend Service

| Variable | Description | Default |
|----------|-------------|---------|
| `JWT_SECRET` | JWT signing secret (32+ chars) | None (JWT not yet implemented) |
| `JWT_EXPIRATION` | JWT token expiration in ms | `86400000` (24 hours) |
| `SERVER_PORT` | Server port | `8080` |
| `LOGGING_LEVEL_ROOT` | Root logging level | `INFO` |
| `LOGGING_LEVEL_ORG_SPRINGFRAMEWORK` | Spring logging level | `INFO` |

**Note**: The `docker` profile requires all database environment variables to be set (no defaults).

#### Required for PostgreSQL Service

| Variable | Description | Example Value |
|----------|-------------|---------------|
| `POSTGRES_USER` | Database superuser | `user` |
| `POSTGRES_PASSWORD` | Database password | `password` |
| `POSTGRES_DB` | Default database name | `mydatabase` |

### Database Configuration

#### PostgreSQL Requirements
- **Version**: PostgreSQL 15 (as used in dev docker-compose)
- **Port**: 5432 (internal), can be mapped to any external port
- **Persistence**: Volume mount at `/var/lib/postgresql/data`
- **Health Check**: `pg_isready -U user -d mydatabase`

#### Flyway Migrations
- Migrations are stored at `classpath:db/migration/` (compiled into the JAR)
- Currently one migration: `V1__baseline_schema.sql`
- Creates 10 tables for the budgeting application:
  - `bank_accounts`
  - `recurring_expenses`
  - `budgets`
  - `balance_history`
  - `budget_income`
  - `budget_expenses`
  - `budget_savings`
  - `todo_lists`
  - `todo_items`
- Migrations run automatically on application startup
- In `docker` profile: `clean-disabled: true` (prevents accidental data loss)

### API Endpoints

All endpoints are currently public (no authentication implemented yet).

#### Base URL
`http://<host>:8080/api`

#### Bank Accounts (`/api/bank-accounts`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create bank account |
| GET | `/` | Get all bank accounts |
| PUT | `/{id}` | Update bank account details |
| POST | `/{id}/balance` | Update bank account balance |
| DELETE | `/{id}` | Soft delete bank account |
| GET | `/{id}/balance-history` | Get paginated balance history |

#### Budgets (`/api/budgets`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create budget |
| GET | `/` | Get all budgets |
| GET | `/{id}` | Get budget details |
| DELETE | `/{id}` | Delete budget |
| POST | `/{budgetId}/income` | Add income to budget |
| PUT | `/{budgetId}/income/{id}` | Update budget income |
| DELETE | `/{budgetId}/income/{id}` | Delete budget income |
| POST | `/{budgetId}/expenses` | Add expense to budget |
| PUT | `/{budgetId}/expenses/{id}` | Update budget expense |
| DELETE | `/{budgetId}/expenses/{id}` | Delete budget expense |
| POST | `/{budgetId}/savings` | Add savings to budget |
| PUT | `/{budgetId}/savings/{id}` | Update budget savings |
| DELETE | `/{budgetId}/savings/{id}` | Delete budget savings |
| PUT | `/{id}/lock` | Lock budget |
| PUT | `/{id}/unlock` | Unlock budget |
| GET | `/{budgetId}/todo-list` | Get todo list for budget |
| PUT | `/{budgetId}/todo-list/items/{id}` | Update todo item status |

#### Recurring Expenses (`/api/recurring-expenses`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/` | Create recurring expense |
| GET | `/` | Get all recurring expenses |
| GET | `/{id}` | Get recurring expense by ID |
| PUT | `/{id}` | Update recurring expense |
| DELETE | `/{id}` | Soft delete recurring expense |

#### Documentation
- **Swagger UI**: `http://<host>:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://<host>:8080/v3/api-docs`

### Frontend Connection Requirements

A frontend application connecting to this backend would need:

#### Network Configuration
1. **API Base URL**: `http://<backend-host>:8080/api`
2. **CORS**: Currently no CORS configuration exists in the codebase. Frontend on a different origin will need CORS to be added to the backend, OR a reverse proxy/gateway should be used.

#### API Contract
1. All requests use JSON (`Content-Type: application/json`)
2. All responses are JSON
3. UUIDs are used for all entity IDs
4. Monetary values use string format for BigDecimal precision
5. Timestamps are ISO 8601 format

#### Authentication (Future)
- JWT authentication is planned but not yet implemented
- When implemented, frontend will need to:
  - Call `/api/auth/register` or `/api/auth/login`
  - Store JWT token
  - Include `Authorization: Bearer <token>` header on protected requests

#### Error Handling
- Global exception handler returns structured error responses
- HTTP status codes follow REST conventions (201 Created, 204 No Content, 400 Bad Request, 404 Not Found)

### Docker Compose Service Dependencies

For a production-like deployment:

```
Services:
  db (PostgreSQL 15)
    ├── Port: 5432
    ├── Volume: postgres_data:/var/lib/postgresql/data
    └── Health check: pg_isready

  backend (axelnyman/balance-backend:latest)
    ├── Port: 8080
    ├── Depends on: db (healthy)
    ├── Environment: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD, SPRING_PROFILES_ACTIVE=docker
    └── Optional: JWT_SECRET, JWT_EXPIRATION

  adminer (optional, for DB management)
    ├── Port: 8081
    └── Depends on: db
```

### Key Configuration Files

| File | Purpose |
|------|---------|
| `docker-compose.dev.yml` | Development database setup |
| `Dockerfile` | Multi-stage build for backend image |
| `application.yml` | Default configuration with env var placeholders |
| `application-docker.yml` | Docker profile (requires explicit env vars) |
| `application-local.yml` | Local development profile |
| `.env.example` | Example environment variables |

## Code References
- `Dockerfile:1-38` - Multi-stage Docker build
- `docker-compose.dev.yml:1-32` - Development docker-compose
- `src/main/resources/application.yml:1-24` - Base configuration
- `src/main/resources/application-docker.yml:1-28` - Docker profile configuration
- `src/main/resources/db/migration/V1__baseline_schema.sql:1-159` - Database schema
- `src/main/java/org/example/axelnyman/main/api/endpoints/BankAccountController.java:17-98` - Bank account API
- `src/main/java/org/example/axelnyman/main/api/endpoints/BudgetController.java:13-139` - Budget API
- `src/main/java/org/example/axelnyman/main/api/endpoints/RecurringExpenseController.java:16-80` - Recurring expense API

## Architecture Documentation

### Spring Boot Profiles
- **default**: Uses environment variables with fallback defaults
- **local**: Hardcoded values for local dev, verbose logging, flyway clean enabled
- **docker**: Requires explicit environment variables, flyway clean disabled
- **test**: Testcontainers PostgreSQL, isolated per test run

### Database Migrations Strategy
- Flyway handles all schema changes
- `ddl-auto: validate` ensures JPA entities match database schema
- Migrations are idempotent and versioned (V1, V2, etc.)
- Never modify applied migrations - create new ones instead

### Network Topology for Docker Compose
```
                    ┌─────────────┐
                    │   Frontend  │
                    │  (external) │
                    └──────┬──────┘
                           │ HTTP
                           ▼
┌──────────────────────────────────────────────────────┐
│                   Docker Network                      │
│                                                       │
│  ┌─────────────┐         ┌─────────────────────┐    │
│  │  PostgreSQL │◄────────│      Backend        │    │
│  │     (db)    │  JDBC   │ (balance-backend)   │    │
│  │   :5432     │         │      :8080          │    │
│  └─────────────┘         └─────────────────────┘    │
│         ▲                                           │
│         │ (optional)                                │
│  ┌──────┴──────┐                                    │
│  │   Adminer   │                                    │
│  │    :8081    │                                    │
│  └─────────────┘                                    │
└──────────────────────────────────────────────────────┘
```

## Open Questions
1. **CORS Configuration**: Not currently implemented. Will need to be added for frontend on different origin, or use reverse proxy.
2. **JWT Authentication**: Mentioned in template docs but not yet implemented. When added, will require `JWT_SECRET` environment variable.
3. **Health Endpoint**: Spring Boot Actuator is not included. Consider adding `/actuator/health` for container orchestration.
4. **SSL/TLS**: Not configured. In production, should be handled by reverse proxy or load balancer.
