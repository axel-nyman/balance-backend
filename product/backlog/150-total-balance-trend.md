# Total-balance trend chart on the accounts page

- **ID:** 150-total-balance-trend
- **Scope:** full-stack
- **Size:** M (about a day)

## Why

The accounts page shows the household's total balance **today**, and the
budgets page now charts planned income/expenses/savings per month (item 110)
— but nothing shows how the couple's actual money has developed over time,
even though every change is already recorded in `BalanceHistory`. A small
"total balance over time" chart answers the most natural retrospective
question of the monthly routine: "is our money actually growing?" The
maintainer has explicitly welcomed focused charts that aid the routine.

## What

- **Backend:** a read-only endpoint that reconstructs month-end household
  totals from `balance_history`, e.g.
  `GET /api/bank-accounts/balance-trend` →
  `{ "points": [ { "year": 2026, "month": 3, "totalBalance": 123456.00 }, … ] }`
  covering up to the **12** most recent calendar months (including the
  current, in-progress month valued as of its latest entries). For each
  month-end and each account: the `balance` of the account's newest history
  entry with `changeDate` ≤ that month-end (ties broken by `createdAt`,
  matching the history pagination order), carried forward when a month has no
  entries. Sum across accounts. Soft-deleted accounts are excluded entirely —
  consistent with how deleted accounts vanish from today's total. Months
  before the first history entry are omitted (no leading zeros).
- **Frontend:** a quiet "Trends" card on `/accounts` (below the totals
  header) with a single-series dependency-free inline-SVG line chart, same
  idiom as the budgets Trends card (item 110) and
  `src/components/goals/GoalHistoryChart.tsx`. sv-SE month labels and
  `8 500 kr` amounts. Hidden entirely when fewer than 2 monthly points exist.

## Acceptance criteria

- [ ] Endpoint returns chronological month points (oldest → newest), max 12,
      each `totalBalance` equal to the sum of every non-deleted account's
      carried-forward month-end balance
- [ ] A month with no history entries for some account still counts that
      account at its carried-forward balance
- [ ] Balances of soft-deleted accounts never contribute, and months before
      the first history entry are omitted
- [ ] Money handled as `BigDecimal` end-to-end; integration tests cover
      carry-forward, the deleted-account rule, and the 12-month cap
- [ ] `/accounts` shows the Trends card when the endpoint yields ≥ 2 points
      and hides it otherwise; amounts/labels formatted sv-SE
- [ ] No new runtime dependencies; component tests for the visibility
      threshold and ordering

## API changes (if backend)

New additive endpoint only (sketch above; exact path/DTO naming follows
existing conventions — implementer's choice). Nothing existing changes, so
the deployed frontend is unaffected.

## UI notes (if frontend)

- Page: `src/pages/AccountsPage.tsx`; new hook + `query-keys` entry; fetch fn
  in `src/api/accounts.ts`.
- Chart pattern to copy: the item 110 budgets Trends card and its pure helper
  in `src/lib/budget-trends.ts` (selection/format logic unit-tested
  separately from the component).

## Out of scope

- Per-account series or account filters (one household total line only).
- Date-range pickers (fixed 12-month window).
- Backfilling or editing history data.
- Planned-vs-actual comparison against budgets (separate, bigger idea).

## Notes

- `balance_history` has `balance`, `change_date DATE`, `created_at`, indexed
  by `bank_account_id` (V1 baseline) — the reconstruction is a straightforward
  query or a small pure utility in `domain/utils/` (unit-testable, like
  `TransferCalculationUtils`).
- Manual corrections carry a user-picked `changeDate` (possibly in the past)
  while AUTOMATIC entries use the server date — using `changeDate` for
  month bucketing is the honest choice and matches the history drawer's
  ordering.
