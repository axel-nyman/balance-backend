# Story 39: Security & Validation E2E Tests

**As a** developer
**I want to** verify input validation and security controls prevent invalid or malicious data
**So that** the system rejects bad input at boundaries and doesn't allow data corruption

## Acceptance Criteria

- Invalid month values (0, 13, -1) are rejected
- Unreasonable year values outside allowed range are rejected
- Special characters in auto-generated comments don't cause SQL injection
- Negative amounts in income/expenses/savings are rejected

## Test Specifications

### Test 1: Invalid Month Validation

**Test Name:** `shouldRejectBudgetCreationWithInvalidMonthValues`

**Description:** Tests that month validation enforces 1-12 range at API boundary.

**Given:**
- Attempt to create budgets with various invalid month values

**When:**
- POST /api/budgets with different invalid months:
  - month = 0, year = 2025
  - month = 13, year = 2025
  - month = -1, year = 2025
  - month = 100, year = 2025

**Then:**
- All requests return 400 Bad Request
- Error messages user-friendly:
  - "Invalid month value. Must be between 1 and 12"
  - OR "Month must be between 1 and 12, but was: 0"
- No budget created in database
- Validation happens before any database interaction
- Validation annotation: `@Min(1) @Max(12)` on month field

**Boundary cases that should SUCCEED:**
- month = 1, year = 2025 → 201 Created
- month = 12, year = 2025 → 201 Created

**Why:** Basic input validation. Tests that DTO validation annotations work correctly and reject out-of-range values before reaching business logic.

---

### Test 2: Year Range Validation

**Test Name:** `shouldRejectBudgetCreationWithUnreasonableYearValues`

**Description:** Tests that year validation enforces reasonable range (e.g., 2000-2100).

**Given:**
- System has configured year range: 2000-2100
- Attempt to create budgets with invalid year values

**When:**
- POST /api/budgets with different invalid years:
  - month = 1, year = 1999 (below minimum)
  - month = 1, year = 2101 (above maximum)
  - month = 1, year = 0
  - month = 1, year = -2024
  - month = 1, year = 999999

**Then:**
- All requests return 400 Bad Request
- Error messages indicate allowed range:
  - "Year must be between 2000 and 2100"
  - OR "Invalid year value: 1999"
- No budget created in database

**Boundary cases that should SUCCEED:**
- month = 1, year = 2000 → 201 Created (minimum valid)
- month = 1, year = 2100 → 201 Created (maximum valid)
- month = 12, year = 2050 → 201 Created (mid-range)

**Why:** Prevents nonsensical year values. Too far in past (pre-2000) or future (post-2100) indicates data error or attack. Tests reasonable business constraints.

---

### Test 3: SQL Injection Prevention in Auto-Generated Comments

**Test Name:** `shouldHandleSpecialCharactersInBalanceHistoryCommentsWithoutSQLInjection`

**Description:** Tests that auto-generated balance history comments don't create SQL injection vulnerabilities, even with malicious budget data.

**Given:**
- Create budget with potentially malicious values:
  - month = 12
  - year = 2024
  - System will generate comment: "Budget lock for 12/2024"
- Create expense with malicious name: "Test'; DROP TABLE balance_history; --"

**When:**
- Lock budget
- System generates balance history entry with comment

**Then:**
- Balance history created successfully
- Comment stored as literal string (no SQL execution)
- No tables dropped or SQL commands executed
- Database tables intact (balance_history still exists)
- Special characters escaped/parameterized in queries
- System uses PreparedStatement or JPA parameter binding (not string concatenation)

**Additional test cases:**
- Expense name with quotes: "Bob's Burgers"
- Account name with semicolons: "Account; Test"
- Comment with SQL keywords: "SELECT * FROM users"

**Why:** Security test. Even though comments are auto-generated, malicious data in related fields (expense names, account names) might be concatenated into comments. Tests that JPA/JDBC parameterization prevents injection.

---

### Test 4: Negative Amount Rejection

**Test Name:** `shouldPreventNegativeAmountsInIncomeExpensesSavings`

**Description:** Tests that DTO validation rejects negative monetary amounts at API boundary.

**Given:**
- Create budget (unlocked)
- Attempt to add items with negative amounts

**When:**
- POST /api/budgets/{id}/income with amount = -500.00
- POST /api/budgets/{id}/expenses with amount = -300.00
- POST /api/budgets/{id}/savings with amount = -100.00

**Then:**
- All requests return 400 Bad Request
- Error messages indicate constraint:
  - "Amount must be positive"
  - OR "Amount must be greater than or equal to 0"
- No items created in database
- Validation annotations: `@PositiveOrZero` or `@Min(0)` on amount fields

**Boundary cases:**
- amount = 0.00 → decision: allow or reject? (Should be rejected: $0 expense is meaningless)
- amount = 0.01 → 201 Created (minimum valid positive)
- amount = 999999.99 → 201 Created (large but valid)

**Alternative:** If system allows $0 amounts, test should verify:
- amount = -0.01 → 400 Bad Request
- amount = 0.00 → 201 Created

**Why:** Negative amounts could break calculations (double negative becomes positive). Semantic validation: negative income/expense doesn't make sense (use opposite category instead). Tests validation prevents logical errors.

---

## Technical Implementation

1. **Test Class:** `SecurityValidationE2ETest`
   - Location: `src/test/java/org/example/axelnyman/main/integration/`
   - Focus on input validation and security boundaries

2. **Helper Methods Needed:**
   ```java
   private void assertValidationError(Response response, String expectedMessageFragment)
   private void assertBudgetCreationFails(int month, int year, int expectedStatus)
   private void assertAmountValidationFails(String endpoint, BigDecimal amount)
   private void assertDatabaseTablesIntact()
   private int countTables(String tableNamePattern)
   ```

3. **SQL Injection Testing:**
   ```java
   private void assertNoSQLInjection(String maliciousInput) {
       // Verify tables still exist after operations with malicious input
       int tableCount = jdbcTemplate.queryForObject(
           "SELECT COUNT(*) FROM information_schema.tables WHERE table_name LIKE '%balance%'",
           Integer.class
       );
       assertThat(tableCount).isGreaterThan(0);
   }

   private void createMaliciousExpense(UUID budgetId, String maliciousName) {
       CreateBudgetExpenseRequest request = new CreateBudgetExpenseRequest(
           maliciousName, // "Test'; DROP TABLE balance_history; --"
           new BigDecimal("100.00"),
           accountId,
           null,
           null,
           false
       );
       // ... post request
   }
   ```

4. **Boundary Value Testing:**
   ```java
   @ParameterizedTest
   @ValueSource(ints = {0, -1, 13, 100, -100})
   void shouldRejectInvalidMonths(int invalidMonth) {
       Response response = createBudget(invalidMonth, 2025);
       assertThat(response.getStatus()).isEqualTo(400);
       assertThat(response.getBody()).contains("month");
   }

   @ParameterizedTest
   @CsvSource({
       "1, 2000, true",   // boundary: minimum valid
       "12, 2100, true",  // boundary: maximum valid
       "1, 1999, false",  // boundary: below minimum
       "1, 2101, false"   // boundary: above maximum
   })
   void testYearBoundaries(int month, int year, boolean shouldSucceed) {
       Response response = createBudget(month, year);
       if (shouldSucceed) {
           assertThat(response.getStatus()).isEqualTo(201);
       } else {
           assertThat(response.getStatus()).isEqualTo(400);
       }
   }
   ```

5. **Validation Error Inspection:**
   ```java
   private void assertValidationErrorContains(
       MockHttpServletResponse response,
       String fieldName,
       String messageFragment
   ) throws Exception {
       assertThat(response.getStatus()).isEqualTo(400);
       String body = response.getContentAsString();
       assertThat(body).contains(fieldName);
       assertThat(body).contains(messageFragment);
   }
   ```

## Definition of Done

- All 4 test scenarios implemented and passing
- Boundary value tests use @ParameterizedTest for multiple cases
- Error messages verified (user-friendly, not stack traces)
- SQL injection test verifies database integrity after malicious input
- Validation happens at DTO level (before service logic)
- Tests document which validation annotations are used (@Min, @Max, @PositiveOrZero, etc.)
- Negative amount policy documented (zero allowed or not?)
- Year range configurable or hardcoded (document decision)
- Tests verify proper HTTP status codes (400 for validation, not 500)
- Security test covers both auto-generated and user-provided content
- All validation errors return consistent JSON error format
- Code coverage includes validation error handling in GlobalExceptionHandler
