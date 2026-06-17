# Savings goals — sidebar page, detail page, create/edit/assign (frontend)

- **ID:** 070b-savings-goals-pages
- **Scope:** frontend
- **Size:** M (about a day)

> **Part 2 of 5** of the savings-goals feature. Depends on **070a** (backend
> foundation) being merged and deployed. Pairs with it to deliver a usable
> first version: see, create, fund, and archive goals.

## Why

Once the backend tracks goals and allocations (070a), the couple needs a place
to actually use them: a goals section in the sidebar, a card grid of their
goals, and a detail page to inspect and adjust a single goal. This is the
visible payoff of the feature.

## What

Add a **Goals** page reachable from the sidebar:

- **List page** (`/goals`): a card grid of active goals. Each card shows name,
  total allocated, target (if set), a progress indicator (allocated / target),
  and the backing accounts. A "new goal" action opens a modal.
- **Create/edit goal modal**: name, optional target, optional end date, and an
  optional initial allocation picker that draws from accounts' **unallocated**
  money (the backend returns unallocated per account from 070a). React Hook
  Form + Zod, explicit save, sv-SE / SEK formatting.
- **Detail page** (`/goals/:id`): goal summary, per-account allocation
  breakdown, progress, and actions to **edit** the goal, **manually assign**
  money from an account (capped at that account's unallocated amount), and
  **archive** the goal (with a confirm — archiving frees the money).

## Acceptance criteria

- [ ] Sidebar gains a "Goals" entry; routes `/goals` and `/goals/:id` exist and
      render under the app layout
- [ ] List page shows a card grid of active goals with name, allocated, target,
      progress indicator, and backing accounts; empty state when none
- [ ] Create modal can create a goal with name + optional target + optional end
      date, and optionally seed an initial allocation from an account without
      exceeding its unallocated amount (client-side guard + backend enforces)
- [ ] Detail page shows per-account allocation breakdown and progress, and
      supports edit, manual assign (capped at unallocated), and archive (with
      confirmation)
- [ ] All money rendered with the existing sv-SE formatters; mobile-first
      layout consistent with the accounts/budgets pages
- [ ] Loading skeletons and error toasts follow existing patterns
- [ ] React Query hooks + a `queryKeys.goals` factory entry; mutations
      invalidate the right keys (goals list/detail, and accounts since
      unallocated figures change)
- [ ] Component/MSW tests cover: list render + empty state, create (with and
      without seed allocation + the unallocated cap), manual assign, and archive

## UI notes

- Sidebar: `src/components/layout/Sidebar.tsx` (`navItems`); routes in
  `src/routes.ts`; route elements in `src/App.tsx`. Add pages under
  `src/pages/` (e.g. `GoalsPage.tsx`, `GoalDetailPage.tsx`).
- Reuse: `AccountCard`/card patterns, `formatCurrency*` (`src/lib/utils.ts`),
  the existing modal + RHF + Zod pattern (see `SavingsItemModal`,
  `ExpenseItemModal`), shadcn `Card`/`Dialog`/`Sheet`, and the budgets card grid
  layout as a model for the goals grid.
- New API module `src/api/goals.ts` + types in `src/api/types.ts`; hooks in
  `src/hooks/use-goals.ts`; extend `src/hooks/query-keys.ts` with `goals`.
- The progress indicator should be a simple **progress bar** (like the todo
  page's), not a chart — keeps within the app's no-reports/charts stance.

## Out of scope

- Linking budget savings items to goals (070c).
- Reacting to manual balance changes (070d).
- Velocity-based predictions and end-date projections (070e) — show the raw
  target/allocated/progress only for now.

## Notes

- Depends on 070a's chosen shape for unallocated figures (account fields vs
  endpoint) — read the merged 070a before building.
