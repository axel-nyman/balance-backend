# Build & push "unstable" / per-PR Docker images for a test deployment

- **ID:** 020-unstable-prerelease-images
- **Scope:** full-stack (CI/infra — both repos)
- **Size:** M (about a day)

> **Prioritised first** (maintainer request, item 015 review): this unlocks a
> test/staging deployment that makes verifying every *later* feature easier, so
> it should land before the other backlog items.

## Why

The maintainer wants a staging/testing deployment to try new features before
cutting a real release to the Raspberry Pi production. Today images are only
built when release-please's release PR is merged (a deliberate, infrequent
deploy gate). Two things make a separate test environment possible without
polluting the versioned `latest` line that production pulls:

- an always-fresh **`unstable`** image per repo, published on every merge to
  `main`; and
- a **per-pull-request** image, published when a PR is opened/updated, so a
  feature can be deployed and tried **before** deciding whether to merge it.

## What

Add a CI workflow to **each** repo that builds and pushes a multi-arch Docker
image (production's versioned/`latest` tags stay exclusively release-please's),
on two triggers:

- **`push` to `main`** → tag `unstable` (the rolling "latest merged" test image).
- **`pull_request`** → tag the image per PR so it can be pulled and deployed to
  the test environment before merge. Use a branch-name-agnostic tag derived from
  the PR number — e.g. `pr-<number>` (from `github.event.pull_request.number`) —
  so it works for any future branch without knowing branch names in advance.
  Optionally also a short commit-SHA tag for traceability.

It must:

- **Only publish if tests pass** — gate the image build on the repo's existing
  test/CI job (backend `./mvnw clean test`; frontend lint + tsc + test + build),
  for both the `main` and `pull_request` paths.
- **Skip docs-only changes** — don't rebuild when the change only touches docs
  (`**.md`, `.claude/**`, `product/**`), mirroring the frontend CI's existing
  `paths-ignore`.
- **Not collide with release-please** — never push semver or `latest` tags from
  this workflow; only `unstable` and `pr-<number>` (plus an optional SHA tag).

## Acceptance criteria

- [ ] On `push` to `main` with non-docs changes, each repo runs its tests and —
      only if they pass — builds and pushes a multi-arch
      (`linux/amd64,linux/arm64`) image tagged `unstable`
- [ ] On `pull_request` (opened/synchronize/reopened) with non-docs changes,
      each repo runs its tests and — only if they pass — builds and pushes a
      multi-arch image tagged `pr-<number>` (PR-number based, branch-name
      agnostic), so the PR's build can be deployed to the test environment
      before merge
- [ ] Docs-only changes (`**.md`, `.claude/**`, `product/**`) do **not** trigger
      an image build on either trigger
- [ ] Failing tests block the image push on both triggers (no image on red)
- [ ] The workflow never pushes `latest` or any semver tag — those remain
      release-please's (`release.yml` unchanged in behaviour)
- [ ] Reuses existing Docker Hub auth secrets (backend `DOCKER_USERNAME` /
      `DOCKER_PASSWORD`; frontend `DOCKER_USERNAME` / `DOCKER_TOKEN`) and the
      existing Dockerfiles unchanged
- [ ] Image names match production (`axelnyman/balance-backend`,
      `axelnyman/balance-frontend`) with the `unstable` / `pr-<number>` tags
- [ ] A short note in each repo's README (or product docs) explains the
      `unstable` and `pr-<number>` tags and that they are not for production

## Implementation notes

- **This item explicitly authorizes adding `.github/workflows/` files** (the
  routine normally forbids touching CI). Do **not** modify `release.yml`,
  `release-please-config.json`, the manifests, or the Dockerfiles.
- Recommended shape: a new `docker-unstable.yml` per repo with
  `on: { push: { branches: [main] }, pull_request: {} }` and `paths-ignore`,
  containing a test job and a build-push job that `needs:` it. Mirror the
  existing release Docker steps (QEMU + Buildx + `docker/login-action` +
  `docker/metadata-action` + `docker/build-push-action`, `cache-from/to:
  type=gha`). Drive the tag from `docker/metadata-action` tag rules:
  `type=raw,value=unstable,enable={{is_default_branch}}` for the main push and
  `type=ref,event=pr` (yields `pr-<number>`) for the PR trigger. Alternatively
  use `workflow_run` gated on the existing test/ci workflow concluding
  `success` — pick one and explain why in the PR.
- **PR builds from forks:** `pull_request` runs from a fork have no access to
  secrets, so the push step would fail. This repo's PRs all come from
  same-repo branches (the routine's `claude/*` branches), so the simple
  `pull_request` trigger is fine; just guard the login/push steps to no-op when
  secrets are absent (or note the same-repo assumption in the PR) rather than
  reaching for `pull_request_target`, which has security caveats.
- **Cleanup:** `pr-<number>` tags accumulate on Docker Hub. Out of scope to
  automate deletion here; note it as a possible follow-up (a small job on
  `pull_request: closed` could remove the tag).
- Backend currently has no `paths-ignore` on its workflows; this new workflow
  should add one so doc/`product/` pushes don't build images.
- Note the existing secret-name skew between repos (`DOCKER_PASSWORD` vs
  `DOCKER_TOKEN`) and use each repo's existing name; don't rename secrets.

## Out of scope

- Standing up the actual test/staging deployment (compose/host config) — this
  item only produces the `unstable` / `pr-<number>` images.
- Changing the release/versioned pipeline or the `latest` tag semantics.
- Auto-deploying the images anywhere, and automatic cleanup of old `pr-<number>`
  tags (note as follow-up).

## Notes

- Two separate PRs (one per repo), since each repo owns its own workflows. Cross
  link them. There is no backend/frontend ordering dependency here.
- CI changes can't be fully verified locally; rely on the workflow running on
  the PR / on `main` after merge, and say so in the PR. Use `act` or a syntax
  check if available, but don't claim a green run that didn't happen.
