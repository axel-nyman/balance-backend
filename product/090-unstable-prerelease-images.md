# Build & push "unstable" Docker images on every merge to main

- **ID:** 090-unstable-prerelease-images
- **Scope:** full-stack (CI/infra — both repos)
- **Size:** M (about a day)

## Why

The maintainer wants a staging/testing deployment to try new features before
cutting a real release to the Raspberry Pi production. Today images are only
built when release-please's release PR is merged (a deliberate, infrequent
deploy gate). An always-fresh "unstable" image per repo, published on every
merge to `main`, makes a separate test environment possible without polluting
the versioned `latest` line that production pulls.

## What

Add a CI workflow to **each** repo that, on push to `main`, builds and pushes a
multi-arch Docker image tagged `unstable` (production's versioned/`latest` tags
stay exclusively release-please's). It must:

- **Only publish if tests pass** — gate the image build on the repo's existing
  test/CI job (backend `./mvnw clean test`; frontend lint + tsc + test + build).
- **Skip docs-only changes** — don't rebuild when a push only touches docs
  (`**.md`, `.claude/**`, `product/**`), mirroring the frontend CI's existing
  `paths-ignore`.
- **Not collide with release-please** — never push semver or `latest` tags from
  this workflow; only `unstable` (optionally also a short commit SHA tag for
  traceability).

## Acceptance criteria

- [ ] Each repo has a workflow that, on push to `main` with non-docs changes,
      runs the repo's tests and — only if they pass — builds and pushes a
      multi-arch (`linux/amd64,linux/arm64`) image tagged `unstable`
- [ ] Docs-only pushes (`**.md`, `.claude/**`, `product/**`) do **not** trigger
      an image build
- [ ] Failing tests block the push of the `unstable` image (no image on red)
- [ ] The workflow never pushes `latest` or any semver tag — those remain
      release-please's (`release.yml` unchanged in behaviour)
- [ ] Reuses existing Docker Hub auth secrets (backend `DOCKER_USERNAME` /
      `DOCKER_PASSWORD`; frontend `DOCKER_USERNAME` / `DOCKER_TOKEN`) and the
      existing Dockerfiles unchanged
- [ ] Image names match production (`axelnyman/balance-backend`,
      `axelnyman/balance-frontend`) with the `unstable` tag
- [ ] A short note in each repo's README (or product docs) explains the
      `unstable` tag and that it is not for production

## Implementation notes

- **This item explicitly authorizes adding `.github/workflows/` files** (the
  routine normally forbids touching CI). Do **not** modify `release.yml`,
  `release-please-config.json`, the manifests, or the Dockerfiles.
- Recommended shape: a new `docker-unstable.yml` per repo, `on: push:
  branches: [main]` with `paths-ignore`, containing a test job and a
  build-push job that `needs:` it. Mirror the existing release Docker steps
  (QEMU + Buildx + `docker/login-action` + `docker/metadata-action` with a
  single `type=raw,value=unstable` tag + `docker/build-push-action`,
  `cache-from/to: type=gha`). Alternatively use `workflow_run` gated on the
  existing test/ci workflow concluding `success` — pick one and explain why in
  the PR.
- Backend currently has no `paths-ignore` on its workflows; this new workflow
  should add one so doc/`product/` pushes don't build images.
- Note the existing secret-name skew between repos (`DOCKER_PASSWORD` vs
  `DOCKER_TOKEN`) and use each repo's existing name; don't rename secrets.

## Out of scope

- Standing up the actual test/staging deployment (compose/host config) — this
  item only produces the `unstable` images.
- Changing the release/versioned pipeline or the `latest` tag semantics.
- Auto-deploying the `unstable` image anywhere.

## Notes

- Two separate PRs (one per repo), since each repo owns its own workflows. Cross
  link them. There is no backend/frontend ordering dependency here.
- CI changes can't be fully verified locally; rely on the workflow running on
  `main` after merge, and say so in the PR. Use `act` or a syntax check if
  available, but don't claim a green run that didn't happen.
