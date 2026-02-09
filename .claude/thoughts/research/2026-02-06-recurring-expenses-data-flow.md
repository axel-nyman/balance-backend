---
date: 2026-02-06T12:00:00+01:00
researcher: Claude Code
git_commit: a933c02a5b008dd332386afda0dc90aec609ea26
branch: main
repository: balance-backend
topic: "Recurring Expenses: Datatypes, Fields, and Layer Transformations"
tags: [research, codebase, recurring-expenses, data-flow, 3-layer-architecture]
status: complete
last_updated: 2026-02-06
last_updated_by: Claude Code
---

# Research: Recurring Expenses — Datatypes, Fields, and Layer Transformations

**Date**: 2026-02-06
**Researcher**: Claude Code
**Git Commit**: a933c02a5b008dd332386afda0dc90aec609ea26
**Branch**: main
**Repository**: balance-backend

## Research Question
Research recurring expenses all over the codebase. Focus on the datatypes and fields, and how it's passed along/transformed across different layers.

## Summary

Recurring expenses follow the project's strict 3-layer architecture. The data originates as HTTP JSON in request DTOs, gets transformed to a JPA entity via static extension methods, persists to PostgreSQL, and returns as response DTOs. Key transformations happen at the DTO↔Entity boundary: the `recurrenceInterval` field changes between `String` (DTOs) and `RecurrenceInterval` enum (Entity), and calculated fields (`nextDueDate`, `isDue`) are computed in the DomainService and injected into the list response DTO. The `lastUsedDate` and `lastUsedBudgetId` fields on the entity are never set via CRUD operations — they are exclusively managed by budget lock/unlock operations.

## Detailed Findings

### 1. Database Schema (PostgreSQL)

**Table**: `recurring_expenses`
**Migration**: `src/main/resources/db/migration/V1__baseline_schema.sql`

| Column | DB Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | NO | Primary key |
| `name` | VARCHAR | NO | Unique among non-deleted |
| `amount` | NUMERIC(19,2) | NO | Monetary value |
| `recurrence_interval` | VARCHAR | NO | Enum stored as string |
| `is_manual` | BOOLEAN | NO | Manual expense flag |
| `last_used_date` | TIMESTAMP | YES | Set on budget lock |
| `last_used_budget_id` | UUID | YES | Set on budget lock |
| `created_at` | TIMESTAMP | NO | JPA auditing |
| `updated_at` | TIMESTAMP | NO | JPA auditing |
| `deleted_at` | TIMESTAMP | YES | Soft delete marker |

Index: `idx_recurring_expenses_deleted` on `deleted_at`
Referenced by: `budget_expenses.recurring_expense_id` (FK)

### 2. JPA Entity — `RecurringExpense`

**File**: `src/main/java/org/example/axelnyman/main/domain/model/RecurringExpense.java`

| Field | Java Type | JPA Annotations | Notes |
|---|---|---|---|
| `id` | `UUID` | `@Id @GeneratedValue(strategy = GenerationType.UUID)` | Auto-generated |
| `name` | `String` | `@Column(nullable = false)` | |
| `amount` | `BigDecimal` | `@Column(nullable = false, precision = 19, scale = 2)` | |
| `recurrenceInterval` | `RecurrenceInterval` | `@Enumerated(EnumType.STRING) @Column(nullable = false)` | Enum → STRING |
| `isManual` | `Boolean` | `@Column(nullable = false)` | Defaults to `false` in constructor |
| `lastUsedDate` | `LocalDateTime` | `@Column(name = "last_used_date")` | Nullable, managed by budget ops |
| `lastUsedBudgetId` | `UUID` | `@Column(name = "last_used_budget_id")` | Nullable, managed by budget ops |
| `createdAt` | `LocalDateTime` | `@CreatedDate @Column(nullable = false, updatable = false)` | Auto-set |
| `updatedAt` | `LocalDateTime` | `@LastModifiedDate @Column(nullable = false)` | Auto-updated |
| `deletedAt` | `LocalDateTime` | `@Column(name = "deleted_at")` | Soft delete |

Uses `@EntityListeners(AuditingEntityListener.class)` for timestamp management.

Constructor: `RecurringExpense(String name, BigDecimal amount, RecurrenceInterval recurrenceInterval, Boolean isManual)` — sets `lastUsedDate` to null.

### 3. Enum — `RecurrenceInterval`

**File**: `src/main/java/org/example/axelnyman/main/domain/model/RecurrenceInterval.java`

```
MONTHLY | QUARTERLY | BIANNUALLY | YEARLY
```

Stored as `STRING` in PostgreSQL. Parsed from request DTO string via `RecurrenceInterval.valueOf(str.toUpperCase())`.

### 4. DTOs (Request & Response)

**File**: `src/main/java/org/example/axelnyman/main/domain/dtos/RecurringExpenseDtos.java`

#### `CreateRecurringExpenseRequest` (record)
| Field | Type | Validation |
|---|---|---|
| `name` | `String` | `@NotBlank("Name is required")` |
| `amount` | `BigDecimal` | `@NotNull @Positive("Amount must be positive")` |
| `recurrenceInterval` | `String` | `@NotNull("Recurrence interval is required")` |
| `isManual` | `Boolean` | `@NotNull("isManual is required")` |

#### `UpdateRecurringExpenseRequest` (record)
Same fields and validation as `CreateRecurringExpenseRequest`.

#### `RecurringExpenseResponse` (record)
| Field | Type | Source |
|---|---|---|
| `id` | `UUID` | Entity `id` |
| `name` | `String` | Entity `name` |
| `amount` | `BigDecimal` | Entity `amount` |
| `recurrenceInterval` | `String` | Entity enum `.name()` |
| `isManual` | `Boolean` | Entity `isManual` |
| `lastUsedDate` | `LocalDateTime` | Entity `lastUsedDate` |
| `createdAt` | `LocalDateTime` | Entity `createdAt` |
| `updatedAt` | `LocalDateTime` | Entity `updatedAt` |

Used for: create, get-by-id, update responses.

#### `RecurringExpenseListItemResponse` (record)
| Field | Type | Source |
|---|---|---|
| `id` | `UUID` | Entity `id` |
| `name` | `String` | Entity `name` |
| `amount` | `BigDecimal` | Entity `amount` |
| `recurrenceInterval` | `String` | Entity enum `.name()` |
| `isManual` | `Boolean` | Entity `isManual` |
| `lastUsedDate` | `LocalDateTime` | Entity `lastUsedDate` |
| `nextDueDate` | `LocalDateTime` | **Calculated** in DomainService |
| `isDue` | `Boolean` | **Calculated** in DomainService |
| `createdAt` | `LocalDateTime` | Entity `createdAt` |

Used for: list response items. Note `updatedAt` is excluded; `nextDueDate` and `isDue` are added.

#### `RecurringExpenseListResponse` (record)
| Field | Type |
|---|---|
| `expenses` | `List<RecurringExpenseListItemResponse>` |

Wrapper for the list endpoint.

### 5. Extension/Mapper — `RecurringExpenseExtensions`

**File**: `src/main/java/org/example/axelnyman/main/domain/extensions/RecurringExpenseExtensions.java`

Three static methods:

| Method | Input | Output | Key Transformation |
|---|---|---|---|
| `toEntity(CreateRecurringExpenseRequest)` | DTO | Entity | `String` → `RecurrenceInterval.valueOf(str.toUpperCase())` |
| `toResponse(RecurringExpense)` | Entity | `RecurringExpenseResponse` | `RecurrenceInterval.name()` → `String` |
| `toListItemResponse(RecurringExpense, LocalDateTime, Boolean)` | Entity + calculated fields | `RecurringExpenseListItemResponse` | Same enum→string + injects `nextDueDate`, `isDue` |

**Critical transformation**: `recurrenceInterval` is `String` in DTOs, `RecurrenceInterval` enum in Entity. Conversion uses `valueOf()` (can throw `IllegalArgumentException`) and `.name()`.

### 6. Repository — `RecurringExpenseRepository`

**File**: `src/main/java/org/example/axelnyman/main/infrastructure/data/context/RecurringExpenseRepository.java`

```java
extends JpaRepository<RecurringExpense, UUID>
```

Custom queries (all filter soft-deleted records):
- `existsByNameAndDeletedAtIsNull(String name)` — duplicate check for create
- `existsByNameAndDeletedAtIsNullAndIdNot(String name, UUID id)` — duplicate check for update
- `findAllByDeletedAtIsNull()` — list active records

### 7. Data Service (Infrastructure Layer) — `IDataService` / `DataService`

**Interface**: `src/main/java/org/example/axelnyman/main/domain/abstracts/IDataService.java:44-56`
**Implementation**: `src/main/java/org/example/axelnyman/main/infrastructure/data/services/DataService.java:103-135`

| Method | Returns | Notes |
|---|---|---|
| `saveRecurringExpense(RecurringExpense)` | `RecurringExpense` | Thin wrapper around `repository.save()` |
| `existsByRecurringExpenseName(String)` | `boolean` | Delegates to repository |
| `existsByRecurringExpenseNameExcludingId(String, UUID)` | `boolean` | Delegates to repository |
| `getRecurringExpenseById(UUID)` | `Optional<RecurringExpense>` | Filters soft-deleted via `.filter(e -> e.getDeletedAt() == null)` |
| `getAllActiveRecurringExpenses()` | `List<RecurringExpense>` | Delegates to repository |
| `deleteRecurringExpense(UUID)` | `void` | Sets `deletedAt = LocalDateTime.now()` and saves |
| `findLockedBudgetsUsingRecurringExpense(UUID, UUID)` | `List<Budget>` | JPQL query on BudgetRepository |

All methods work with **entities only** — no DTOs at this layer.

### 8. Domain Service (Business Layer) — `IDomainService` / `DomainService`

**Interface**: `src/main/java/org/example/axelnyman/main/domain/abstracts/IDomainService.java:33-41`
**Implementation**: `src/main/java/org/example/axelnyman/main/domain/services/DomainService.java:250-377`

| Method | Returns | Key Logic |
|---|---|---|
| `createRecurringExpense(CreateRecurringExpenseRequest)` | `RecurringExpenseResponse` | Duplicate name check → `toEntity()` → save → `toResponse()` |
| `getRecurringExpenseById(UUID)` | `RecurringExpenseResponse` | Fetch → throw if not found → `toResponse()` |
| `updateRecurringExpense(UUID, UpdateRecurringExpenseRequest)` | `RecurringExpenseResponse` | Fetch → name uniqueness check → parse enum → update fields → save → `toResponse()` |
| `getAllRecurringExpenses()` | `RecurringExpenseListResponse` | Fetch all → calculate `nextDueDate`/`isDue` → `toListItemResponse()` → sort by name → wrap |
| `deleteRecurringExpense(UUID)` | `void` | Validate exists → delegate soft delete to DataService |

**Private helper methods** (lines 339-377):
- `calculateNextDueDate(RecurringExpense)` → `LocalDateTime` or null: Adds interval to `lastUsedDate` (1/3/6/12 months)
- `calculateIsDue(LocalDateTime, LocalDateTime)` → `Boolean`: True if never used or next due date not in future

**Budget integration methods** (lines 806-981):
- `updateRecurringExpensesForBudget(UUID budgetId, LocalDateTime lockedAt)`: On budget lock, sets `lastUsedDate` and `lastUsedBudgetId` for all linked recurring expenses
- `restoreRecurringExpenses(UUID budgetId)`: On budget unlock, reverts to previous budget's state or resets to null

### 9. Controller (API Layer) — `RecurringExpenseController`

**File**: `src/main/java/org/example/axelnyman/main/api/endpoints/RecurringExpenseController.java`

| Endpoint | Method | Request Body | Response Body | HTTP Status |
|---|---|---|---|---|
| `POST /api/recurring-expenses` | `createRecurringExpense` | `CreateRecurringExpenseRequest` | `RecurringExpenseResponse` | 201 Created |
| `GET /api/recurring-expenses/{id}` | `getRecurringExpenseById` | — | `RecurringExpenseResponse` | 200 OK |
| `PUT /api/recurring-expenses/{id}` | `updateRecurringExpense` | `UpdateRecurringExpenseRequest` | `RecurringExpenseResponse` | 200 OK |
| `GET /api/recurring-expenses` | `getAllRecurringExpenses` | — | `RecurringExpenseListResponse` | 200 OK |
| `DELETE /api/recurring-expenses/{id}` | `deleteRecurringExpense` | — | — | 204 No Content |

Delegates entirely to `IDomainService`. Uses `@Valid` for request validation.

### 10. Error Handling

| Exception | HTTP Status | Thrown By |
|---|---|---|
| `DuplicateRecurringExpenseException` | 400 Bad Request | DomainService (create, update) |
| `RecurringExpenseNotFoundException` | 404 Not Found | DomainService (get, update, delete) |
| `IllegalArgumentException` (invalid enum) | 400 Bad Request | Extensions `toEntity()`, DomainService update |

Handled in `GlobalExceptionHandler` (`src/main/java/.../shared/exceptions/GlobalExceptionHandler.java:83-95`).

### 11. Cross-Entity Relationship — BudgetExpense

**File**: `src/main/java/org/example/axelnyman/main/domain/model/BudgetExpense.java`

`BudgetExpense` has an optional link to `RecurringExpense`:
- `recurringExpenseId` (`UUID`, nullable) — the FK column
- `recurringExpense` (`@ManyToOne(fetch = LAZY)`, `insertable = false, updatable = false`) — read-only JPA relationship

When a budget is **locked**, the DomainService iterates budget expenses with non-null `recurringExpenseId`, updates each linked `RecurringExpense.lastUsedDate` and `lastUsedBudgetId`. When **unlocked**, it restores them to the previous locked budget's state.

## Data Flow Diagrams

### Create Flow
```
HTTP POST JSON                    CreateRecurringExpenseRequest          RecurringExpense Entity
{                                 record {                               class {
  "name": "Netflix",               @NotBlank name: String,                name: String,
  "amount": 15.99,                 @Positive amount: BigDecimal,          amount: BigDecimal,
  "recurrenceInterval": "MONTHLY", @NotNull recurrenceInterval: String,   recurrenceInterval: RecurrenceInterval.MONTHLY,
  "isManual": false                @NotNull isManual: Boolean              isManual: Boolean (false),
}                                 }                                        lastUsedDate: null,
                                                                           lastUsedBudgetId: null
     │                                 │                                        │
     │  @Valid deserialization          │  RecurringExpenseExtensions            │  repository.save()
     ├────────────────────────────────► ├──────────toEntity()─────────────────► ├──────────────────►  PostgreSQL
     │  Controller                      │  String → valueOf().toUpperCase()      │
     │                                  │                                        │
     │                                  │                                        │  saved entity
     │  RecurringExpenseResponse   ◄────┤────────toResponse()──────────────◄────┤◄─────────────────
     │  record {                        │  RecurrenceInterval.name() → String    │
     │    id, name, amount,             │                                        │
     │    recurrenceInterval: String,   │                                        │
     │    isManual, lastUsedDate,       │                                        │
     │    createdAt, updatedAt          │                                        │
     │  }                               │                                        │
```

### List Flow (with calculated fields)
```
RecurringExpense entities          DomainService                          RecurringExpenseListResponse
from DataService                   calculates per-item:                   {
  [entity1, entity2, ...]           - nextDueDate = lastUsedDate +          expenses: [
                                       interval(1/3/6/12 months)              { ...fields, nextDueDate, isDue },
     │                              - isDue = lastUsedDate==null              { ...fields, nextDueDate, isDue }
     │                                  || nextDueDate <= now()             ]
     ├───────────────────────────► ├───────────────────────────────────► }
     │  dataService                 │  toListItemResponse(entity,         sorted by name ASC
     │  .getAllActive()             │    nextDueDate, isDue)
```

## Code References

- `src/main/resources/db/migration/V1__baseline_schema.sql` — Database table definition
- `src/main/java/.../domain/model/RecurringExpense.java` — JPA entity
- `src/main/java/.../domain/model/RecurrenceInterval.java` — Enum (MONTHLY, QUARTERLY, BIANNUALLY, YEARLY)
- `src/main/java/.../domain/dtos/RecurringExpenseDtos.java` — All request/response DTOs
- `src/main/java/.../domain/extensions/RecurringExpenseExtensions.java` — Entity↔DTO mapping
- `src/main/java/.../infrastructure/data/context/RecurringExpenseRepository.java` — JPA repository with soft-delete queries
- `src/main/java/.../infrastructure/data/services/DataService.java:103-135` — Data access methods
- `src/main/java/.../domain/abstracts/IDataService.java:44-56` — Data service interface
- `src/main/java/.../domain/services/DomainService.java:250-377` — Business logic, CRUD, due date calculation
- `src/main/java/.../domain/services/DomainService.java:806-827` — Budget lock: update recurring expenses
- `src/main/java/.../domain/services/DomainService.java:939-981` — Budget unlock: restore recurring expenses
- `src/main/java/.../domain/abstracts/IDomainService.java:33-41` — Domain service interface
- `src/main/java/.../api/endpoints/RecurringExpenseController.java` — REST controller
- `src/main/java/.../shared/exceptions/RecurringExpenseNotFoundException.java` — 404 exception
- `src/main/java/.../shared/exceptions/DuplicateRecurringExpenseException.java` — 400 exception
- `src/main/java/.../shared/exceptions/GlobalExceptionHandler.java:83-95` — Exception→HTTP mapping
- `src/main/java/.../domain/model/BudgetExpense.java` — References RecurringExpense via FK
- `src/test/java/.../integration/RecurringExpenseIntegrationTest.java` — Integration tests (~976 lines)

## Architecture Documentation

The recurring expense feature is a textbook implementation of the project's 3-layer architecture:

1. **API Layer** (`RecurringExpenseController`): HTTP-only concerns. Accepts validated DTOs, delegates to `IDomainService`, returns `ResponseEntity` with appropriate status codes.

2. **Domain Layer** (`DomainService`): All business logic lives here. Performs duplicate name checks, enum parsing, due date calculations, and entity↔DTO transformation via `RecurringExpenseExtensions`. Returns DTOs only.

3. **Infrastructure Layer** (`DataService`): Thin repository wrappers. Works with entities only. Handles soft-delete logic (setting `deletedAt`, filtering nulls).

**Key field transformations across layers**:
- `recurrenceInterval`: `String` (JSON/DTO) ↔ `RecurrenceInterval` enum (Entity) ↔ `VARCHAR` (PostgreSQL)
- `nextDueDate` and `isDue`: Do not exist on entity; **calculated** in DomainService and injected into `RecurringExpenseListItemResponse`
- `lastUsedDate` / `lastUsedBudgetId`: Exist on entity but are **never set by CRUD operations**; managed exclusively by budget lock/unlock flows
- `deletedAt`: Exists on entity but **never exposed in any DTO**; used internally for soft-delete filtering

## Historical Context (from thoughts/)

- `.claude/thoughts/plans/2026-01-09-flyway-database-migrations.md` — Contains the complete schema for `recurring_expenses` table as part of Flyway migration planning
- `.claude/thoughts/research/2026-01-09-database-connection-initialization.md` — Documents RecurringExpense entity structure in database initialization context
- `.claude/thoughts/research/2025-12-29-budget-totals-get-all-endpoint.md` — References `recurring_expense_id` in budget expense totals calculation
- `.claude/thoughts/research/2025-12-28-timestamp-date-handling.md` — Mentions recurring expense due date calculations in timestamp handling context

## Related Research

- No prior dedicated research on recurring expenses exists in `.claude/thoughts/research/`

## Open Questions

- The `UpdateRecurringExpenseRequest` has identical fields and validation to `CreateRecurringExpenseRequest` — these are defined as separate records
- The `toEntity()` mapping only exists for create; update applies fields manually in `DomainService.updateRecurringExpense()`
- `lastUsedBudgetId` is stored on the entity but never included in any response DTO
