# Story 25: Generate Todo List on Lock

**As a** system process
**I want to** automatically generate a todo list when a budget is locked
**So that** users have clear action items for the month

## Acceptance Criteria

- Triggered automatically during budget lock (part of lock transaction)
- Creates TRANSFER todos for money movements between accounts
- Creates PAYMENT todos for manually paid expenses
- Uses TransferCalculationUtils for transfer logic
- A todo list is linked to exactly one budget
- Deletes existing todo list before generating new one (if relocking after unlock)

## Todo Generation Logic

1. Use `TransferCalculationUtils.calculateTransfers(...)` to get transfers
2. For each transfer, create TRANSFER todo item
3. For each expense where `isManual = true`, create PAYMENT todo item

## API Specification

```
GET /api/budgets/{budgetId}/todo-list

Success Response (200):
{
  "id": "uuid",
  "budgetId": "uuid",
  "createdAt": "datetime",
  "items": [
    {
      "id": "uuid",
      "name": "string",
      "status": "PENDING|COMPLETED",
      "type": "PAYMENT|TRANSFER",
      "amount": "decimal",
      "fromAccount": {
        "id": "uuid",
        "name": "string"
      },
      "toAccount": {
        "id": "uuid",
        "name": "string"
      },
      "createdAt": "datetime",
      "completedAt": "datetime"
    }
  ],
  "summary": {
    "totalItems": "integer",
    "pendingItems": "integer",
    "completedItems": "integer"
  }
}

Error Response (404):
{
  "error": "Todo list not found for this budget"
}
```

## Technical Implementation

1. **Domain Model Changes**

   - Create `TodoList` entity: id, budgetId, createdAt
   - Create `TodoItem` entity: id, todoListId, name, status (enum: PENDING/COMPLETED), type (enum: PAYMENT/TRANSFER), fromAccountId, toAccountId (nullable for PAYMENT), amount, createdAt, completedAt
   - Create `TodoItemStatus` enum: PENDING, COMPLETED
   - Create `TodoItemType` enum: PAYMENT, TRANSFER

2. **Repository Implementation**

   - Create `TodoListRepository` extending JpaRepository
   - Add queries: `findByBudgetId(UUID budgetId)`, `deleteByBudgetId(UUID budgetId)`
   - Create `TodoItemRepository` extending JpaRepository
   - Add query: `findAllByTodoListId(UUID todoListId)`

3. **Data Service Layer**

   - Add to `IDataService`:
     - Methods to save/delete todo list
     - Methods to save todo items
     - Method to get todo list by budget id
     - Method to get all items for a todo list
   - Implement in `DataService`

4. **DTOs and Extensions**

   - Create `TodoDtos.java` with:
     - `TodoListResponse` record
     - `TodoItemResponse` record with nested account details
     - `TodoSummaryResponse` record
   - Create `TodoExtensions.java` with mapping methods

5. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to generate todo list (called during lock)
     - Method to get todo list for budget
   - Implement in `DomainService`:
     - **generateTodoList(UUID budgetId)**:
       - Delete existing todo list for budget if exists (via IDataService)
       - Create new TodoList entity
       - Load budget income, expenses, savings from IDataService
       - Call `TransferCalculationUtils.calculateTransfers(...)` to get transfers
       - For each transfer: create TRANSFER todo item
       - For each manual expense: create PAYMENT todo item
       - Save all entities via IDataService
     - **getTodoList(UUID budgetId)**:
       - Load todo list and items from IDataService
       - Calculate summary statistics
       - Return DTO

6. **Integration with Budget Lock**

   - Modify `DomainService.lockBudget()`:
     - After validating balance = 0
     - Before committing transaction
     - Call internal `generateTodoList(budgetId)` method
     - If todo generation fails, rollback entire lock transaction

7. **Controller Implementation**

   - Add GET /api/budgets/{budgetId}/todo-list endpoint to `BudgetController`

8. **Integration Tests**
   - Test todo list generation on budget lock
   - Test PAYMENT items for manual expenses
   - Test TRANSFER items match TransferCalculationUtils output
   - Test summary calculations
   - Test deletion of existing todo list on relock

## Definition of Done

- All acceptance criteria met
- Unit tests for todo generation logic
- Integration tests passing
- API documentation updated
- Transaction handling verified
- Code reviewed and approved
