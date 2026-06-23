# Clean up CI and agentic workflows

- **ID:** 055-workflow-cleanup
- **Scope:** frontend & backend
- **Size:** S (≤ half a day)

## Why

Certain GitHub actions could be improved on when they're triggered and how they're configured. docker images pile up quickly in docker hub etc.

## What

Change the PR actions (both repos) to only run when the PR is opened or changed. Certain workflows are also run on merge which isn't needed.

Change the PR image build/push workflow (both repos) to only push pr-latest. I don't need any more granularity than that, and the extra tags ("pr-xx", "sha...") just clutter the repo on docker hub. If there's an easy fix for automatically cleaning up old images from docker hub you could add that to this PR as well, so that only the latest few images are stored under pr-latest.

Slightly modify the agent prompts under /product in backend to spend more focus on research and doing more focused code edits. The last few PRs in my mind has been slightly too "verbose" in the code edits. Also, don't be too verbose in doing these edits.
## Completion notes

**Completed:** 2026-06-23
**PRs:** balance-backend (this repo — CI + docs + bookkeeping), balance-frontend (CI)
**Backlog item:** 055-workflow-cleanup

### What changed

1. **PR actions only run on open/change (both repos).** Removed the redundant
   post-merge (`push: main`) trigger from `balance-backend/.github/workflows/test.yml`
   and `balance-frontend/.github/workflows/ci.yml`. The PR already gates merges,
   so re-running the full suite on `main` after merge only burned CI minutes.
   `release.yml` still owns the merge-to-main path. `docker-pr-preview.yml` and
   `pr-title-lint.yml` were already PR-only and were left as-is.

2. **PR preview image pushes only `pr-latest` (both repos).** In each
   `docker-pr-preview.yml`, the `docker/metadata-action` tag list was reduced
   from `type=ref,event=pr` + `type=sha` + `type=raw,value=pr-latest` to just
   `type=raw,value=pr-latest`. Both READMEs' preview-image sections were updated
   to match.

3. **Docker Hub cleanup:** addressed by construction rather than a new workflow.
   With a single rolling `pr-latest` tag, each PR push overwrites the tag, so
   there is no per-PR/per-SHA tag pile-up to clean up. A dedicated retention
   workflow was deliberately *not* added: it would require a new Docker Hub API
   credential and destructive delete calls — outside the "easy/safe fix" the
   spec asked for. Untagged digests left behind by the moving tag are handled by
   Docker Hub's own GC; if the maintainer later wants hard retention, that can
   be a small follow-up with the appropriate secret.

4. **Agent prompts tightened** (`product/ROUTINE_PROMPT.md`): the Plan step now
   leads with "invest in research before touching code" and a new hard guardrail,
   "Keep the diff focused and minimal," asks for the smallest correct edit and
   no reformatting / speculative abstraction / comment padding.

### Interpretation decisions

- "Only run when the PR is opened or changed" → keep the `pull_request`
  triggers (whose default types are `opened`/`synchronize`/`reopened`) and drop
  the `push: main` triggers that fired on merge. Did not add explicit `types:`
  since the defaults already mean exactly "opened or changed."
- Left the duplicate PR test run (suite runs in both `test.yml`/`ci.yml` and the
  preview workflow's gate job) untouched — that pre-existing redundancy is out
  of this item's scope; noted here as a possible future dedupe.

### Verification

- All four modified workflow YAML files parse and expose only the
  `pull_request` trigger (validated with PyYAML).
- No application code changed in either repo, so the app test suites carry no
  signal for this item and were not run; the change is CI/docs only.
