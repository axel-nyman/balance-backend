# Balance — State of the App

> **Living document.** This is the first thing a fresh agent (or human) should
> read. Every completed backlog item MUST update this file (see
> `product/README.md`). Deeper history: `product/done/`, each repo's
> `CHANGELOG.md` (generated — never hand-edit), and `.claude/thoughts/` in
> both repos for engineering research and plans.

**Last updated:** 2026-06-30 (item 090 — reconciled backlog dir to `product/backlog/`)

## What Balance is

Self-hosted budgeting app for one couple managing shared monthly finances on
their home network. Two trusted users, one household. Swedish context:
currency SEK formatted `8 500 kr` (locale `sv-SE`), timezone Europe/Stockholm.
Production runs on a Raspberry Pi via docker compose and holds the couple's
real financial data — **data safety beats feature count**.

**Mission:** make the couple's monthly money routine fast, clear, and hard to
get wrong. It is not a general-purpose finance platform.

**Non-goals (firm):** no auth/user accounts, no bank integrations, no
investments or debt tracking, no data export, no dark mode, no i18n.
(Data visualizations/charts are **not** a non-goal — clarified by the maintainer
in the item 015 review; focused charts that aid the monthly routine are welcome.)

## The monthly routine it supports

1. **Plan** — create next month's budget in a 5-step wizard (month → income →
   expenses → savings → review), optionally copying from the last budget.
   Every line item is tied to a bank account. Recurring expense templates due
   that month are grouped on top for one-click add ("add all due"). A budget
   must balance to exactly zero (income = expenses + savings) to be created.
2. **Lock** — locking makes the budget read-only, computes the minimal set of
   inter-account transfers to cover each account's planned net position
   (greedy algorithm in `TransferCalculationUtils`), generates a todo list
   (TRANSFER items for the plan + PAYMENT items for manual expenses), applies
   the transfers to account balances, and writes AUTOMATIC balance-history
   entries. Recurring templates used in the budget get their last-used
   month/year stamped, which drives their next due date. Any savings line
   linked to a goal (item 070c) also earmarks that month's saving toward the
   goal (a `BUDGET_LOCK` allocation), inside the same lock transaction.
3. **Execute** — during the month the couple works through the todo list
   (optimistic checkboxes) and records manual balance corrections with date +
   comment as reality drifts. A manual balance change on a goal-backed account
   keeps earmarks truthful (item 070d): a decrease that over-allocates auto-reduces
   a single backing goal (or prompts for a split across several), and an increase
   optionally earmarks straight onto a goal — all inside the balance-update
   transaction, writing `BALANCE_REALLOCATION` history.
4. **Correct** — unlock fully reverses a lock: balances restored, todo list
   deleted, recurring-template stamps reverted, and goal earmarks made on lock
   removed. Only the most recent budget can be locked; one unlocked budget
   exists at a time (the working draft).

## System architecture

| | balance-frontend | balance-backend |
|---|---|---|
| Stack | React 19 + TypeScript + Vite, TanStack Query v5, React Hook Form + Zod v4, Tailwind v4 + shadcn/ui, PWA | Spring Boot 3.4.x, Java 17, PostgreSQL 15, JPA/Hibernate + Flyway, strict 3-layer architecture |
| Serving | nginx serves the SPA and proxies `/api/` → backend (`BACKEND_URL` env) | REST API at `/api`, Swagger UI at `/swagger-ui.html` |
| Tests | 50+ Vitest files (Testing Library + MSW): `npm run lint && npx tsc --noEmit && npm test -- --run && npm run build` | Testcontainers integration tests + unit tests: `./mvnw test` (needs Docker) |
| Image | `axelnyman/balance-frontend` | `axelnyman/balance-backend` |

- **No authentication, by design.** The API is open; deployment is LAN-only.
- **Delivery:** Conventional Commits → release-please maintains a release PR
  per repo → the maintainer merges it → GitHub Actions builds multi-arch
  (amd64 + arm64) images → Docker Hub → the Raspberry Pi runs them. Merging a
  feature PR alone does **not** deploy; the release-PR merge is the deploy
  gate. Separately, each open PR (when its tests pass) publishes a
  non-production preview image under a single rolling **`pr-latest`** tag via
  `docker-pr-preview.yml` — for trying a candidate build in a test environment
  before merge; each PR push overwrites the tag (no per-PR/per-SHA pile-up) and
  it never pushes `latest`/semver (items 020, 055).

## Domain model (backend)

- **BankAccount** — name (unique), description, currentBalance. Soft delete.
- **BalanceHistory** — per-account: balance after change, changeAmount,
  changeDate, optional comment, source `MANUAL|AUTOMATIC`, optional budgetId.
- **Budget** — month + year (unique among non-deleted), status
  `UNLOCKED|LOCKED`, lockedAt. Soft delete (unlocked budgets only).
- **BudgetIncome / BudgetExpense / BudgetSavings** — name, amount,
  bankAccount. Expense additionally: optional `recurringExpenseId` link,
  `isManual` flag, `deductedAt`. Savings additionally: optional
  `savingsGoalId` (item 070c) — links the line to a goal so locking earmarks
  that month's saving toward it and unlocking reverses it.
- **RecurringExpense** — template: name, amount, interval
  `MONTHLY|QUARTERLY|BIANNUALLY|YEARLY`, isManual, optional default
  bankAccount; lastUsedBudgetId/Month/Year stamped on lock (due = last used +
  interval, exposed as `dueMonth`/`dueYear`/`dueDisplay`). Soft delete.
- **TodoList** (1:1 with budget; created on lock, deleted on unlock) →
  **TodoItem** — `TRANSFER` (from → to account) or `PAYMENT` (from account),
  status `PENDING|COMPLETED`, completedAt.
- **SavingsGoal** (item 070a) — name, optional targetAmount, optional endDate,
  status `ACTIVE|ARCHIVED`, archivedAt. Soft delete. "Completed" is derived
  (allocated ≥ target), not stored.
- **GoalAllocation** — earmark of a bank account's `currentBalance` for a goal
  (`amount > 0`); at most one active row per `(goal, account)` — adjusted, not
  duplicated. No soft delete: removed (hard-deleted) when set to zero or on
  archive. Invariant: an account's total active allocations may never exceed
  its `currentBalance`. Allocations do **not** move money (except archive with
  `releaseToBalance=true`).
- **GoalAllocationChange** — append-only ledger (mirrors `BalanceHistory`):
  signed `changeAmount`, `resultingAmount`, `source`
  `MANUAL|BUDGET_LOCK|BALANCE_REALLOCATION|ARCHIVE`. Written on every
  allocation change; preserved across archiving and removal.

Money is `BigDecimal` / `NUMERIC(19,2)` everywhere. Flyway migrations V1–V6
(V4 dropped the deprecated `last_used_date` column; V5 added the savings-goals
tables; V6 added the nullable `budget_savings.savings_goal_id` FK).

## API surface (summary — details in Swagger)

- `/api/bank-accounts` — POST, GET (includes totalBalance); `/{id}` PUT,
  DELETE; `/{id}/balance` POST (manual update); `/{id}/balance-history` GET
  (paginated). `BankAccountResponse` now also carries `allocatedAmount` /
  `unallocatedAmount` (additive, item 070a). The balance POST accepts an optional
  signed `reallocation` list to reconcile goal earmarks (item 070d): a single-goal
  deficit auto-reduces; a multi-goal deficit without a split returns `409` with the
  conflict detail; an increase can earmark money straight onto goals. The response
  carries additive `allocationAdjustments`. Legacy request shape still works.
- `/api/savings-goals` (item 070a) — POST (optional seed allocations from
  accounts' unallocated money), GET (active goals with per-goal summary:
  totalAllocated, progress, backing accounts; archived excluded); `/{id}` GET
  (per-account breakdown), PUT (name/target/endDate); `/{id}/history` GET
  (allocation-change ledger, newest first, available for archived goals);
  `/{id}/allocations` POST (set an account's earmark; zero removes it);
  `/{id}/archive` POST (`releaseToBalance` boolean — frees allocations, and
  when true also reduces backing balances with `AUTOMATIC` balance-history).
- `/api/budgets` — POST, GET (with totals); `/{id}` GET, DELETE; `/{id}/lock`
  PUT; `/{id}/unlock` PUT; `/{budgetId}/income|expenses|savings` POST and
  `…/{itemId}` PUT, DELETE; `/{budgetId}/todo-list` GET;
  `/{budgetId}/todo-list/items/{id}` PUT. Savings create/update accept an
  optional `savingsGoalId` (item 070c; additive, defaults to null) echoed back
  on savings responses.
- `/api/recurring-expenses` — POST, GET; `/{id}` GET, PUT, DELETE.
- There is no `PUT /api/budgets/{id}` — month/year is not editable after
  creation (a possible future item; not yet specced).

## Frontend pages

- `/accounts` — totals header; account list; create/edit/delete via modals;
  update-balance modal (date + comment); balance-history drawer with infinite
  scroll. On a goal-backed account the update-balance modal reconciles earmarks
  (item 070d): an increase offers a pre-checked-by-default checkbox (single goal,
  fully allocated) or per-goal distribution inputs (multiple); a multi-goal deficit
  opens a split dialog; an automatic single-goal reduction is shown via a toast.
- `/recurring-expenses` — template list with due-status indicator; CRUD modals.
- `/budgets` — budget card grid (status + totals); "new budget" leads to the
  wizard (one unlocked budget at a time — enforced in UI and backend).
- `/budgets/new` — 5-step wizard; copy-from-last-budget; due templates grouped
  with "add all due"; non-due recurring templates are tucked behind a
  "Show other recurring expenses (N)" collapsible on the expenses step
  (item 040); create requires balance = 0. Recurring quick-add cards are a
  single compact row (name + amount + add) with the bank account deferred until
  the item is added — set from the template default and editable afterwards —
  and tighter padding at `md+` so more fit on desktop (item 050). The savings
  step also has an optional per-line **Goal** selector (item 070c), so a saving
  can be earmarked toward a goal during budget creation.
- `/budgets/:id` — income/expenses/savings sections with add/edit/delete
  modals (UNLOCKED only); summary with balance bar; lock/unlock/delete
  actions; link to the todo page when locked. The savings add/edit modal has an
  optional **Goal** selector (item 070c) and savings rows show the linked goal
  (`account · goal`). On UNLOCKED budgets a
  "due recurring expenses not added" hint above the expenses section lists
  recurring templates due for the budget month or earlier that aren't linked
  from any expense row, with one-click add (item 010).
- `/budgets/:id/todo` — progress bar; TRANSFER/PAYMENT items with optimistic
  checkbox toggling.
- `/goals` — card grid of active savings goals (name, allocated/target progress
  bar, backing accounts, completed badge); "new goal" opens a create modal with
  an optional initial allocation seeded from an account's unallocated money
  (item 070b).
- `/goals/:id` — goal summary + progress (allocated/target/remaining), per-account
  allocation breakdown, and edit / assign-money (capped at unallocated) / archive
  actions. A "Progress over time" card (item 070e) shows a dependency-free
  inline-SVG chart of allocated-over-time (reconstructed from the
  `GET /{id}/history` ledger), a velocity-based projected completion date (or a
  "not enough history yet" fallback), and — for goals with an end date — the
  required monthly contribution vs. current pace (ahead/behind). Forward-looking
  text is shown for ACTIVE goals only. Archive offers the `releaseToBalance`
  choice (free the earmark, or also spend it and reduce backing balances).
  Archived goals render read-only (item 070b).

## Conventions that matter

- **Conventional Commits** (`feat:`/`fix:`/`docs:`/`chore:`/`refactor:`/
  `test:`) for commits **and PR titles** (squash-merge makes the title the
  commit) — release-please derives versions from them.
- Modal-based editing only; explicit save everywhere except todo checkboxes
  (optimistic updates). Apple-inspired clean, light, mobile-first UI.
- The user-visible read queries (budget list/detail, accounts, recurring
  expenses, todo list) background-poll every 30s (`POLL_INTERVAL` in
  `src/lib/query-config.ts`) so two open sessions stay in near-real-time sync;
  polling pauses when the tab is hidden (item 060). Reads only — no push/SSE.
  The React Query default `staleTime` is **0** (item 070c follow-up), so every
  navigation/mount and window focus also refetches — cheap on a two-user LAN and
  it keeps views (e.g. goals after a budget lock) current between polls. Lock/
  unlock additionally invalidate the goals queries directly.
- Soft deletes everywhere; migrations are additive/backward compatible; never
  edit an applied migration; never hand-edit `CHANGELOG.md`.
- Engineering conventions live in each repo's `CLAUDE.md` (3-layer
  architecture and TDD on the backend; React Query + RHF/Zod patterns on the
  frontend).

## Known quirks & debt

- `balance-backend/README.md` now describes Balance accurately (item 005, done
  2026-06-14); the earlier template fiction (JWT/auth/user endpoints that do
  not exist) is gone. The backend `CLAUDE.md` was cleaned up the same way on
  2026-06-12.
- Backend package `org.example.axelnyman.main` and Maven artifact
  `spring-boot-rest-api-template` are template leftovers. Renaming is invasive
  and low-value; leave unless explicitly decided.
- Todo lists are deleted on unlock — there is no todo history. By design so far.
- `todo/backlog/sprint-5/` holds 10 unimplemented E2E-test-hardening stories
  from the original build-out; promote into `product/backlog/` if wanted.
- No rate limiting and no auth: fine on a LAN, never expose to the internet.

## Open backlog (as of 2026-06-30)

Specs live in `product/backlog/` (filename `NNN-slug.md`, lowest number =
highest priority); `done/` holds completed specs. This matches `README.md`,
both `CLAUDE.md` files, and `ROUTINE_PROMPT.md` (item 090 reconciled the
earlier flat-`product/` drift).

The savings-goals feature is complete (`070a`–`070e`, all **done**). Three new
specs were proposed on 2026-06-26 to refill the empty backlog (grounded in
documented debt, routine friction, and the sprint-5 E2E stories); the
maintainer prioritizes/approves them by merging the proposal PR:

| Item | What | Scope | Size |
|---|---|---|---|
| `080-edit-budget-month-year` | Edit an UNLOCKED budget's month/year in place (`PUT /api/budgets/{id}`) instead of delete-and-rebuild | full-stack | M |
| `100-transfer-algorithm-e2e-tests` | Promote sprint-5 Story 32: correctness E2E tests for the lock-time transfer algorithm | backend (tests) | M |

## Recently completed

(newest first; keep ≤ 20 rows, prune from the bottom — full history lives in
`product/done/`)

| Date | Item | Repos |
|---|---|---|
| 2026-06-30 | Reconcile backlog dir drift: specs moved into `product/backlog/` to match the docs (item 090) | backend (docs) |
| 2026-06-25 | Manual balance changes reconcile goal allocations: auto single-goal deficit, multi-goal split (409), increase earmark (item 070d) | backend, frontend |
| 2026-06-25 | Savings-goals progress: goal detail history chart, velocity projection & end-date required-contribution (item 070e) | frontend, backend (bookkeeping) |
| 2026-06-25 | Savings-goals budget linking: savings lines link to goals, earmark on lock / reverse on unlock (item 070c) | backend, frontend |
| 2026-06-24 | Savings-goals frontend pages: list, detail, create/edit/assign/archive (item 070b) | frontend, backend (bookkeeping) |
| 2026-06-24 | Savings-goals backend foundation: entities, allocation ledger + history, CRUD, archive (item 070a) | backend |
| 2026-06-23 | Near-real-time cross-device refresh via React Query polling (item 060) | frontend, backend (bookkeeping) |
| 2026-06-23 | Clean up CI/preview workflows + tighten agent prompts (item 055) | backend (CI+docs), frontend (CI) |
| 2026-06-23 | Tighten budget wizard quick-add card density (item 050) | frontend, backend (bookkeeping) |
| 2026-06-18 | Collapse non-due recurring expenses in the budget wizard (item 040) | frontend, backend (bookkeeping) |
| 2026-06-17 | Wizard item modal buttons clear the iPhone home indicator (item 030) | frontend, backend (bookkeeping) |
| 2026-06-17 | Per-PR `pr-<number>` Docker preview-image workflows (item 020) | backend (CI+docs), frontend (CI+docs) |
| 2026-06-17 | Scope six new-feature ideas into specs (item 015) | backend (docs) |
| 2026-06-15 | Due-recurring hint on budget detail page (item 010) | frontend, backend (bookkeeping) |
| 2026-06-14 | Rewrite backend README to describe Balance (item 005) | backend |
| 2026-06-12 | Product workflow set up (`product/` system, CLAUDE.md doc fixes) | backend, frontend |
