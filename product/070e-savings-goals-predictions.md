# Savings goals — progress, end-date and velocity-based projections (frontend)

- **ID:** 070e-savings-goals-predictions
- **Scope:** frontend (with optional small backend velocity endpoint)
- **Size:** M (about a day)

> **Part 5 of 5** of the savings-goals feature. Depends on **070a–070b** (goals,
> allocations, detail page) and reads better once **070c** exists (so locking
> budgets produces allocation history to derive velocity from).
>
> ⚠️ **Non-goal tension — confirm before building.** STATE.md lists
> "no reports/charts" as a firm non-goal. This item is the part of idea 5 most
> likely to brush against that. It is scoped deliberately to **simple progress
> bars and textual projections, not charting**, but the maintainer should
> confirm the desired visual fidelity (and whether any charting at all is
> wanted) before implementation. If full charts are out, ship the textual
> version; if a chart library is explicitly approved, treat that as a scope
> change recorded in the PR.

## Why

The couple wants to know whether a goal is on track: when, at the current
saving pace, it will be reached, and — if the goal has an end date — how much
they'd need to save per month to hit it in time. This turns goals from a static
earmark into a planning aid for the monthly routine.

## What

On the goal detail page (070b), add:

- A **progress** view of allocated vs target (progress bar already present from
  070b — enrich with remaining amount and % to target).
- A **projection**: using historical saving velocity for the goal (how its
  allocation has grown over time), estimate a date the target will be reached,
  shown as text (e.g. "On track to reach 50 000 kr around March 2027 at the
  current pace").
- If the goal has an **end date**: compare the projection to it and show the
  monthly amount needed to reach the target by the end date (e.g. "Save
  4 200 kr/month to reach this by your Dec 2026 date — currently ~3 100 kr/mo").

Keep visuals to progress bars and clear text/number callouts, consistent with
the app's clean, no-charts aesthetic, unless charting is explicitly approved
(see the non-goal note above).

## Acceptance criteria

- [ ] Goal detail shows remaining-to-target and % progress alongside the
      existing bar
- [ ] When enough history exists, a projected completion date is shown from the
      goal's saving velocity; when history is insufficient, a graceful
      "not enough history yet" message instead of a misleading guess
- [ ] For goals with an end date: the required monthly contribution to hit the
      target by that date is shown, compared against current pace, with clear
      ahead/behind framing
- [ ] All money/dates use sv-SE / SEK and Europe/Stockholm conventions
- [ ] No charting library is added unless the maintainer approved it in review
- [ ] Tests cover: progress math, projection with sufficient history,
      insufficient-history fallback, and the end-date required-contribution math
      (pure functions unit-tested)

## Velocity / history source

Velocity needs a history of the goal's allocation over time. Decide the source
and record it in the PR:

- **Preferred (minimal):** derive velocity on the **frontend** from data already
  available (e.g. allocation changes surfaced via existing endpoints / the
  goal's created date and current allocated amount as a coarse first pass).
- **If insufficient:** a small **additive backend** endpoint returning the
  goal's allocation history (e.g. `GET /api/savings-goals/{id}/history`) backed
  by timestamps already written when allocations change. Keep it additive and
  backward compatible. Prefer not to add a new history table unless 070a/070c's
  data is genuinely inadequate — flag that as a follow-up rather than expanding
  this item.

## UI notes

- Put the projection in the goal detail page from 070b. Keep calculations in
  pure functions under `src/lib/` (e.g. `goal-projection.ts`) for easy unit
  testing, mirroring the existing `budget-lifecycle.ts` / `utils.ts` style.

## Out of scope

- Account/portfolio-wide reporting or dashboards (firmly a non-goal).
- Any charting beyond progress bars unless explicitly approved.
- Changing how allocations are produced (070a/070c/070d own that).

## Notes

- This is the lowest-priority slice of the savings-goals feature; the feature is
  fully usable after 070a–070d. Build only after confirming the non-goal
  question above.
