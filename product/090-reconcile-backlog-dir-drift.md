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
