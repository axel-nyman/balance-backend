# Tuck away "not due this month" recurring expenses in the wizard

- **ID:** 040-wizard-hide-non-due-recurring
- **Scope:** frontend
- **Size:** S (≤ half a day)

## Why

In the budget wizard's expenses step, every recurring-expense template that
isn't due for the chosen month is still listed in full under "Other recurring".
For a couple with many templates this pushes the genuinely relevant "Due this
month" group down and clutters the step. The due items are what matter when
planning a month; the rest are an occasional "I want to add it early" case.

## What

On the expenses step, keep the **"Due this month"** group expanded and
prominent as today, but move the non-due ("Other recurring") templates into a
collapsed container that the user can expand on demand (e.g. a
"Show other recurring expenses (N)" toggle that reveals the existing list, or a
"Add other recurring…" affordance). When collapsed, only the due group and the
toggle are visible. Expanding shows the same quick-add cards that exist today.

## Acceptance criteria

- [ ] Templates due for the budget's month/year render expanded as they do now
      ("Due this month" group with "Add All")
- [ ] Non-due templates are hidden behind a collapsed control by default and do
      not occupy vertical space until expanded
- [ ] The collapsed control shows the count of hidden non-due templates
- [ ] Expanding reveals the existing quick-add cards (same add behaviour,
      animations, and "added" feedback as today) and collapsing hides them
- [ ] When there are zero non-due templates, the toggle is not rendered
- [ ] When there are zero due templates, the step still reads sensibly (e.g.
      the toggle becomes the only recurring control)
- [ ] Component tests cover: due-only, non-due-only, both, and neither; plus the
      expand/collapse toggle and that adding from the expanded list still works

## UI notes

- File: `src/components/wizard/steps/StepExpenses.tsx`. The split already exists
  (`dueExpenses` vs `otherExpenses`, ~lines 88–93) and both render quick-add
  cards (`WizardItemCard variant="quick-add"`).
- Use the existing shadcn `Collapsible`/`CollapsibleContent` already used in the
  wizard rather than a new modal, to stay consistent and keep the add flow
  inline. (A modal is an acceptable alternative if it reads better — record the
  choice in the PR.)
- Preserve the existing `CollapseWrapper` animations on the quick-add cards.

## Out of scope

- Changing how "due" is computed (still `dueMonth/dueYear === budget month/year`
  — overdue handling is item 010's concern, already shipped on the detail page).
- Any backend change (all template data already comes from
  `GET /api/recurring-expenses`).
- The desktop card-density change (that's item 050).

## Notes

- Pairs naturally with item 050 (wizard density) but is independently shippable.

## Completion notes

- **Date:** 2026-06-18
- **PRs:** frontend axel-nyman/balance-frontend (branch
  `claude/peaceful-hamilton-tx79tf`); backend bookkeeping (this branch
  `claude/youthful-hamilton-tx79tf`). Frontend-only feature — no merge-order
  dependency.
- **Implementation:** In `StepExpenses.tsx` the non-due ("Other recurring")
  group is now wrapped in the existing shadcn `Collapsible`, collapsed by
  default. The trigger is a ghost button reading
  `Show other recurring expenses (N)` when collapsed (N = number of hidden
  non-due templates) and `Hide other recurring expenses` when expanded, with the
  same chevron-rotate affordance used by the review step. The "Due this month"
  group is unchanged (expanded, "Add All"). Expanding renders the exact same
  `WizardItemCard variant="quick-add"` cards wrapped in `CollapseWrapper`, so add
  behaviour, copy animations, and "added" feedback are preserved.
- **Interpretation decisions:**
  - Chose the inline `Collapsible` (the spec's preferred option) over a modal.
  - The toggle replaces the former static `Other recurring` /
    `Quick add from recurring` heading; when there are no due templates the
    toggle is the only recurring control and the step still reads sensibly (AC).
  - Collapsed by default in all cases, including the no-due-templates case (the
    spec does not ask for auto-expand there).
  - Radix `CollapsibleContent` unmounts when closed, so non-due cards occupy no
    vertical space until expanded (satisfies the "do not occupy vertical space"
    AC) and are absent from the DOM/tests until the toggle is opened.
- **Tests:** Updated the existing
  `shows quick-add section when recurring expenses exist` test (non-due item is
  now behind the toggle) and added a `non-due recurring collapse` describe block
  covering due-only, non-due-only, both, and neither, plus expand/collapse and
  adding from the expanded list. Full frontend suite green: `npm run lint`
  (0 errors, 7 pre-existing warnings), `npx tsc --noEmit` clean,
  `npm test -- --run` 503 passed (54 files), `npm run build` succeeds.
- **Deviations / cut:** none. No backend change (template data already comes
  from `GET /api/recurring-expenses`).
