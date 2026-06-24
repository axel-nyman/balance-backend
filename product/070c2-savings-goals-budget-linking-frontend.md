# Savings goals — budget savings "goal" selector (frontend)

- **ID:** 070c2-savings-goals-budget-linking-frontend
- **Scope:** frontend
- **Size:** S

> **Frontend half of item 070c** (savings-goals budget linking). Split out from
> the original `070c` because the backend half (`070c1`, done) is independently
> mergeable/deployable and the frontend depends on **070b**'s `use-goals.ts`
> hooks, which must merge first. Implement after 070b lands.

## Why

The backend (`070c1`) now lets a budget **savings** item optionally reference a
savings goal: on lock that month's saving is earmarked toward the goal, and
unlock reverses it. The frontend needs to let the couple set and see that link.

## What

Add an optional **goal** selector to the budget savings add/edit flow and show
the linked goal on the savings section.

## Acceptance criteria

- [ ] The budget-detail savings add/edit modal
      (`src/components/budget-detail/SavingsItemModal.tsx`) gains an optional
      "Goal" selector (none / pick an active goal), reusing the existing
      account-select pattern. Selecting persists `savingsGoalId` via the savings
      create/update mutations; choosing "none" sends it absent/null.
- [ ] The wizard savings step gets the same optional selector.
- [ ] The savings section/rows show the linked goal's name when set.
- [ ] Only offered on **UNLOCKED** budgets (editing is already locked-out
      otherwise).
- [ ] Active goals are listed via `use-goals.ts` (070b); sv-SE / SEK formatting
      throughout; mobile-first.
- [ ] Tests cover: the selector renders and persists a link, editing changes/
      clears it, and that omitting it leaves today's behaviour unchanged.

## Backend contract (already shipped in 070c1)

- `CreateBudgetSavingsRequest` / `UpdateBudgetSavingsRequest` accept an optional
  `savingsGoalId` (UUID, nullable; omitting preserves current behaviour).
- `BudgetSavingsResponse` and the budget-detail `savings[]` items carry
  `savingsGoalId`.
- Linking a non-existent goal → 404; linking an archived goal → 400.

## Out of scope

- Manual balance-change reallocation (070d).
- Predictions / progress visualizations (070e).

## Notes

- Frontend-only. No backend changes; the `070c1` backend must be deployed first.
- Hooks: `use-budgets.ts` savings mutations already invalidate budget queries;
  no goal-list invalidation needed for the link itself, but funding totals on
  goals change when a linked budget locks (that view refresh is already covered
  by the goals/accounts polling).
