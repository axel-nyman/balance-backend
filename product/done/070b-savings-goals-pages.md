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
  **archive** the goal. The archive confirm offers the 070a `releaseToBalance`
  choice: archive and free the earmark (balances unchanged, default), or archive
  *and* deduct the money from the backing accounts' balances (the goal was a real
  expense that's now paid) — phrase the two options plainly.

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
      confirmation that includes the `releaseToBalance` yes/no choice from 070a)
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
- The progress indicator on the **cards** should be a simple **progress bar**
  (like the todo page's) to keep the grid clean; richer history visualizations
  belong on the detail page in 070e.

## Out of scope

- Linking budget savings items to goals (070c).
- Reacting to manual balance changes (070d).
- Velocity-based predictions and end-date projections (070e) — show the raw
  target/allocated/progress only for now.

## Notes

- Depends on 070a's chosen shape for unallocated figures (account fields vs
  endpoint) — read the merged 070a before building.

## Completion notes

**Completed:** 2026-06-24 · **PR:** balance-frontend (branch `claude/peaceful-hamilton-n37ctb`) · bookkeeping in balance-backend (branch `claude/youthful-hamilton-n37ctb`)

Frontend for the savings-goals epic, built on the merged 070a backend. All acceptance criteria met; full frontend suite green (59 files, 527 tests, +13 new across `GoalsPage.test.tsx` and `GoalDetailPage.test.tsx`). `npm run lint` (0 errors), `npx tsc --noEmit`, and `npm run build` all pass.

### What shipped (frontend)
- Sidebar "Goals" entry (Target icon); routes `/goals` and `/goals/:id` under the app layout (`routes.ts`, `App.tsx`, `Sidebar.tsx`).
- `GoalsPage`: card grid of active goals (`GoalGrid` + `GoalCard`) with name, allocated/target progress bar, backing-account summary, "Complete" badge; loading skeletons, error state, empty state — all reusing the shared components.
- `GoalModal` (create/edit, RHF + Zod): name, optional target, optional end date; create-only optional initial allocation from an account's unallocated money with a client-side cap (backend still enforces the invariant).
- `GoalDetailPage`: progress summary, per-account allocation breakdown, and edit / assign-money / archive actions. Archived goals render read-only (actions hidden, "Archived" badge).
- `AllocateModal`: manual assign — sets an account's earmark (absolute), pre-filled with the current allocation, capped at `unallocated + current earmark`; zero removes.
- `ArchiveGoalDialog`: surfaces the 070a `releaseToBalance` choice via a plainly-phrased checkbox ("Also spend the money" → reduce backing balances; default off → just free the earmark).
- API/data: `api/goals.ts`, `useGoals/useGoal/useGoalHistory/useCreateGoal/useUpdateGoal/useAllocateToGoal/useArchiveGoal`, `queryKeys.goals` factory. Goal mutations invalidate both goals and accounts (unallocated figures change).
- Types: additive `allocatedAmount?` / `unallocatedAmount?` on `BankAccount` plus the savings-goal DTO types, mirroring 070a's `BankAccountResponse` and `SavingsGoalDtos`.

### Interpretation decisions
- **Unallocated source:** read 070a's additive `allocatedAmount` / `unallocatedAmount` on the accounts response (not a dedicated endpoint).
- **Made the two new `BankAccount` fields optional** in the TS type. They are always present on `/api/bank-accounts`, but a couple of places (e.g. `TodoItemList`) build partial account objects; optional avoids fabricating allocation data there and keeps the diff additive. Goals code defaults them with `?? 0`.
- **Assign is absolute "set" semantics** (matches the 070a `POST /{id}/allocations` contract): the field is the new earmark for that account, pre-filled with the existing amount; zero removes. Client cap = `account.unallocatedAmount + thisGoal'sCurrentEarmarkOnAccount`.
- **Create-time seed** offers a single optional account+amount (the backend accepts a list); richer multi-account seeding deferred — manual assign on the detail page covers it.
- **`GoalAllocationChange` history** is fetched by a hook (`useGoalHistory`) but not yet surfaced in the UI — the detail-page visualizations are 070e's scope. History/predictions intentionally out of scope here.

### Deploy ordering / note
- Frontend-only change; depends on the 070a backend being **deployed** (release-please PR balance-backend#55 merged) before this frontend is deployed, or the Goals page will 404 against production. The backend is already merged to `main`; this is a merge-ordering note for the maintainer, not a code dependency.

### Not done (later parts, by design)
- Budget-savings ↔ goal linking on lock (070c), manual-balance reallocation (070d), and progress/velocity visualizations + the allocation-history view (070e).
