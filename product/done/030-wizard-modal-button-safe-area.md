# Give the wizard item modal's buttons room above the iPhone home indicator

- **ID:** 030-wizard-modal-button-safe-area
- **Scope:** frontend
- **Size:** S (≤ half a day)

## Why

When adding or editing a line item in the budget creation wizard on a modern
iPhone (e.g. iPhone 17 Pro), the bottom-sheet modal's action buttons ("Done"
and "Delete") sit so close to the bottom edge that the rounded screen corners
and the home indicator clip them. Tapping a partially hidden destructive
button is exactly the kind of fiddly, error-prone interaction the app should
avoid during the monthly money routine.

## What

Add bottom safe-area breathing room to the wizard item modal so its action
buttons are fully visible and comfortably tappable on notched/rounded phones,
without changing the desktop appearance.

`WizardItemEditModal` renders a shadcn `Sheet` with `side="bottom"` and a
`SheetFooter` overridden to `flex-col gap-2 px-0 pb-2`. The footer has no
`env(safe-area-inset-bottom)` handling, so on iOS the buttons render under the
home indicator. The fix is to pad the sheet's bottom by the safe-area inset
(the codebase already uses `@supports (padding-top: env(safe-area-inset-top))`
blocks in `src/index.css` for the header/sidebar — reuse that mechanism rather
than inventing a new one).

## Acceptance criteria

- [ ] The wizard item modal ("Done"/"Delete" buttons) keeps a gap below the
      buttons at least as large as `env(safe-area-inset-bottom)` on devices
      that report one
- [ ] On devices/browsers without a safe-area inset (desktop), the modal looks
      unchanged from today (the existing `pb-2` spacing is preserved as the
      floor)
- [ ] Applies to all three uses of `WizardItemEditModal` (income, expenses,
      savings) since they share the component
- [ ] No layout regression to the form fields above the footer
- [ ] A component/snapshot test (or existing modal test) still passes; if
      feasible, assert the safe-area padding class/utility is present on the
      footer or sheet content

## UI notes

- Component: `src/components/wizard/WizardItemEditModal.tsx` (`SheetFooter`
  around lines 213–225).
- Shared primitive: `src/components/ui/sheet.tsx` (the bottom `SheetContent`).
  Decide whether the inset belongs on the shared `SheetContent` (affects every
  bottom sheet) or only on the wizard modal's footer — prefer the **narrowest**
  change that fixes the reported screen unless other bottom sheets share the
  bug, and record that decision in the PR.
- Reuse the existing safe-area approach in `src/index.css` (the
  `mobile-content` / `mobile-sidebar` `env(safe-area-inset-bottom)` blocks).
  Tailwind v4 also allows `pb-[env(safe-area-inset-bottom)]`-style arbitrary
  values; either is fine if consistent with the codebase.

## Out of scope

- Restyling the modal beyond the bottom spacing fix.
- Touching non-wizard modals/sheets unless they demonstrably share the bug
  (note them in the PR if so, but don't expand scope without confirmation).

## Notes

- No backend changes. No new dependencies.
- Hard to verify pixel-perfectly without the physical device; the implementer
  should reason from the CSS and note in the PR that on-device confirmation is
  the maintainer's to do.

## Completion notes

- **Date:** 2026-06-17
- **PRs:** balance-frontend (this item's implementation) + balance-backend (this
  bookkeeping). Cross-linked; no merge-order dependency (frontend-only change).
- **Interpretation:** Replaced the wizard item modal footer's fixed `pb-2` with
  `pb-[max(0.5rem,env(safe-area-inset-bottom))]`. The `max()` keeps the existing
  0.5rem floor on devices that report no safe-area inset (desktop, unchanged),
  and grows the bottom padding to the inset on notched/rounded phones so the
  "Done"/"Delete" buttons clear the home indicator.
- **Scope decision:** Made the change on `WizardItemEditModal`'s footer only, not
  the shared `SheetContent` primitive. `side="bottom"` is used in exactly one
  place in the codebase (the wizard item modal), so the narrowest fix fully
  covers the reported bug; no other bottom sheet shares it. All three uses
  (income/expenses/savings) share this component, so all are covered.
- **Tests:** Added `WizardItemEditModal.test.tsx` — asserts the action buttons
  render and that the footer carries the safe-area padding utility. Full
  frontend suite green: lint (0 errors), `tsc --noEmit` clean, 499 tests across
  55 files pass, `npm run build` succeeds.
- **Not verified:** On-device pixel confirmation on a physical iPhone is the
  maintainer's to do (per the spec's note); the change is reasoned from the CSS.
- **Deviations / cut:** None.
