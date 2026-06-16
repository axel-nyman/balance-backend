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

## Out of scope

Don't implement anything.

## Notes

Split anything too large into several smaller features if needed.
