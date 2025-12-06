# Story 28: Mark Todo Item Complete

**As a** user
**I want to** mark todo items as completed
**So that** I can track what has been done

## Acceptance Criteria

- Can toggle between PENDING and COMPLETED status
- Records completedAt timestamp when marked complete
- Clears completedAt when marked pending
- Can only update todos for locked budgets

## API Specification

```
PUT /api/budgets/{budgetId}/todo-list/items/{id}
Request Body:
{
  "status": "PENDING|COMPLETED"
}

Success Response (200):
{
  "id": "uuid",
  "name": "string",
  "status": "COMPLETED",
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
  "completedAt": "datetime",
  "createdAt": "datetime"
}

Error Response (404):
{
  "error": "Todo item not found"
}
```

## Technical Implementation

1. **Data Service Layer**

   - Add to `IDataService`:
     - Method to get todo item by id
     - Method to save todo item
   - Implement in `DataService`

2. **DTOs**

   - Add to `TodoDtos.java`:
     - `UpdateTodoItemRequest` record

3. **Domain Service Layer**

   - Add to `IDomainService`:
     - Method to update todo item status
   - Implement in `DomainService`:
     - Validate todo item exists
     - Validate associated budget is locked
     - Update status
     - If status = COMPLETED: set completedAt = now
     - If status = PENDING: set completedAt = null
     - Save via IDataService
     - Return DTO

4. **Controller Implementation**

   - Add PUT /api/budgets/{budgetId}/todo-list/items/{id} endpoint

5. **Integration Tests**
   - Test marking item complete
   - Test marking item pending
   - Test completedAt timestamp handling
   - Test status toggle multiple times

## Definition of Done

- All acceptance criteria met
- Integration tests passing
- API documentation updated
- Code reviewed and approved
