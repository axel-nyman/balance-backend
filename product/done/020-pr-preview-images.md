# Build & push a per-PR Docker image for a test deployment

- **ID:** 020-pr-preview-images
- **Scope:** full-stack (CI/infra — both repos)
- **Size:** M (about a day)

> **Prioritised first** (maintainer request, item 015 review): this unlocks a
> test/staging deployment that makes verifying every *later* feature easier, so
> it should land before the other backlog items.

## Why

The maintainer wants a staging/testing deployment to try a feature **before**
deciding whether to merge it to production on the Raspberry Pi. Today images are
only built when release-please's release PR is merged (a deliberate, infrequent
deploy gate), so there is no way to pull and run a candidate build while it is
still under review.

A **per-pull-request** image solves exactly that: published when a PR is
opened/updated, it lets the maintainer deploy the PR's build to the test
environment and try it before merging — without ever touching the versioned
`latest` line that production pulls.

> Scope decision (item 015 review): an earlier draft of this spec also proposed
> a rolling `unstable` image on every merge to `main`. The maintainer asked to
> drop that and keep **only** the per-PR image — it's the one that will
> realistically be used. This spec is therefore PR-image-only.

## What

Add a CI workflow to **each** repo that builds and pushes a multi-arch Docker
image on the **`pull_request`** trigger (production's versioned/`latest` tags
stay exclusively release-please's):

- **`pull_request`** (opened/synchronize/reopened) → tag the image per PR so it
  can be pulled and deployed to the test environment before merge. Use a
  branch-name-agnostic tag derived from the PR number — e.g. `pr-<number>` (from
  `github.event.pull_request.number`) — so it works for any future branch
  without knowing branch names in advance. Optionally also a short commit-SHA
  tag for traceability.

It must:

- **Only publish if tests pass** — gate the image build on the repo's existing
  test/CI job (backend `./mvnw clean test`; frontend lint + tsc + test + build).
- **Skip docs-only changes** — don't rebuild when the change only touches docs
  (`**.md`, `.claude/**`, `product/**`), mirroring the frontend CI's existing
  `paths-ignore`.
- **Not collide with release-please** — never push semver or `latest` tags from
  this workflow; only `pr-<number>` (plus an optional SHA tag).

## Acceptance criteria

- [ ] On `pull_request` (opened/synchronize/reopened) with non-docs changes,
      each repo runs its tests and — only if they pass — builds and pushes a
      multi-arch (`linux/amd64,linux/arm64`) image tagged `pr-<number>`
      (PR-number based, branch-name agnostic), so the PR's build can be deployed
      to the test environment before merge
- [ ] Docs-only changes (`**.md`, `.claude/**`, `product/**`) do **not** trigger
      an image build
- [ ] Failing tests block the image push (no image on red)
- [ ] The workflow never pushes `latest` or any semver tag — those remain
      release-please's (`release.yml` unchanged in behaviour)
- [ ] Reuses existing Docker Hub auth secrets (backend `DOCKER_USERNAME` /
      `DOCKER_PASSWORD`; frontend `DOCKER_USERNAME` / `DOCKER_TOKEN`) and the
      existing Dockerfiles unchanged
- [ ] Image names match production (`axelnyman/balance-backend`,
      `axelnyman/balance-frontend`) with the `pr-<number>` tag
- [ ] A short note in each repo's README (or product docs) explains the
      `pr-<number>` tags and that they are not for production

## Implementation notes

- **This item explicitly authorizes adding `.github/workflows/` files** (the
  routine normally forbids touching CI). Do **not** modify `release.yml`,
  `release-please-config.json`, the manifests, or the Dockerfiles.
- Recommended shape: a new `docker-pr-preview.yml` per repo with
  `on: { pull_request: { types: [opened, synchronize, reopened] } }` and
  `paths-ignore`, containing a test job and a build-push job that `needs:` it.
  Mirror the existing release Docker steps (QEMU + Buildx +
  `docker/login-action` + `docker/metadata-action` + `docker/build-push-action`,
  `cache-from/to: type=gha`). Drive the tag from `docker/metadata-action` tag
  rules: `type=ref,event=pr` (yields `pr-<number>`). Alternatively use
  `workflow_run` gated on the existing test/ci workflow concluding `success` —
  pick one and explain why in the PR.
- **PR builds from forks:** `pull_request` runs from a fork have no access to
  secrets, so the push step would fail. This repo's PRs all come from same-repo
  branches (the routine's `claude/*` branches), so the simple `pull_request`
  trigger is fine; just guard the login/push steps to no-op when secrets are
  absent (or note the same-repo assumption in the PR) rather than reaching for
  `pull_request_target`, which has security caveats.
- **Cleanup:** `pr-<number>` tags accumulate on Docker Hub. Out of scope to
  automate deletion here; note it as a possible follow-up (a small job on
  `pull_request: closed` could remove the tag).
- Backend currently has no `paths-ignore` on its workflows; this new workflow
  should add one so doc/`product/` pushes don't build images.
- Note the existing secret-name skew between repos (`DOCKER_PASSWORD` vs
  `DOCKER_TOKEN`) and use each repo's existing name; don't rename secrets.

## Out of scope

- A rolling `unstable` image on merge to `main` (dropped per maintainer review —
  the per-PR image is the only one wanted).
- Standing up the actual test/staging deployment (compose/host config) — this
  item only produces the `pr-<number>` images.
- Changing the release/versioned pipeline or the `latest` tag semantics.
- Auto-deploying the images anywhere, and automatic cleanup of old `pr-<number>`
  tags (note as follow-up).

## Notes

- Two separate PRs (one per repo), since each repo owns its own workflows. Cross
  link them. There is no backend/frontend ordering dependency here.
- CI changes can't be fully verified locally; rely on the workflow running on
  the PR itself, and say so in the PR. Use `act` or a syntax check if available,
  but don't claim a green run that didn't happen.

## Completion notes

- **Completed:** 2026-06-17
- **PRs:**
  - balance-backend: `claude/youthful-hamilton-rwyeh3` — adds
    `.github/workflows/docker-pr-preview.yml` + README "PR preview images" note
    + this bookkeeping.
  - balance-frontend: `claude/peaceful-hamilton-rwyeh3` — adds
    `.github/workflows/docker-pr-preview.yml` + README "Docker preview images"
    note.
  - No merge ordering between the two (each repo owns its own workflow); cross-
    linked in the PR bodies.

### Interpretation decisions
- **Trigger shape:** chose the spec's recommended *self-contained* shape — a
  `test` job plus a `build-push` job that `needs:` it — over the `workflow_run`
  alternative. Reason: `workflow_run` executes in the default-branch context,
  which makes deriving the PR number (and thus the `pr-<number>` tag) awkward;
  the self-contained gate is atomic and reads top-to-bottom. Trade-off: the
  test suite also runs in the existing `test.yml`/`ci.yml` on the same PR, i.e.
  it runs twice. Accepted for a clear, self-gating image build.
- **Tags:** `docker/metadata-action` with `type=ref,event=pr` (→ `pr-<number>`,
  branch-name agnostic) plus `type=sha` (short commit SHA, for traceability).
  No semver/`latest` tags are ever pushed here.
- **Tag rule precision:** by default `docker/metadata-action`'s `type=ref`
  events apply to `flavor.latest=auto` only for the default branch, and we set
  no `latest` flavor, so `latest` cannot be produced — `release.yml` remains the
  sole source of `latest`/semver.
- **Secret guarding:** detect Docker Hub credentials in a `creds` step (reading
  them via `env:` to avoid script injection) and gate the QEMU/Buildx/login/
  metadata/build steps on their presence, so a fork PR (no secrets) no-ops the
  push instead of failing. This repo's PRs come from same-repo `claude/*`
  branches, so credentials are normally present.
- **Per-repo secret names preserved:** backend uses `DOCKER_USERNAME` /
  `DOCKER_PASSWORD`; frontend uses `DOCKER_USERNAME` / `DOCKER_TOKEN`. Not
  renamed.
- **paths-ignore:** `**.md`, `.claude/**`, `product/**` on both workflows so
  docs-only PRs don't build an image (backend had no `paths-ignore` before).
- **Concurrency:** one in-flight preview build per PR
  (`cancel-in-progress: true`, keyed on the PR number) so rapid pushes don't
  pile up multi-arch builds.

### Deviations / not done
- `release.yml`, `release-please` config, manifests, and the Dockerfiles are
  unchanged, per spec.
- **Verification:** GitHub Actions workflows can't be executed locally in this
  environment. Validated both files parse as YAML (`yaml.safe_load`). The real
  green run only happens when these PRs trigger the workflow on GitHub — called
  out in both PR bodies. No green CI run is claimed here.
- **Follow-up (out of scope, noted in spec):** automatic cleanup of accumulated
  `pr-<number>` tags on Docker Hub (e.g. a `pull_request: closed` job).
