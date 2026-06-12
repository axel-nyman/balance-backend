# Rewrite the backend README to describe Balance (remove template fiction)

- **ID:** 005-backend-readme-cleanup
- **Scope:** backend
- **Size:** S

## Why

`README.md` is still the original "Spring Boot REST API Template" README: it
documents JWT auth, user registration/login endpoints, and template features
that do not exist in this codebase, and it omits everything Balance actually
does. That misleads humans and — worse — misleads fresh AI agents on every
run.

This is deliberately a docs-only first item: it exercises the whole routine
pipeline (pick → implement → verify → bookkeeping → PR) with zero production
risk.

## What

Rewrite `README.md` so it accurately describes the Balance backend.

## Acceptance criteria

- [ ] No mention of JWT, auth, users, registration, or login anywhere in the README
- [ ] Describes: what Balance is (link to `product/STATE.md` for detail), the
      tech stack, local development (docker-compose.dev.yml +
      `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`), running
      tests, the Swagger UI pointer, and the delivery pipeline summary
      (release-please → multi-arch Docker Hub images)
- [ ] Points to `product/` for the product workflow and `CLAUDE.md` for
      engineering conventions
- [ ] `CHANGELOG.md` untouched (release-please owns it)
- [ ] Commit/PR type: `docs:`

## Out of scope

- Renaming the Maven artifact / Java package (known debt, separate decision)
- Any code changes
