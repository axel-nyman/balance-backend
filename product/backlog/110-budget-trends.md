# Month-over-month budget trends on the budgets page

- **ID:** 110-budget-trends
- **Scope:** frontend
- **Size:** M (about a day)

## Why

The couple now has a growing stack of locked budgets, but the app has no view
that compares one month to another — every number on the budgets grid and the
budget detail page is single-month. A small trends chart on the budgets page
answers the natural retrospective question of the monthly routine: "are we
spending less and saving more than we used to?" The maintainer has explicitly
welcomed focused charts that aid the routine (item 015 review).

## What

A "Trends" card at the top of the budgets list page (`/budgets`) showing the
locked budgets over time, using a dependency-free inline-SVG chart in the same
style as the goal detail page's `GoalHistoryChart`:

- Plot per-month **income**, **expenses**, and **savings** for up to the 12
  most recent LOCKED budgets, in chronological order (oldest → newest).
- Below or beside the chart, a small savings-rate stat (latest locked month vs.
  the average of the plotted months) as plain text — no second chart needed.
- Draft (UNLOCKED) budgets are excluded — they are plans, not outcomes.
- The card renders only when there are **2 or more** locked budgets; otherwise
  it is hidden entirely (no empty-state noise for new users).
- Mobile-first: the chart must stay legible on a phone; on desktop it can
  widen. Month labels in compact sv-SE style, amounts formatted `8 500 kr`.

Data source is the existing `GET /api/budgets` list — `BudgetSummary.totals`
already carries `income`, `expenses`, `savings` per budget
(frontend `src/api/types.ts:173-192`). **No backend change.**

## Acceptance criteria

- [ ] `/budgets` shows a Trends card when ≥ 2 locked budgets exist, and hides
      it (renders nothing) with 0–1 locked budgets
- [ ] The chart plots income, expenses, and savings per month for up to the 12
      most recent locked budgets, ordered chronologically by (year, month)
- [ ] UNLOCKED budgets never appear in the chart, regardless of their dates
- [ ] Amounts and month labels use sv-SE formatting consistent with the rest
      of the app
- [ ] No new runtime dependencies — inline SVG like
      `src/components/goals/GoalHistoryChart.tsx`
- [ ] Component tests cover: hidden below the 2-locked-budget threshold,
      chronological ordering, draft exclusion, and the 12-month cap

## API changes (if backend)

None. Uses the existing budget list response.

## UI notes (if frontend)

- Page: `src/pages/BudgetsPage.tsx` (data already available via the
  `useBudgets` hook).
- Chart pattern to copy: `src/components/goals/GoalHistoryChart.tsx`
  (dependency-free SVG, existing axis/label conventions).
- Presentation: shadcn/ui `Card`; keep it visually quiet (muted grid, the
  existing color conventions).
- Line-vs-bar choice is the implementer's; keep the three series visually
  distinct and add a small legend.

## Out of scope

- Any backend change.
- Per-category, per-account, or per-line-item breakdowns.
- Date-range pickers or filters (the fixed 12-month window is enough).
- Planned-vs-actual comparison against real account balances (a separate,
  bigger idea).

## Notes

- `BudgetCard`/`BudgetSummary` show how existing code formats these totals.
- Bookkeeping as usual in balance-backend (spec → `done/`, STATE.md update)
  via a small `docs:` PR alongside the frontend PR.
