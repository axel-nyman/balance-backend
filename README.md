# Balance — Backend

Backend for **Balance**, a self-hosted budgeting app that one couple uses to
manage their shared monthly finances on their home network. It is a focused
tool for a single household's monthly money routine — plan a budget, lock it
to compute the transfers and todo list, execute through the month, and
correct as reality drifts — **not** a general-purpose finance platform.

For the full picture of what the app does today (domain model, the monthly
routine, API surface, conventions, and known quirks), read
[`product/STATE.md`](product/STATE.md) — it is the living source of truth.

> **No authentication — by design.** Balance is deployed LAN-only for two
> trusted users. There are no user accounts and Spring Security is not on the
> classpath; every `/api/**` endpoint is open. Do not add an access-control
> layer (see the non-goals in `product/STATE.md`).

## Tech stack

- **Spring Boot 3.4.x** on **Java 17**, Maven (via the Maven Wrapper)
- **PostgreSQL 15** with JPA/Hibernate
- **Flyway** for versioned schema migrations
  (`src/main/resources/db/migration/`)
- Strict **3-layer architecture** (DataService → DomainService → Controller)
- **Testcontainers** integration tests
- **OpenAPI / Swagger UI** for API documentation

## Local development

### 1. Start the database

```bash
docker-compose -f docker-compose.dev.yml up -d
```

### 2. Run the application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

- API: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui.html` — explore and try every
  endpoint
- Adminer (DB UI): `http://localhost:8081`

Flyway applies the migrations automatically on startup. To reset the local
database:

```bash
docker-compose -f docker-compose.dev.yml down -v
docker-compose -f docker-compose.dev.yml up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Environment variables

`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` (the `local` profile
provides sensible defaults for the docker-compose dev database).

## Testing

Integration tests run against a real PostgreSQL via Testcontainers, so a
running Docker daemon is required.

```bash
./mvnw test                                  # full suite
./mvnw test -Dtest=BudgetIntegrationTest     # one class
./mvnw clean test jacoco:report              # coverage at target/site/jacoco/index.html
```

The project follows TDD: integration tests with `@SpringBootTest` +
Testcontainers are the primary safety net, with focused unit tests for pure
algorithms (e.g. `TransferCalculationUtilsTest`).

## API documentation

Swagger UI at `http://localhost:8080/swagger-ui.html` lists every endpoint
with request/response schemas and a try-it console. A summary of the API
surface also lives in [`product/STATE.md`](product/STATE.md).

## Delivery pipeline

Commits and PR titles follow **Conventional Commits**
(`feat:` / `fix:` / `docs:` / `chore:` / `refactor:` / `test:`).
[release-please](https://github.com/googleapis/release-please) reads them to
maintain a release PR and `CHANGELOG.md` automatically — **never hand-edit
`CHANGELOG.md`**. When the maintainer merges the release PR, GitHub Actions
builds multi-arch (amd64 + arm64) Docker images, publishes them to Docker
Hub (`axelnyman/balance-backend`), and the Raspberry Pi running production
pulls them. Merging a feature PR alone does not deploy; the release-PR merge
is the deploy gate.

## Where to look next

- [`product/`](product/) — the product workflow: `STATE.md` (what the app is
  today), `backlog/` (prioritized specs), `done/` (history), and
  `ROUTINE_PROMPT.md` (operating manual for the scheduled daily agent).
- [`CLAUDE.md`](CLAUDE.md) — engineering conventions: the 3-layer
  architecture, TDD practice, migration rules, and coding style.
