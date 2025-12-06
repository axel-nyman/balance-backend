# Story 35: Todo List Lifecycle E2E Tests

**As a** developer
**I want to** verify todo list generation, cleanup, and completion tracking work correctly
**So that** users always see accurate, non-duplicate todo items with correct state

## Acceptance Criteria

- Old todo lists are deleted when relocking, preventing accumulation
- Empty todo lists handled gracefully when no transfers/payments needed
- Todo generation is deterministic (same input produces same ordered output)
- Todo list access blocked for unlocked budgets
- Completion state doesn't leak across lock cycles

## Test Specifications

### Test 1: Todo List Cleanup on Relock

**Test Name:** `shouldDeleteOldTodoListWhenRelockingAndNotAccumulateTodos`

**Description:** Verifies that relocking a budget deletes the old todo list completely before generating a new one.

**Given:**
- Create budget with income, expenses (manual), savings
- Lock budget → generates todo list with 5 items (3 transfers, 2 manual payments)
- Mark 2 todos as completed
- Unlock budget
- Modify budget: add more manual expenses
- Expected: new todo list will have 7 items (3 transfers, 4 manual payments)

**When:**
- Lock budget again (relock)

**Then:**
- Old todo list completely deleted (including completed items)
- New todo list generated with 7 fresh items
- All new items have status=PENDING
- Database has exactly 1 TodoList for this budget
- No accumulated todo items from previous lock
- Query: `SELECT COUNT(*) FROM todo_list WHERE budget_id = ?` returns 1
- Query: `SELECT COUNT(*) FROM todo_item WHERE todo_list_id = ?` returns 7

**Why:** Poor cleanup could cause todo lists to accumulate on each relock, confusing users with duplicate or stale todos. Tests cleanup is complete before regeneration.

---

### Test 2: Empty Todo List Handling

**Test Name:** `shouldGenerateEmptyTodoListWhenBudgetRequiresNoTransfersOrPayments`

**Description:** Tests system behavior when budget requires zero todo items (no transfers, no manual payments).

**Given:**
- Create budget with perfectly balanced single account:
  - Account A: $1000 income, $600 expenses (auto-pay), $400 savings
  - All activity on one account, no transfers needed
  - No manual payment expenses
- Budget is balanced (income - expenses - savings = 0)

**When:**
- Lock budget

**Then:**
- Two acceptable outcomes:
  - **Option A:** TodoList created with empty items array
  - **Option B:** No TodoList entity created at all
- GET /api/budgets/{id}/todo-list returns:
  - Option A: 200 OK with empty items array, summary shows 0 pending/completed
  - Option B: 404 Not Found with message "No todo list for this budget"
- No exceptions thrown during lock
- Budget locks successfully

**Why:** Edge case that could expose assumptions in code (e.g., "todo list always has items"). System must handle empty case gracefully without errors.

---

### Test 3: Deterministic Todo Generation

**Test Name:** `shouldGenerateTodosInDeterministicOrderForConsistentUserExperience`

**Description:** Verifies that locking the same budget setup multiple times produces identical todo list ordering.

**Given:**
- Create budget setup:
  - 3 accounts (A, B, C)
  - 5 budget items in specific arrangement
  - Balance = 0
- Lock budget → capture todo list order
- Unlock budget, delete it
- Recreate exact same budget setup (same accounts, amounts, names)

**When:**
- Lock second budget

**Then:**
- Todo items appear in identical order both times
- Same transfers generated (same fromAccount, toAccount, amount, order)
- Same manual payment todos (same order)
- Ordering should be deterministic (e.g., alphabetical by account name, or by database ID)
- No random ordering or non-deterministic UUID generation affecting order

**Why:** Non-deterministic ordering confuses users ("why did my todos change?"). Tests that todo generation is a pure function with consistent output.

---

### Test 4: Todo List Access Control

**Test Name:** `shouldPreventTodoListAccessForUnlockedBudgets`

**Description:** Tests that todo list endpoint properly handles requests for unlocked budgets.

**Given:**
- Create budget (unlocked, no lock ever performed)
- Budget has income/expenses/savings but is not locked

**When:**
- GET /api/budgets/{id}/todo-list

**Then:**
- Response: 404 Not Found with message "Todo list not found for this budget" or "Todo list only exists for locked budgets"
- OR: 200 OK with null/empty todo list and explanation
- No server error (500)
- Clear error message indicating todo lists only generated on lock

**Alternative scenario:**
- Create budget, lock it (todo list generated)
- Unlock budget (todo list deleted)
- GET /api/budgets/{id}/todo-list → same 404 response

**Why:** Todo lists only exist for locked budgets. Endpoint must handle unlocked state gracefully with clear messaging, not throw exceptions.

---

### Test 5: Completion State Isolation

**Test Name:** `shouldHandleTodoCompletionWhenBudgetUnlockedAndRelocked`

**Description:** Verifies that todo completion state doesn't persist or leak across lock cycles.

**Given:**
- Create budget, lock it
- Todo list generated with 5 items
- Mark items 1, 2, 3 as COMPLETED
- Items 4, 5 remain PENDING

**When:**
- Unlock budget (deletes todo list including completion state)
- Lock budget again (regenerates todo list)

**Then:**
- New todo list has 5 fresh items
- All items have status=PENDING (none marked completed)
- Old completion state completely gone
- New items have new UUIDs (not reusing old item IDs)
- Users must mark todos as complete again if same budget relocked

**Why:** Completion state belongs to a specific lock cycle. Unlocking invalidates todo list, so completion state must not leak into new lock cycle. Tests proper state isolation.

---

## Technical Implementation

1. **Test Class:** `TodoListLifecycleE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on todo list state management

2. **Helper Methods Needed:**
   ```java
   private TodoListResponse getTodoList(UUID budgetId)
   private void assertTodoListCount(UUID budgetId, int expectedCount)
   private void assertTodoItemCount(UUID todoListId, int expectedCount)
   private void assertAllTodoItemsArePending(UUID todoListId)
   private void markTodoComplete(UUID budgetId, UUID todoItemId)
   private List<UUID> getTodoItemIds(UUID budgetId)
   private void assertTodoOrderIdentical(List<TodoItemResponse> list1, List<TodoItemResponse> list2)
   ```

3. **Todo Comparison Helpers:**
   ```java
   private void assertTodosEquivalent(
       List<TodoItemResponse> expected,
       List<TodoItemResponse> actual
   ) {
       // Compare todos ignoring UUIDs, timestamps (focus on name, type, amount, accounts)
       assertThat(actual).hasSize(expected.size());
       for (int i = 0; i < expected.size(); i++) {
           assertTodoMatches(expected.get(i), actual.get(i));
       }
   }

   private void assertTodoMatches(TodoItemResponse expected, TodoItemResponse actual) {
       assertThat(actual.name()).isEqualTo(expected.name());
       assertThat(actual.type()).isEqualTo(expected.type());
       assertThat(actual.amount()).isEqualByComparingTo(expected.amount());
       // ... compare accounts, etc.
   }
   ```

4. **Database Inspection:**
   ```java
   @Autowired
   private TodoListRepository todoListRepository;

   @Autowired
   private TodoItemRepository todoItemRepository;

   private long countTodoListsForBudget(UUID budgetId) {
       return todoListRepository.findByBudgetId(budgetId).count();
   }
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Cleanup logic verified at database level (count queries)
- Empty todo list case handled without errors
- Deterministic ordering verified through multiple runs
- Access control tested for both never-locked and unlocked-after-lock states
- Completion state isolation verified (no state leakage)
- Tests verify todo list deletion cascade (deleting list deletes items)
- Error messages user-friendly and informative
- Tests document system behavior for edge cases (empty list, no list)
- Code coverage includes todo list state transitions
