# Savings goals — progress, end-date and velocity-based projections (frontend)

- **ID:** 070e-savings-goals-predictions
- **Scope:** frontend
- **Size:** M (about a day)

> **Part 5 of 5** of the savings-goals feature. Depends on **070a–070b** (goals,
> allocations, allocation history, detail page) and reads better once **070c**
> exists (so locking budgets produces allocation history to derive velocity
> from).
>
> Data visualizations are welcome here — "no reports/charts" is **not** a
> non-goal (clarified by the maintainer in the item 015 review). A small,
> focused chart of a goal's progress over time is in scope; keep it tasteful and
> consistent with the app's clean, Apple-inspired aesthetic.

## Why

The couple wants to know whether a goal is on track: when, at the current
saving pace, it will be reached, and — if the goal has an end date — how much
they'd need to save per month to hit it in time. This turns goals from a static
earmark into a planning aid for the monthly routine.

## What

On the goal detail page (070b), add:

- A **progress** view of allocated vs target (progress bar already present from
  070b — enrich with remaining amount and % to target).
- A **history visualization**: a simple line/area chart of the goal's allocated
  amount over time, built from the goal's allocation history
  (`GET /api/savings-goals/{id}/history`, 070a). Optionally overlay the target
  and end date.
- A **projection**: using historical saving velocity for the goal (how its
  allocation has grown over time), estimate a date the target will be reached,
  shown as text (e.g. "On track to reach 50 000 kr around March 2027 at the
  current pace") and, where it reads well, on the history chart.
- If the goal has an **end date**: compare the projection to it and show the
  monthly amount needed to reach the target by the end date (e.g. "Save
  4 200 kr/month to reach this by your Dec 2026 date — currently ~3 100 kr/mo").

## Acceptance criteria

- [ ] Goal detail shows remaining-to-target and % progress alongside the
      existing bar
- [ ] A history visualization of allocated-over-time is shown when the goal has
      allocation history; a graceful empty/low-data state otherwise
- [ ] When enough history exists, a projected completion date is shown from the
      goal's saving velocity; when history is insufficient, a graceful
      "not enough history yet" message instead of a misleading guess
- [ ] For goals with an end date: the required monthly contribution to hit the
      target by that date is shown, compared against current pace, with clear
      ahead/behind framing
- [ ] All money/dates use sv-SE / SEK and Europe/Stockholm conventions
- [ ] Tests cover: progress math, projection with sufficient history,
      insufficient-history fallback, and the end-date required-contribution math
      (pure functions unit-tested)

## Velocity / history source

Velocity is derived from the goal's **allocation history**, which 070a already
persists (`GoalAllocationChange`, exposed via
`GET /api/savings-goals/{id}/history`). No new backend work should be needed —
read that endpoint, reconstruct the allocated-amount-over-time series on the
frontend, and compute velocity from it. If the endpoint turns out to be
inadequate in practice, flag a small additive backend enhancement as a
follow-up rather than expanding this item.

## UI notes

- Put the progression, history chart, and projection on the goal detail page
  from 070b. Keep calculations in pure functions under `src/lib/` (e.g.
  `goal-projection.ts`) for easy unit testing, mirroring the existing
  `budget-lifecycle.ts` / `utils.ts` style.
- For the chart, prefer a lightweight approach consistent with the stack. A
  charting library (e.g. `recharts`) is acceptable since visualizations are in
  scope — justify the dependency choice and bundle impact in the PR, or use a
  minimal SVG/`<progress>`-style rendering if that's enough. Keep it to this one
  goal-history view; portfolio-wide dashboards remain out of scope.

## Out of scope

- Account/portfolio-wide reporting or cross-goal dashboards (this item is a
  single goal's detail view only).
- Changing how allocations are produced (070a/070c/070d own that).

## Notes

- This is the lowest-priority slice of the savings-goals feature; the feature is
  fully usable after 070a–070d.
