# New feature frenzy

- **ID:** 015-scope-new-features
- **Scope:** product
- **Size:** S (≤ half a day)

## Why

I have several unresearched ideas for new features I want implemented, they all need to be clearly scoped before implementation.

## What

Take each idea, create a new .md file in /product following the TEMPLATE.md and scope it clearly.

## Idea 1

The delete button in the mobile new item modal in the budget creation wizard is clipped in the corners by the screen on iPhone 17 pro. Let’s add some room for those buttons to be fully visible.

## Idea 2

All NULL recurring expenses are always visible in the budget creation wizard. Let’s move all items that aren’t due for that specific month into some kind of hidden view that either opens in a modal or expands.

## Idea 3

Let’s see if we can preserve screen real estate in the budget creation wizard even more. Maybe things like accounts/amounts aren’t needed before adding recurring expenses for example, that is only relevant information once we’ve decided to add them to the budget? Also all quick-add cards could be significantly downsized on desktop.

## Idea 4

Real-time updates across the app. Would be nice when me and my wife have it open on different units simultaneously.

## Idea 5 - the BIG one

I'd like to be able to create savings goals in the app, that live in their own page in the sidebar. A savings goal should be connected to one or several bank accounts, and each bank account can be connected to several savings goals. The reason for connecting bank account and these goals are so that we can keep track of what money is already allocated to a goal and what money is not. When creating a new goal, we can choose starting amounts from existing unallocated money on our bank accounts. When creating a budget, we can connect each savings item to 0 or 1 savings goal, which automatically allocates that money in that account to that specific goal upon locking the budget. Unlocking the budget should also undo this. The page in the sidebar shows a list/grid of my created goals in cards with some vital information. Clicking one of these opens a detail page where we can see more information and visualizations of that specific goal. We should also be able to edit the goal and manually assign money to it from this page. Each goal can also have an optional "end date". It'd be cool if it can use historical saving velocity for that goal to calculate and display predictions on when we will reach that goal, which can be visualized together with the end date and how much we'd need to save to reach the goal within the desired end date. We also need to think about what happens when a goal is completed, should the money be "unallocated" automatically? Probably not, since we don't want that money immediately visible when allocating money to other goals. Maybe the goal could be archived, which unallocates the funds, making it up to the user when to do this. Another point to think about is when the amount of money in a bank account is changed manually. If an account is 100% allocated to three different savings goals, and I change the account balance, those goals need to update. The user should be informed and be able to on their own decide how that reallocation split should look. If only a single goal is connected to the bank account, the user should be informed but the unallocation automatic. And if the account is 50% allocated and the amount reduced by only 10%, nothing will need to happen to the goal at all.

## Idea 6

Create a github actions that builds and pushes an "unstable" or "pre-release" version of both frontend & backend on every merge/commit to the main branch. it shouldn't run if tests fail though, and maybe not if the only changes are docs etc.. the reason for this feature is that i want to setup a testing deployment of the app to test new features before creating an actual release to prod.

## Out of scope

Don't implement anything.

## Notes

Split anything too large into several smaller features if needed.

## Completion notes

- **Date:** 2026-06-17
- **Repo / PR:** balance-backend (docs-only) — branch
  `claude/youthful-hamilton-gh2fag`. No code changed; this item only scopes
  specs. A tiny companion frontend docs PR drops "reports/charts" from the
  frontend `CLAUDE.md` Non-Goals list (per the maintainer review); no app code
  is built (per "Out of scope: Don't implement anything").

### What was produced

Each of the six ideas was turned into a TEMPLATE-shaped spec in `product/`,
grounded in the actual code (file paths and method/line references included so
the implementer can act). Idea 5 ("the BIG one") was split into five sequenced,
independently-mergeable parts as the spec invited. New items, by priority:

| New item | Idea | Scope | Size |
|---|---|---|---|
| `020-unstable-prerelease-images` | 6 — `unstable` (on merge) + per-PR Docker images | full-stack (CI) | M |
| `030-wizard-modal-button-safe-area` | 1 — clipped modal buttons on iPhone | frontend | S |
| `040-wizard-hide-non-due-recurring` | 2 — collapse not-due recurring in wizard | frontend | S |
| `050-wizard-density` | 3 — tighter wizard / smaller desktop quick-add cards | frontend | M |
| `060-near-realtime-refresh` | 4 — keep two open sessions in sync | frontend | S |
| `070a-savings-goals-backend-foundation` | 5 — entity + allocation ledger + history + CRUD | backend | M |
| `070b-savings-goals-pages` | 5 — sidebar page, detail, create/edit/assign | frontend | M |
| `070c-savings-goals-budget-linking` | 5 — link budget savings, allocate on lock | full-stack | M |
| `070d-savings-goals-balance-reallocation` | 5 — manual-balance-change reallocation | full-stack | M |
| `070e-savings-goals-predictions` | 5 — progress, end-date & velocity projections + chart | frontend | M |

Priority order reflects the **maintainer's item 015 review**: the unstable/
per-PR images (`020`) were moved to the front so later features can be test-
deployed before merge. The wizard button fix moved `020 → 030`.

### Interpretation decisions & flags

- **Idea 4 (real-time):** scoped as the simplest data-safe interpretation —
  React Query background polling, frontend-only, no backend infra (the backend
  has no SSE/WebSocket/messaging today). True push is called out as a possible
  follow-up inside `060`, not built.
- **Idea 5 split rationale:** `070a` is a pure ledger over existing balances and
  never mutates `currentBalance`; the only places balances and allocations
  interact are the lock flow (`070c`) and manual balance changes (`070d`), which
  are isolated into their own specs to keep the safety-critical lock/unlock
  invariant front-of-mind.
- **Idea 5 visualizations:** originally flagged against a "no reports/charts"
  non-goal; the maintainer clarified in review that charts/visualizations are
  **not** a non-goal, so `070e` now includes a goal-history chart and the flag
  is removed (STATE.md and ROUTINE_PROMPT.md non-goal lists updated to match).
- **Idea 6 (CI):** `020` explicitly authorizes adding `.github/workflows/` files
  (normally forbidden by the routine), while leaving `release.yml`,
  release-please config, and Dockerfiles untouched.

### Review revisions (2026-06-17, maintainer review of the original PR)

The maintainer reviewed the first draft of these specs and asked for changes,
applied in the same PR:

1. **"No reports/charts" is not a non-goal.** Removed it from STATE.md and
   ROUTINE_PROMPT.md non-goal lists; `070e` and `070b` updated so data
   visualizations are explicitly in scope.
2. **`070a`** gains (a) an append-only `GoalAllocationChange` history ledger so
   allocation changes are tracked over time (and preserved when a goal is
   archived, for future statistics), and (b) an archive `releaseToBalance`
   option that can also deduct the freed money from account balances (archiving
   as "paying" a big planned expense). Cascaded into `070b` (archive UI +
   history), `070c` (writes `BUDGET_LOCK` history), `070d` (`BALANCE_REALLOCATION`
   history), and `070e` (velocity now sourced from the history endpoint).
3. **`070d`** now also handles balance **increases**: single-goal checkbox
   (default keyed off whether the account was ≈100% allocated) and multi-goal
   manual distribution, in addition to the existing decrease/deficit rules.
4. **`020` (was `090`)** reprioritised to first and extended to also build/push
   a per-PR image (`pr-<number>`, branch-name-agnostic) so a feature can be
   test-deployed before it's merged.

### Deviations / cut

- Nothing implemented, per the item's "Out of scope". These specs are proposals;
  the maintainer prioritizes/approves them by merging this PR, and a later run
  picks them up lowest-number-first.
