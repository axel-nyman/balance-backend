---
date: 2026-01-09T12:00:00+01:00
researcher: Claude
git_commit: 67081917df23a844b23bf4e962593a5d1f9258df
branch: main
repository: balance-backend
topic: "Release-please implementation for automated versioning and Docker Hub publishing"
tags: [research, ci-cd, release-please, github-actions, docker, maven, versioning]
status: complete
last_updated: 2026-01-09
last_updated_by: Claude
---

# Research: Release-please Implementation for Automated Versioning and Docker Hub Publishing

**Date**: 2026-01-09T12:00:00+01:00
**Researcher**: Claude
**Git Commit**: 67081917df23a844b23bf4e962593a5d1f9258df
**Branch**: main
**Repository**: balance-backend

## Research Question
How compatible is this codebase with release-please for automated versioning and Docker Hub publishing? What would be needed for implementation?

## Summary

This codebase is **highly compatible** with release-please. The major prerequisites are already in place:

1. **Conventional commits already in use** - Git history shows consistent use of `feat:`, `fix:`, `docs:`, `chore:`, `refactor:` prefixes
2. **Maven project structure** - Standard `pom.xml` with versioning at `1.0.0-SNAPSHOT`
3. **Multi-stage Dockerfile** - Production-ready Docker build
4. **Existing GitHub Actions** - Test workflow in place that can be extended

The implementation would require adding release-please configuration files and extending the GitHub Actions workflow.

## Detailed Findings

### Current Codebase State

#### GitHub Actions Workflow
- **File**: `.github/workflows/test.yml`
- **Current functionality**: Runs tests on push to main/develop and PRs to main
- **Uses**: JDK 17 with Temurin distribution, Maven wrapper

```yaml
# Current triggers
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]
```

#### Docker Configuration
- **File**: `Dockerfile`
- **Multi-stage build**: Builder stage with JDK 17, runtime with JRE 17
- **Security**: Non-root user (`appuser:appgroup`)
- **Build command**: `./mvnw package -DskipTests -B`

#### Maven Configuration
- **File**: `pom.xml`
- **Current version**: `1.0.0-SNAPSHOT`
- **Artifact**: `spring-boot-rest-api-template`
- **Group ID**: `com.template`

#### Git Commit History (Conventional Commits)
Recent commits demonstrate consistent conventional commit usage:
- `docs: add research and plan for budget totals fix`
- `fix: calculate correct budget totals in get all budgets endpoint`
- `feat: add validation to prevent backdated balance history entries`
- `chore: add planning notes and update Claude settings`
- `refactor: change balance history date from LocalDateTime to LocalDate`

### Release-Please Configuration Requirements

#### 1. Release-Please Config File
Create `release-please-config.json`:
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

#### 2. Manifest File
Create `.release-please-manifest.json`:
```json
{
  ".": "1.0.0"
}
```

Note: Start at `1.0.0` since the pom.xml is at `1.0.0-SNAPSHOT`. Release-please will manage the version from this point.

#### 3. GitHub Actions Workflow
Create or update `.github/workflows/release.yml`:

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
    steps:
      - name: Release Please
        uses: googleapis/release-please-action@v4
        id: release
        with:
          release-type: maven
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Checkout
        if: ${{ steps.release.outputs.release_created }}
        uses: actions/checkout@v4

      - name: Set up JDK 17
        if: ${{ steps.release.outputs.release_created }}
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Build JAR
        if: ${{ steps.release.outputs.release_created }}
        run: ./mvnw clean package -DskipTests

      - name: Set up Docker Buildx
        if: ${{ steps.release.outputs.release_created }}
        uses: docker/setup-buildx-action@v3

      - name: Log in to Docker Hub
        if: ${{ steps.release.outputs.release_created }}
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKER_USERNAME }}
          password: ${{ secrets.DOCKER_PASSWORD }}

      - name: Extract Docker metadata
        if: ${{ steps.release.outputs.release_created }}
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: axelnyman/balance-backend
          tags: |
            type=semver,pattern={{version}},value=${{ steps.release.outputs.tag_name }}
            type=semver,pattern={{major}}.{{minor}},value=${{ steps.release.outputs.tag_name }}
            type=semver,pattern={{major}},value=${{ steps.release.outputs.tag_name }}
            type=raw,value=latest

      - name: Build and push Docker image
        if: ${{ steps.release.outputs.release_created }}
        uses: docker/build-push-action@v5
        with:
          context: .
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Conventional Commits Version Bump Rules

| Commit Type | Version Bump | Example |
|-------------|--------------|---------|
| `fix:` | PATCH (0.0.x) | `fix: resolve null pointer exception` |
| `feat:` | MINOR (0.x.0) | `feat: add bank account balance history` |
| `feat!:` or `fix!:` | MAJOR (x.0.0) | `feat!: redesign authentication API` |
| `BREAKING CHANGE:` in footer | MAJOR (x.0.0) | Any commit with breaking change footer |
| `chore:`, `docs:`, `refactor:` | No bump | Internal changes (won't trigger release) |

### How Release-Please Works

1. **On every push to main**: Analyzes commits since last release
2. **Creates/updates Release PR**: If releasable changes exist (feat/fix/BREAKING)
3. **When Release PR is merged**: Creates GitHub release with tag, triggers Docker build
4. **After release**: Creates SNAPSHOT PR to bump to next SNAPSHOT version

### GitHub Secrets Required

Configure in GitHub Repository Settings > Secrets and Variables > Actions:
- `DOCKER_USERNAME`: Docker Hub username (axelnyman)
- `DOCKER_PASSWORD`: Docker Hub access token (not password, create at hub.docker.com)

### Important Caveats

#### Critical Bug Warning
Use `release_created` (singular), NOT `releases_created` (plural):
```yaml
# CORRECT
if: ${{ steps.release.outputs.release_created }}

# INCORRECT - defaults to true even when no release
if: ${{ steps.release.outputs.releases_created }}
```

#### Triggering Other Workflows
Releases created with `GITHUB_TOKEN` won't trigger other workflows (like tag-based triggers). If you need to trigger separate workflows, use a Personal Access Token (PAT) instead.

## Code References
- `.github/workflows/test.yml` - Existing test workflow
- `Dockerfile` - Multi-stage build configuration
- `pom.xml:17` - Current version: `1.0.0-SNAPSHOT`

## Architecture Documentation

The codebase follows a standard Spring Boot Maven project structure with:
- Maven wrapper for consistent builds (`./mvnw`)
- Multi-stage Docker builds for optimized images
- GitHub Actions for CI (tests on push/PR)

## Historical Context (from thoughts/)

- `.claude/thoughts/plans/2025-12-28-timezone-fix.md` - Documents Dockerfile ENTRYPOINT modifications for timezone
- `.claude/thoughts/research/2025-12-28-timestamp-date-handling.md` - References Docker deployment considerations

## Files to Create for Implementation

1. `release-please-config.json` (repository root)
2. `.release-please-manifest.json` (repository root)
3. `.github/workflows/release.yml` (new workflow)

## Open Questions

1. **Version starting point**: Should we start at 1.0.0 or reset to 0.1.0?
2. **Existing Docker Hub images**: Need to decide how to handle existing manually-pushed tags
3. **CHANGELOG.md**: Release-please will create/manage this - is that acceptable?
4. **Branch protection**: May need to allow release-please to push to main for SNAPSHOT bumps

## Sources

- [Release Please GitHub Repository](https://github.com/googleapis/release-please)
- [Release Please Action](https://github.com/googleapis/release-please-action)
- [Release Please Java Documentation](https://github.com/googleapis/release-please/blob/main/docs/java.md)
- [Conventional Commits Specification](https://www.conventionalcommits.org/en/v1.0.0/)
- [Docker Build CI with GitHub Actions](https://docs.docker.com/build/ci/github-actions/)
