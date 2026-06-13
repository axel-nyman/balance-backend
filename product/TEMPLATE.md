# <Feature name — short, user-visible>

- **ID:** NNN-kebab-slug (must equal the filename without `.md`)
- **Scope:** backend | frontend | full-stack
- **Size:** S (≤ half a day) | M (about a day) — anything bigger gets split

## Why

One to three sentences of user value, in terms of the couple's monthly
routine. If you can't phrase the value, the item probably isn't worth doing.

## What

The intended behavior, stated decisively. Rough is OK — the implementer reads
the surrounding code, picks the simplest interpretation consistent with the
existing UX, and records that interpretation in the PR.

## Acceptance criteria

- [ ] Concrete, testable statements — each should map to at least one test

## API changes (if backend)

Endpoint/DTO sketch. Breaking changes are almost never acceptable: the
currently deployed frontend must keep working against the new backend.

## UI notes (if frontend)

Where it lives; which existing patterns and components to reuse
(modals + React Hook Form + Zod, shadcn/ui, sv-SE formatting).

## Out of scope

Explicit cuts that keep the item S/M-sized.

## Notes

File pointers, risks, related `.claude/thoughts/` docs.
