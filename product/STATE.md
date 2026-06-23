# Balance — State of the App

> **Living document.** This is the first thing a fresh agent (or human) should
> read. Every completed backlog item MUST update this file (see
> `product/README.md`). Deeper history: `product/done/`, each repo's
> `CHANGELOG.md` (generated — never hand-edit), and `.claude/thoughts/` in
> both repos for engineering research and plans.

**Last updated:** 2026-06-23 (item 055 — clean up CI and agentic workflows)

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
   month/year stamped, which drives their next due date.
3. **Execute** — during the month the couple works through the todo list
   (optimistic checkboxes) and records manual balance corrections with date +
   comment as reality drifts.
4. **Correct** — unlock fully reverses a lock: balances restored, todo list
   deleted, recurring-template stamps reverted. Only the most recent budget
   can be locked; one unlocked budget exists at a time (the working draft).

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
  `isManual` flag, `deductedAt`.
- **RecurringExpense** — template: name, amount, interval
  `MONTHLY|QUARTERLY|BIANNUALLY|YEARLY`, isManual, optional default
  bankAccount; lastUsedBudgetId/Month/Year stamped on lock (due = last used +
  interval, exposed as `dueMonth`/`dueYear`/`dueDisplay`). Soft delete.
- **TodoList** (1:1 with budget; created on lock, deleted on unlock) →
  **TodoItem** — `TRANSFER` (from → to account) or `PAYMENT` (from account),
  status `PENDING|COMPLETED`, completedAt.

Money is `BigDecimal` / `NUMERIC(19,2)` everywhere. Flyway migrations V1–V4
(V4 dropped the deprecated `last_used_date` column).

## API surface (summary — details in Swagger)

- `/api/bank-accounts` — POST, GET (includes totalBalance); `/{id}` PUT,
  DELETE; `/{id}/balance` POST (manual update); `/{id}/balance-history` GET
  (paginated).
- `/api/budgets` — POST, GET (with totals); `/{id}` GET, DELETE; `/{id}/lock`
  PUT; `/{id}/unlock` PUT; `/{budgetId}/income|expenses|savings` POST and
  `…/{itemId}` PUT, DELETE; `/{budgetId}/todo-list` GET;
  `/{budgetId}/todo-list/items/{id}` PUT.
- `/api/recurring-expenses` — POST, GET; `/{id}` GET, PUT, DELETE.
- There is no `PUT /api/budgets/{id}` — month/year is not editable after
  creation (a possible future item; not yet specced).

## Frontend pages

- `/accounts` — totals header; account list; create/edit/delete via modals;
  update-balance modal (date + comment); balance-history drawer with infinite
  scroll.
- `/recurring-expenses` — template list with due-status indicator; CRUD modals.
- `/budgets` — budget card grid (status + totals); "new budget" leads to the
  wizard (one unlocked budget at a time — enforced in UI and backend).
- `/budgets/new` — 5-step wizard; copy-from-last-budget; due templates grouped
  with "add all due"; non-due recurring templates are tucked behind a
  "Show other recurring expenses (N)" collapsible on the expenses step
  (item 040); create requires balance = 0. Recurring quick-add cards are a
  single compact row (name + amount + add) with the bank account deferred until
  the item is added — set from the template default and editable afterwards —
  and tighter padding at `md+` so more fit on desktop (item 050).
- `/budgets/:id` — income/expenses/savings sections with add/edit/delete
  modals (UNLOCKED only); summary with balance bar; lock/unlock/delete
  actions; link to the todo page when locked. On UNLOCKED budgets a
  "due recurring expenses not added" hint above the expenses section lists
  recurring templates due for the budget month or earlier that aren't linked
  from any expense row, with one-click add (item 010).
- `/budgets/:id/todo` — progress bar; TRANSFER/PAYMENT items with optimistic
  checkbox toggling.

## Conventions that matter

- **Conventional Commits** (`feat:`/`fix:`/`docs:`/`chore:`/`refactor:`/
  `test:`) for commits **and PR titles** (squash-merge makes the title the
  commit) — release-please derives versions from them.
- Modal-based editing only; explicit save everywhere except todo checkboxes
  (optimistic updates). Apple-inspired clean, light, mobile-first UI.
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
- **Doc drift:** backlog specs live **flat in `product/`** (e.g.
  `product/020-….md`), not in a `product/backlog/` subdirectory. `README.md`,
  both `CLAUDE.md` files, and `ROUTINE_PROMPT.md` still say `product/backlog/`;
  only `done/` is a real subdir. Treat "the backlog" as the `NNN-*.md` files in
  `product/` until the docs are reconciled.

## Open backlog (as of 2026-06-23)

Specs live directly in `product/` (filename `NNN-slug.md`, lowest number =
highest priority). Item 015 scoped six raw feature ideas into these; priority
order reflects the maintainer's item 015 review (PR preview image first):

- `060` near-real-time refresh to sync two open sessions (frontend, S)
- `070a–070e` **savings goals** (split: backend foundation → goals pages →
  budget linking on lock → manual-balance reallocation → progress/predictions).
  Sizeable new domain — `070a` is the gate. `070a` now includes an append-only
  allocation-history ledger and an archive option that can release allocations
  back to account balances; `070e` adds progress visualizations (charts are in
  scope — see the non-goals note above).

## Recently completed

(newest first; keep ≤ 20 rows, prune from the bottom — full history lives in
`product/done/`)

| Date | Item | Repos |
|---|---|---|
| 2026-06-23 | Clean up CI/preview workflows + tighten agent prompts (item 055) | backend (CI+docs), frontend (CI) |
| 2026-06-23 | Tighten budget wizard quick-add card density (item 050) | frontend, backend (bookkeeping) |
| 2026-06-18 | Collapse non-due recurring expenses in the budget wizard (item 040) | frontend, backend (bookkeeping) |
| 2026-06-17 | Wizard item modal buttons clear the iPhone home indicator (item 030) | frontend, backend (bookkeeping) |
| 2026-06-17 | Per-PR `pr-<number>` Docker preview-image workflows (item 020) | backend (CI+docs), frontend (CI+docs) |
| 2026-06-17 | Scope six new-feature ideas into specs (item 015) | backend (docs) |
| 2026-06-15 | Due-recurring hint on budget detail page (item 010) | frontend, backend (bookkeeping) |
| 2026-06-14 | Rewrite backend README to describe Balance (item 005) | backend |
| 2026-06-12 | Product workflow set up (`product/` system, CLAUDE.md doc fixes) | backend, frontend |
