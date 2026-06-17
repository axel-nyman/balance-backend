# Tighten the budget wizard's use of screen space

- **ID:** 050-wizard-density
- **Scope:** frontend
- **Size:** M (about a day)

## Why

The budget creation wizard is the most-used flow each month and currently
spends a lot of vertical (mobile) and horizontal (desktop) space on detail
that only matters once the user has decided to add an item. Showing the
account and amount on every quick-add recurring card before it's been added is
noise; on desktop the quick-add cards are also larger than they need to be.
Tightening this makes the whole "plan a month" flow faster to scan.

## What

Two related density improvements to the wizard, both focused on the recurring
quick-add cards:

1. **Defer non-essential detail until add.** On a quick-add recurring card,
   de-emphasise or hide secondary info (account, and optionally amount) until
   the item is actually being added — the relevant decision at scan time is
   "do I want this expense this month?", not which account it lands in. The
   account/amount remain editable in the item modal after adding (or via the
   card's expanded state).
2. **Downsize quick-add cards on desktop.** Make the quick-add cards visually
   more compact at `md`+ breakpoints (reduced padding, tighter rows, possibly a
   multi-column grid) so more fit on screen, while keeping the comfortable
   single-column mobile layout.

## Acceptance criteria

- [ ] Quick-add recurring cards show less secondary detail at scan time than
      today (at minimum the account is de-emphasised/hidden until add); the
      item's name and the add control remain clearly visible
- [ ] After adding, the account and amount are still set correctly (default
      account if the template has one; the modal still lets the user choose/edit)
- [ ] On `md`+ screens the quick-add cards are visibly more compact than today
      (smaller padding and/or a grid), with no loss of tap-target size on mobile
- [ ] Mobile single-column layout and existing animations remain intact
- [ ] "Add All" for due items still works and produces the same expense rows
- [ ] Component tests cover the add flow still producing correct expense data
      and the responsive layout switch (e.g. grid vs stack) where testable

## UI notes

- Files: `src/components/wizard/WizardItemCard.tsx` (the `quick-add` variant,
  ~lines 36–102) and `src/components/wizard/steps/StepExpenses.tsx` (layout and
  the `space-y-*`/grid wrappers).
- Current quick-add card wrapper: `w-full bg-popover rounded-xl p-4` with a
  single flex row. Introduce responsive classes (`md:p-2`, `md:grid`,
  `md:grid-cols-2`, etc.) rather than a separate component.
- Keep using `WizardItemCard` for both due and non-due lists so item 040's
  collapse still composes cleanly.
- sv-SE currency formatting via `formatCurrency*` in `src/lib/utils.ts` stays.

## Out of scope

- The collapse-non-due behaviour (item 040).
- Restructuring income/savings steps — this item is scoped to the expenses
  step's recurring quick-add cards. If the same card component is reused by
  income/savings copy-from-last, keep their appearance acceptable but don't
  redesign them here.
- Any backend change.

## Notes

- Could be split into 050a (defer detail) and 050b (desktop downsize) if it
  runs long; they share the same files so a single PR is preferred.
- Coordinate with item 040 if both are in flight (same file).
