# Previous/next budget navigation on the budget detail page

- **ID:** 020-budget-prev-next-navigation
- **Scope:** frontend
- **Size:** S

## Why

Comparing this month against last month currently means: detail page → back to
`/budgets` → find the card → click. A one-tap hop between adjacent months
makes the most common browsing pattern ("what did we plan last month?")
frictionless — especially on mobile.

## What

On `/budgets/:id`, add previous/next controls (e.g. chevrons beside the
month title) that navigate to the chronologically adjacent *existing* budget,
ordered by (year, month). Hidden or disabled at the ends.

## Acceptance criteria

- [ ] Chevrons navigate to the adjacent budget using the existing budgets list
      query (`useBudgets()`) — no new API calls
- [ ] First/last budget handled gracefully (control disabled or hidden)
- [ ] Works on mobile; keyboard accessible with aria-labels
- [ ] Component tests cover ordering and the end cases

## Out of scope

- Swipe gestures
- Creating missing months from the detail page
