# Make archived savings goals visible on the goals page

- **ID:** 140-archived-goals-visibility
- **Scope:** full-stack
- **Size:** S (≤ half a day)

## Why

Archiving a savings goal currently makes it vanish: `GET /api/savings-goals`
returns ACTIVE goals only, so an archived goal disappears from `/goals` and
its detail page (which already renders read-only, item 070b) and preserved
allocation history become unreachable unless you saved the URL. For the
couple, a finished goal ("Japan trip 2026") is a small victory worth being
able to look back on — today it silently evaporates.

## What

Expose archived goals additively and list them behind a collapsed section:

- **Backend:** `GET /api/savings-goals` gains an optional
  `includeArchived=true` query param (default `false` — the deployed frontend
  keeps working unchanged). When true, archived goals are appended with their
  `status` and `archivedAt` populated; active-goal summaries are unchanged.
  Note an archived goal's allocations were removed on archive, so its
  totalAllocated is 0 — that is correct, not a bug.
- **Frontend:** `/goals` shows a collapsed "Archived goals (N)" section below
  the active grid (same collapsible idiom as the wizard's "Show other
  recurring expenses (N)", item 040), rendered only when N > 0. Archived
  cards are visually muted (name, target if any, archived date in sv-SE
  format) and link to the existing read-only detail page, where the history
  chart and ledger still work.

## Acceptance criteria

- [ ] `GET /api/savings-goals` without the param returns exactly what it does
      today (active only) — backward compatible
- [ ] `GET /api/savings-goals?includeArchived=true` also returns archived
      goals with `status: ARCHIVED` and `archivedAt` set
- [ ] Soft-deleted goals are never returned in either mode
- [ ] `/goals` renders the collapsed archived section only when archived
      goals exist; expanding lists them muted with archived date
- [ ] Clicking an archived card opens the existing read-only goal detail page
      (edit/allocate/archive actions absent, history visible)
- [ ] Integration tests (backend) and component tests (frontend) cover the
      above; no new dependencies

## API changes (if backend)

`GET /api/savings-goals?includeArchived=true` — additive query param only.
No DTO shape changes required if the list item already carries
`status`/`archivedAt`; otherwise add them additively.

## UI notes (if frontend)

- `src/pages/GoalsPage.tsx` + `src/components/goals/GoalGrid.tsx`/`GoalCard.tsx`;
  fetch in `src/api/goals.ts` (`getGoals`), hook `useGoals` — either a param
  on the existing query or a separate `useArchivedGoals` keyed query, matching
  the `query-keys` factory.
- Keep the archived section quiet: muted `gray-500` text, no progress bar
  (allocations are gone by design).

## Out of scope

- Unarchiving / reactivating goals.
- Deleting archived goals from the UI.
- Any change to archive semantics (`releaseToBalance` etc.).

## Notes

- Backend pointers: `SavingsGoalController.getAllSavingsGoals`
  (`api/endpoints/SavingsGoalController.java:44`),
  `DomainService.getAllSavingsGoals` (`domain/services/DomainService.java:1402`),
  ACTIVE filter in `infrastructure/data/services/DataService.java:385`.
- The detail endpoint and `/history` already serve archived goals (item 070a),
  so the frontend detail page needs no API change.
