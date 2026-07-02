# Reconcile the `product/backlog/` directory doc drift

- **ID:** 090-reconcile-backlog-dir-drift
- **Scope:** backend (docs only)
- **Size:** S (≤ half a day)

## Why

`README.md`, both repos' `CLAUDE.md`, and `ROUTINE_PROMPT.md` all say backlog
specs live in `product/backlog/`, but in reality they sit **flat** in
`product/` (only `done/` is a real subdirectory). STATE.md documents this as
known drift and tells readers to "treat the backlog as the `NNN-*.md` files in
`product/`." Every fresh agent has to discover this mismatch by running `ls`
and finding `backlog/` does not exist (this run did). Making docs and reality
agree removes a recurring papercut from the one workflow every routine depends
on.

## What

Pick one source of truth and make everything match it. **Recommended:** create
the `product/backlog/` subdirectory and move the open `NNN-*.md` specs into it,
because four separate docs already describe that layout — moving the files is
less churn than rewriting all of them, and it matches the system's original
design (`done/` is already a subdir). Whichever direction is chosen, after this
item there must be **zero** remaining references to a layout that doesn't exist.

## Acceptance criteria

- [ ] After the change, the documented backlog location and the actual on-disk
      location agree — verified by following each doc literally
- [ ] `STATE.md`'s "Doc drift" bullet under "Known quirks & debt" is removed (or
      rewritten to reflect the resolved state); the "Open backlog" section's
      path description matches reality
- [ ] `README.md`, `balance-backend/CLAUDE.md`, `balance-frontend/CLAUDE.md`,
      and `ROUTINE_PROMPT.md` all reference the same, correct path
- [ ] If the subdir approach is taken: the `NNN-*.md` specs (including the new
      080/100 from this batch) are `git mv`d into `product/backlog/`, and the
      "lowest-numbered spec in `product/backlog/`" pick rule now works literally
- [ ] No spec **content** is changed and no code changes — this is a docs/layout
      reconciliation only

## Out of scope

- Renaming or renumbering any spec
- Touching `done/`, `todo/`, or `TEMPLATE.md` content
- Any application code or backlog **prioritization** changes

## Notes

- Cross-repo: the frontend `CLAUDE.md` "Product Workflow" section points at
  these same paths, so the frontend gets a small sibling `docs:` change too
  (one PR per repo, per the routine).
- Touch only docs and the spec file locations; do not alter
  `.github/workflows/` or any build config.
- Whoever implements this should also confirm `ROUTINE_PROMPT.md`'s step 3 and
  the "Empty backlog" section read correctly against the chosen layout.

## Completion notes

- **Date:** 2026-06-30
- **PRs:** balance-backend (this PR) — backend docs only; **no frontend PR**
  (see "Frontend" below).
- **Approach chosen: subdirectory (the spec's recommendation).** Created
  `product/backlog/` and `git mv`d the open specs into it, rather than
  rewriting four docs to describe the flat layout. This was deliberately the
  lower-churn direction: `README.md`, both `CLAUDE.md` files, and
  `ROUTINE_PROMPT.md` already referenced `product/backlog/`, so moving the
  files made those references correct instead of editing them.
- **What moved:**
  - `product/080-edit-budget-month-year.md` → `product/backlog/`
  - `product/100-transfer-algorithm-e2e-tests.md` → `product/backlog/`
  - `product/090-…` (this spec) → `product/done/` (normal bookkeeping move)
- **What changed in docs:** only `STATE.md` — it was the sole doc describing the
  *flat* reality. Removed the "Doc drift" bullet under "Known quirks & debt",
  rewrote the "Open backlog" section's path description to `product/backlog/`,
  dropped 090 from the open-backlog table, and added the recently-completed row.
  No spec **content** was changed; no application code touched.
- **Docs confirmed correct against the new layout (no edit needed):**
  `README.md` (layout table + lifecycle), `balance-backend/CLAUDE.md`,
  `balance-frontend/CLAUDE.md`, and `ROUTINE_PROMPT.md` — including step 3's
  "lowest-numbered spec in `product/backlog/`" pick rule and the "Empty backlog"
  instruction to write specs into `product/backlog/`, both of which now work
  literally.
- **Frontend:** the spec's Notes anticipated a sibling frontend `docs:` change,
  but `balance-frontend/CLAUDE.md` already references `product/backlog/`, which
  the subdir move makes correct. There was no honest change to make there, so —
  per the minimal-diff guardrail — no frontend PR was opened. (The
  `2025-12-18-planning-files-inconsistencies.md` research note in
  balance-frontend is dated historical research and was intentionally left
  untouched.)
- **Overlap with in-flight item 080 (PRs balance-backend#63 / balance-frontend#28):**
  those PRs (a different routine run, `*-rtettl` branches) also move the 080
  spec (to `done/`) and edit `STATE.md`. Whichever of #63 and this PR merges
  second will hit a trivial conflict limited to (a) the 080 spec file's final
  location — it belongs in `product/done/` once 080 lands — and (b) `STATE.md`'s
  open-backlog / recently-completed tables. No code is involved either way.
