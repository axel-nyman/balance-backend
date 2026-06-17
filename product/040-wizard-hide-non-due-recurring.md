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
