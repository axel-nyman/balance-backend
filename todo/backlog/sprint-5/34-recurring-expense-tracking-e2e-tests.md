# Story 34: Recurring Expense Tracking E2E Tests

**As a** developer
**I want to** verify recurring expense tracking works correctly across complex budget lifecycles
**So that** lastUsedDate and lastUsedBudgetId remain accurate through out-of-order operations and edge cases

## Acceptance Criteria

- Recurring expense tracking works when budgets are locked out of chronological order
- Template deletion doesn't break unlock operations on budgets referencing it
- Sequential unlocking correctly walks backwards through budget history
- Multiple templates with identical names don't interfere with each other's tracking
- Unlocking only updates templates where lastUsedBudgetId matches the unlocking budget

## Test Specifications

### Test 1: Out-of-Order Budget Locking

**Test Name:** `shouldHandleRecurringExpenseTrackingWhenMultipleBudgetsLockedOutOfOrder`

**Description:** Verifies that locking budgets in non-chronological order doesn't corrupt recurring expense tracking.

**Given:**
- Create recurring expense template "Netflix" with amount $15.99
- Create three unlocked budgets:
  - January 2025 (unlocked)
  - February 2025 (unlocked)
  - March 2025 (unlocked)
- Add "Netflix" expense to all three budgets

**When:**
- Lock budgets in non-chronological order:
  1. Lock March 2025 first
  2. Then lock February 2025
  3. Finally lock January 2025

**Then:**
- After locking March:
  - Netflix.lastUsedDate = March lock timestamp
  - Netflix.lastUsedBudgetId = March budget ID
- After locking February (even though it's older than March):
  - Netflix.lastUsedDate = February lock timestamp (overwrites March)
  - Netflix.lastUsedBudgetId = February budget ID
  - **Issue**: This might not be desired behavior!
- After locking January:
  - Netflix.lastUsedDate = January lock timestamp
  - Netflix.lastUsedBudgetId = January budget ID

**Alternative Expected Behavior** (if implementation tracks most recent by date):
- System should track the chronologically latest locked budget, not just the most recently locked
- Would require checking year/month, not just lock order

**Why:** Tests whether tracking is based on lock order or chronological order. Reveals design decision and potential bug if users lock budgets out of order (e.g., locking future month before current month).

---

### Test 2: Template Deletion with Active References

**Test Name:** `shouldHandleRecurringExpenseWhenTemplateDeletedButReferencesExistInLockedBudgets`

**Description:** Tests graceful handling when template is deleted but locked budgets still reference it.

**Given:**
- Create recurring expense template "Gym Membership"
- Create January budget, add expense linked to "Gym Membership", lock budget
- Create February budget, add expense linked to "Gym Membership", lock budget
- Delete "Gym Membership" template (soft delete)

**When:**
- Attempt to unlock February budget

**Then:**
- Unlock operation doesn't crash
- System handles missing template gracefully (options):
  - Skip updating template (already deleted, nothing to update)
  - Log warning but complete unlock successfully
- Budget unlocks successfully
- Balance reversal completes correctly
- No foreign key violations or null pointer exceptions

**Why:** Real-world scenario where users clean up old templates. System must handle orphaned references gracefully without blocking legitimate operations.

---

### Test 3: Sequential Unlocking Walk-back

**Test Name:** `shouldCorrectlyRestoreRecurringExpenseWhenMultipleBudgetsUnlockedInSequence`

**Description:** Verifies that unlocking budgets in sequence correctly walks backwards through template usage history.

**Given:**
- Create recurring expense "Internet"
- Lock January with Internet expense
- Lock February with Internet expense
- Lock March with Internet expense
- Current state: Internet.lastUsedDate = March lock timestamp, lastUsedBudgetId = March ID

**When:**
- Unlock March → lastUsedDate should restore to February
- Unlock February → lastUsedDate should restore to January
- Unlock January → lastUsedDate should restore to null (no more locked budgets using it)

**Then:**
After unlock March:
- Internet.lastUsedDate = February lock timestamp
- Internet.lastUsedBudgetId = February budget ID

After unlock February:
- Internet.lastUsedDate = January lock timestamp
- Internet.lastUsedBudgetId = January budget ID

After unlock January:
- Internet.lastUsedDate = null
- Internet.lastUsedBudgetId = null

**Why:** Tests complex state restoration logic. System must query for "previous locked budget that used this template" and restore correctly, handling the case where no previous budget exists.

---

### Test 4: Identical Template Names

**Test Name:** `shouldHandleRecurringExpenseTrackingWithIdenticalTemplateNames`

**Description:** Tests that tracking uses template ID, not name, preventing confusion with duplicate names.

**Given:**
- Create two recurring expense templates both named "Subscription":
  - Template A: "Subscription", $9.99
  - Template B: "Subscription", $14.99
- Create January budget:
  - Add expense linked to Template A
- Create February budget:
  - Add expense linked to Template B
- Lock both budgets

**When:**
- Query lastUsedDate for Template A
- Query lastUsedDate for Template B

**Then:**
- Template A.lastUsedDate = January lock timestamp
- Template A.lastUsedBudgetId = January budget ID
- Template B.lastUsedDate = February lock timestamp
- Template B.lastUsedBudgetId = February budget ID
- No cross-contamination between templates with same name
- Tracking uses UUID, not string name

**Why:** Tests that implementation correctly uses template ID for tracking. Name collision should not cause bugs or confusion in tracking logic.

---

### Test 5: Selective Template Update on Unlock

**Test Name:** `shouldNotUpdateRecurringExpenseWhenUnlockingBudgetThatWasNotLastToUseIt`

**Description:** Verifies unlock only updates templates where lastUsedBudgetId matches the unlocking budget.

**Given:**
- Create recurring expense "Phone Bill"
- Lock January with Phone Bill expense
- Lock February (without Phone Bill)
- Lock March with Phone Bill expense again
- Current state: PhoneBill.lastUsedBudgetId = March ID

**When:**
- Unlock January budget

**Then:**
- Phone Bill template remains unchanged:
  - lastUsedDate still points to March (not affected by January unlock)
  - lastUsedBudgetId still points to March
- Rationale: January was not the "last to use" this template, so unlocking it shouldn't affect the template
- Only if March were unlocked would the template update (restore to January)

**Why:** Critical correctness test. Unlocking an old budget shouldn't corrupt current template state. Only the budget that "owns" the current lastUsedBudgetId should be able to update it on unlock.

---

## Technical Implementation

1. **Test Class:** `RecurringExpenseTrackingE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on template state across budget lifecycle

2. **Helper Methods Needed:**
   ```java
   private UUID createRecurringExpense(String name, BigDecimal amount)
   private void addExpenseWithTemplate(UUID budgetId, UUID templateId)
   private RecurringExpenseResponse getRecurringExpense(UUID templateId)
   private void assertTemplateTracking(UUID templateId, LocalDateTime expectedDate, UUID expectedBudgetId)
   private void softDeleteTemplate(UUID templateId)
   private List<Budget> getLockedBudgetsUsingTemplate(UUID templateId)
   ```

3. **State Verification Helpers:**
   ```java
   private void verifyTemplateStateProgression(
       UUID templateId,
       Map<String, ExpectedState> stateByOperation
   ) {
       // Track state changes through multiple operations
   }

   private Budget findPreviousLockedBudgetUsingTemplate(
       UUID templateId,
       UUID excludeBudgetId
   ) {
       // Simulate the query used by unlock operation
   }
   ```

4. **Test Data Builders:**
   ```java
   private BudgetWithExpenses createBudgetWithRecurringExpense(
       int month,
       int year,
       UUID templateId
   ) {
       UUID budgetId = createBudget(month, year);
       UUID accountId = createBankAccount("Default", BigDecimal.ZERO);
       addExpenseWithTemplate(budgetId, templateId, accountId);
       return new BudgetWithExpenses(budgetId, List.of(expenseId));
   }
   ```

## Definition of Done

- All 5 test scenarios implemented and passing
- Out-of-order locking behavior documented (intended design decision)
- Template deletion with active references handled gracefully
- Sequential unlock walk-back logic verified mathematically
- Template tracking uses ID, not name (verified explicitly)
- lastUsedBudgetId ownership logic tested and documented
- Tests verify query logic: "find previous locked budget using this template"
- Edge case where no previous budget exists handled (null restoration)
- Tests document expected behavior vs potential bugs discovered
- Code coverage includes template state transitions through complex scenarios
