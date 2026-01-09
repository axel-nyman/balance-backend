# Release-Please with Docker Hub Publishing Implementation Plan

## Overview

Implement automated versioning and Docker Hub publishing using release-please. When conventional commits are pushed to main, release-please will create/update a Release PR. When that PR is merged, it will create a GitHub release, update the version in pom.xml, and automatically build and push Docker images to Docker Hub.

## Current State Analysis

- **Test workflow**: `.github/workflows/test.yml` runs tests on push to main/develop and PRs
- **Dockerfile**: Multi-stage build, production-ready (lines 1-38)
- **pom.xml**: Version at `1.0.0-SNAPSHOT` (line 17)
- **Conventional commits**: Already in use consistently

### Key Discoveries:

- Existing workflow uses JDK 17 Temurin with Maven cache
- Dockerfile builds JAR internally, so we can use it as-is for Docker Hub publishing
- No existing release automation or changelog

## Desired End State

After implementation:

1. Every push to main triggers release-please to analyze commits
2. If releasable changes exist (feat/fix), a Release PR is created/updated
3. Merging the Release PR:
   - Creates a GitHub release with tag (e.g., `v1.0.0`)
   - Updates pom.xml version
   - Generates/updates CHANGELOG.md
   - Builds and pushes Docker image to `axelnyman/balance-backend` with tags:
     - `1.0.0` (full version)
     - `1.0` (major.minor)
     - `1` (major)
     - `latest`

### Verification:

- Push a `feat:` commit → Release PR appears
- Merge Release PR → GitHub release created, Docker image pushed
- `docker pull axelnyman/balance-backend:latest` works

## What We're NOT Doing

- Not modifying the existing test.yml workflow
- Not setting up multi-architecture builds (can be added later)
- Not adding container scanning/security checks (future enhancement)
- Not changing the Dockerfile
- Not setting up GitHub Packages (Docker Hub only)

## Implementation Approach

Create three files:

1. `release-please-config.json` - Configuration for release-please
2. `.release-please-manifest.json` - Version tracking
3. `.github/workflows/release.yml` - GitHub Actions workflow

The workflow will:

1. Run release-please on every push to main
2. When a release is created, build and push Docker image

## Phase 1: Create Release-Please Configuration Files

### Overview

Add the two configuration files that release-please needs to function.

### Changes Required:

#### 1. Release-Please Config

**File**: `release-please-config.json` (new file, repository root)

```json
{
  "$schema": "https://raw.githubusercontent.com/googleapis/release-please/main/schemas/config.json",
  "release-type": "maven",
  "packages": {
    ".": {
      "include-component-in-tag": false
    }
  }
}
```

#### 2. Release-Please Manifest

**File**: `.release-please-manifest.json` (new file, repository root)

```json
{
  ".": "1.0.0"
}
```

Note: Starting at `1.0.0` since pom.xml is at `1.0.0-SNAPSHOT`. Release-please will manage versions from this point.

### Success Criteria:

#### Automated Verification:

- [x] Files exist: `ls release-please-config.json .release-please-manifest.json`
- [x] JSON is valid: `cat release-please-config.json | python3 -m json.tool`
- [x] JSON is valid: `cat .release-please-manifest.json | python3 -m json.tool`

#### Manual Verification:

- [x] None required for this phase

---

## Phase 2: Create GitHub Actions Release Workflow

### Overview

Create the workflow that runs release-please and builds/pushes Docker images on release.

### Changes Required:

#### 1. Release Workflow

**File**: `.github/workflows/release.yml` (new file)

```yaml
name: Release and Deploy

on:
  push:
    branches:
      - main

permissions:
  contents: write
  pull-requests: write

jobs:
  release:
    runs-on: ubuntu-latest
    outputs:
      release_created: ${{ steps.release.outputs.release_created }}
      tag_name: ${{ steps.release.outputs.tag_name }}
    steps:
      - name: Release Please
        uses: googleapis/release-please-action@v4
        id: release
        with:
          token: ${{ secrets.GITHUB_TOKEN }}

  docker:
    needs: release
    if: ${{ needs.release.outputs.release_created == 'true' }}
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Extract Docker metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: axelnyman/balance-backend
          tags: |
            type=semver,pattern={{version}},value=${{ needs.release.outputs.tag_name }}
            type=semver,pattern={{major}}.{{minor}},value=${{ needs.release.outputs.tag_name }}
            type=semver,pattern={{major}},value=${{ needs.release.outputs.tag_name }}
            type=raw,value=latest

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Success Criteria:

#### Automated Verification:

- [x] File exists: `ls .github/workflows/release.yml`
- [x] YAML is valid: `cat .github/workflows/release.yml | python3 -c "import sys, yaml; yaml.safe_load(sys.stdin)"`
- [x] Existing tests still pass: `./mvnw test`

#### Manual Verification:

- [x] None required for this phase

---

## Phase 3: Configure GitHub Repository Secrets

### Overview

Add the required Docker Hub credentials to the GitHub repository settings.

### Changes Required:

This phase requires manual configuration in GitHub:

1. **Create Docker Hub Access Token**:

   - Go to https://hub.docker.com/settings/security
   - Click "New Access Token"
   - Name: `balance-backend-github-actions`
   - Access permissions: Read & Write
   - Copy the generated token

2. **Add GitHub Repository Secrets**:
   - Go to GitHub repository → Settings → Secrets and variables → Actions
   - Add two secrets:
     - `DOCKER_USERNAME`: `axelnyman`
     - `DOCKER_PASSWORD`: (paste the Docker Hub access token)

### Success Criteria:

#### Automated Verification:

- [x] None (secrets are not visible)

#### Manual Verification:

- [x] DOCKER_USERNAME secret exists in GitHub repository settings
- [x] DOCKER_PASSWORD secret exists in GitHub repository settings

**Implementation Note**: After completing this phase and verifying secrets exist, proceed to Phase 4.

---

## Phase 4: Commit, Push, and Verify

### Overview

Commit all changes and verify the workflow triggers correctly.

### Changes Required:

1. Commit the three new files with a conventional commit message
2. Push to main
3. Verify release-please creates a Release PR (since there are releasable commits)

### Success Criteria:

#### Automated Verification:

- [x] All files committed: `git status` shows clean working tree
- [x] Push succeeds: `git push origin main`

#### Manual Verification:

- [x] GitHub Actions shows "Release and Deploy" workflow running
- [x] Release-please creates a Release PR (check GitHub Pull Requests)
- [x] Release PR contains:
  - Changelog in PR description (not separate file for Maven)
  - Version bump in pom.xml to 1.0.1-SNAPSHOT

**Implementation Note**: After verifying the Release PR is created correctly, proceed to Phase 5.

---

## Phase 5: Merge Release PR and Verify Docker Push

### Overview

Merge the Release PR and verify the complete workflow including Docker Hub publishing.

### Changes Required:

1. Review and merge the Release PR created by release-please
2. Verify GitHub release is created
3. Verify Docker image is pushed to Docker Hub

### Success Criteria:

#### Automated Verification:

- [x] Docker image exists: `docker pull axelnyman/balance-backend:1.0.1`
- [x] Latest tag works: `docker pull axelnyman/balance-backend:latest`

#### Manual Verification:

- [x] GitHub Releases page shows v1.0.1 release with changelog
- [x] Docker Hub shows image at https://hub.docker.com/r/axelnyman/balance-backend
- [x] Image has expected tags: `1.0.1`, `1.0`, `1`, `latest`

---

## Testing Strategy

### Integration Testing:

After Phase 5, create a test commit to verify the full cycle:

1. Push a `feat: test release workflow` commit
2. Verify Release PR is updated (version becomes 1.1.0)
3. Merge and verify Docker image `1.1.0` is pushed

### Rollback Testing:

If issues occur:

- Delete the Release PR (release-please will recreate on next push)
- Docker images can be deleted from Docker Hub console
- GitHub releases can be deleted from Releases page

## Important Notes

### Critical Bug Warning

The workflow uses `release_created` (singular), NOT `releases_created` (plural). The plural version defaults to true even when no release is created, which would cause unnecessary Docker builds.

### Conventional Commits Version Bumps

| Commit Type                    | Version Bump  |
| ------------------------------ | ------------- |
| `fix:`                         | PATCH (0.0.x) |
| `feat:`                        | MINOR (0.x.0) |
| `feat!:` or `BREAKING CHANGE:` | MAJOR (x.0.0) |
| `chore:`, `docs:`, `refactor:` | No release    |

### Workflow Triggers

- Releases created with `GITHUB_TOKEN` won't trigger other workflows
- If you need tag-triggered workflows in the future, use a Personal Access Token

## References

- Research document: `.claude/thoughts/research/2026-01-09-release-please-docker-hub.md`
- Current test workflow: `.github/workflows/test.yml`
- Dockerfile: `Dockerfile`
- pom.xml version: `pom.xml:17`
- [Release Please Action](https://github.com/googleapis/release-please-action)
- [Docker Build Push Action](https://github.com/docker/build-push-action)
