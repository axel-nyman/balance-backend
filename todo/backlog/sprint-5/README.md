# Sprint 5: End-to-End System Testing

## Overview

Sprint 5 focuses on comprehensive end-to-end testing that goes beyond individual endpoint validation. These tests simulate realistic user workflows, test edge cases that only appear when looking at the system holistically, and verify data consistency across complex multi-step operations.

## Philosophy

Unlike the existing integration tests that focus on individual endpoints one at a time, these E2E tests:

- **Execute multi-step workflows** - Simulating how real users interact with the system
- **Test state consistency** - Verifying that totals, balances, and references stay in sync
- **Find edge cases** - Discovering bugs that only appear in the "big picture"
- **Stress test boundaries** - Pushing the system to realistic extremes
- **Verify atomicity** - Ensuring complex operations fully succeed or fully fail

## Sprint Stories (30-39)

### Core System Integrity (Stories 30-35)

**Story 30: Data Consistency & Integrity** (5 tests)
- Balance history consistency across operations
- Orphaned data prevention
- Manual updates between lock/unlock cycles
- Future-dated entries handling
- Budget ID linkage in automatic history

**Story 31: Numeric Precision & Rounding** (5 tests)
- Rounding error accumulation over many cycles
- Very large monetary amounts
- Negative balance calculations
- Fractional amount precision
- Exact zero detection for budget locking

**Story 32: Transfer Algorithm Correctness** (5 tests)
- Circular transfer prevention
- Self-transfer prevention
- Transfer count minimization
- Edge case: all-deficit accounts
- Dominant hub account pattern

**Story 33: State Machine & Business Rules** (5 tests)
- Race condition prevention for unlocked budgets
- Strict zero-balance validation
- Most recent budget unlock constraint
- Locked budget modification prevention
- Account deletion by budget status

**Story 34: Recurring Expense Tracking** (5 tests)
- Out-of-order budget locking
- Template deletion with active references
- Sequential unlocking walk-back
- Identical template names handling
- Selective template update on unlock

**Story 35: Todo List Lifecycle** (5 tests)
- Todo list cleanup on relock
- Empty todo list handling
- Deterministic todo generation
- Todo list access control
- Completion state isolation

### System Robustness (Stories 36-39)

**Story 36: Multi-Budget Temporal** (5 tests)
- 30+ budget cycles performance
- Budget recreation after deletion
- Leap year handling
- Year boundary budget ordering
- Extreme year value handling

**Story 37: Transaction Atomicity & Rollback** (4 tests)
- Lock operation rollback
- Unlock operation rollback
- Corrupted balance history handling
- Concurrent budget operations

**Story 38: Performance & Scale** (3 tests)
- Budget with 50 accounts and 200+ items
- Balance history pagination with 1000+ entries
- Account with 100+ savings items

**Story 39: Security & Validation** (4 tests)
- Invalid month validation
- Year range validation
- SQL injection prevention
- Negative amount rejection

## Total Test Count

**41 comprehensive E2E test scenarios** covering:
- ✅ Data integrity and consistency
- ✅ Numeric precision and calculations
- ✅ Algorithm correctness
- ✅ Business rule enforcement
- ✅ State machine transitions
- ✅ Temporal complexity
- ✅ Transaction atomicity
- ✅ Performance at scale
- ✅ Security boundaries

## Implementation Strategy

### Phase 1: Core Integrity (Stories 30-32)
Focus on data consistency, numeric precision, and algorithm correctness - the foundation of system reliability.

### Phase 2: Business Logic (Stories 33-35)
Verify business rules, state management, and feature-specific edge cases.

### Phase 3: Robustness (Stories 36-39)
Test temporal scenarios, transaction handling, performance limits, and security.

## Test Class Organization

Each story will create tests in one of these classes:
- `DataConsistencyE2ETest` (Story 30)
- `NumericPrecisionE2ETest` (Story 31)
- `TransferAlgorithmE2ETest` (Story 32)
- `StateMachineBusinessRulesE2ETest` (Story 33)
- `RecurringExpenseTrackingE2ETest` (Story 34)
- `TodoListLifecycleE2ETest` (Story 35)
- `MultiBudgetTemporalE2ETest` (Story 36)
- `TransactionAtomicityE2ETest` (Story 37)
- `PerformanceScaleE2ETest` (Story 38)
- `SecurityValidationE2ETest` (Story 39)

All in: `src/test/java/org/example/axelnyman/main/integration/`

## Key Principles

1. **Given-When-Then Structure** - Clear test organization
2. **Realistic Scenarios** - Simulate actual user behavior
3. **Meaningful Names** - `shouldXWhenY` naming convention
4. **Comprehensive Cleanup** - Tests are independent
5. **Performance Awareness** - Document acceptable thresholds
6. **Edge Case Focus** - Find the bugs that unit tests miss

## Success Criteria

- All 41 test scenarios implemented and passing
- Tests reveal real bugs (not just verify current behavior)
- Performance benchmarks established and documented
- Edge cases comprehensively covered
- System confidence increased for production deployment

## Notes

These tests are designed to be **creative and thorough** - finding the edge cases and potential flaws that only appear when viewing the system holistically. They complement (not replace) the existing endpoint-focused integration tests.

---

**Start with Story 30 and proceed sequentially through Sprint 5.**
