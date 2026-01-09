# Multi-Platform Docker Builds Implementation Plan

## Overview

Add multi-platform Docker image builds to support deployment on Raspberry Pi and other ARM64 devices. The release workflow will build images for both `linux/amd64` and `linux/arm64` platforms, publishing them as a multi-architecture manifest to Docker Hub.

## Current State Analysis

The repository already has most infrastructure in place for multi-platform builds:

- **Docker Buildx**: Already configured at `.github/workflows/release.yml:33-34`
- **Base Images**: `eclipse-temurin:17-jdk` and `eclipse-temurin:17-jre` support both amd64 and arm64
- **Dockerfile**: Platform-agnostic (pure Java/Maven build, standard Linux commands)
- **Missing**: QEMU setup for cross-platform emulation and `platforms` parameter in build action

### Key Discoveries:
- `.github/workflows/release.yml:53-61` - Build action has no `platforms` parameter, defaults to runner's native platform (amd64)
- `Dockerfile:2,20` - Eclipse Temurin images are multi-arch manifests, automatically pulling correct platform
- Research document `.claude/thoughts/research/2026-01-09-docker-infrastructure.md:293` identifies multi-architecture as an open question

## Desired End State

After implementation:
1. Every release publishes Docker images for both `linux/amd64` and `linux/arm64`
2. Docker Hub tags (`latest`, `X.Y.Z`, `X.Y`, `X`) are multi-architecture manifests
3. Users can `docker pull axelnyman/balance-backend:latest` on any platform and get the correct image
4. Raspberry Pi 3/4/5 and ARM64 servers (AWS Graviton, Apple Silicon) are supported

### Verification:
```bash
# Verify multi-arch manifest exists
docker manifest inspect axelnyman/balance-backend:latest

# Should show both platforms:
# - linux/amd64
# - linux/arm64
```

## What We're NOT Doing

- Adding `linux/arm/v7` (32-bit ARM) support - can be added later if needed
- Changing the Dockerfile structure
- Adding platform-specific build steps or optimizations
- Setting up native ARM runners (using QEMU emulation instead)

## Implementation Approach

The change is minimal - add two steps to the existing workflow:
1. Set up QEMU for cross-platform emulation
2. Add `platforms` parameter to the build-push-action

## Phase 1: Add Multi-Platform Build Support

### Overview
Modify the GitHub Actions release workflow to build for both amd64 and arm64 platforms.

### Changes Required:

#### 1. Add QEMU Setup Step
**File**: `.github/workflows/release.yml`
**Location**: After checkout (line 31), before Docker Buildx setup (line 33)
**Changes**: Add QEMU action for cross-platform emulation

```yaml
      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3
```

#### 2. Add Platforms Parameter to Build Action
**File**: `.github/workflows/release.yml`
**Location**: In the `Build and push Docker image` step, after `context: .` (line 56)
**Changes**: Add platforms parameter

```yaml
          platforms: linux/amd64,linux/arm64
```

### Complete Modified Workflow Section

The docker job steps (lines 29-61) will become:

```yaml
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up QEMU
        uses: docker/setup-qemu-action@v3

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
          platforms: linux/amd64,linux/arm64
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```

### Success Criteria:

#### Automated Verification:
- [x] Workflow file is valid YAML: `python -c "import yaml; yaml.safe_load(open('.github/workflows/release.yml'))"`
- [ ] No syntax errors when pushing to GitHub (workflow appears in Actions tab)

#### Manual Verification:
- [ ] Create a test release to trigger the workflow
- [ ] Verify workflow completes successfully in GitHub Actions
- [ ] Verify Docker Hub shows multi-architecture manifest: `docker manifest inspect axelnyman/balance-backend:latest`
- [ ] Test pulling and running on ARM64 device (Raspberry Pi or Apple Silicon with Docker)

---

## Phase 2: Update Research Documentation

### Overview
Update the Docker infrastructure research document to reflect that multi-architecture builds are now implemented.

### Changes Required:

#### 1. Update Open Questions Section
**File**: `.claude/thoughts/research/2026-01-09-docker-infrastructure.md`
**Location**: Line 293 (Open Questions section)
**Changes**: Mark multi-architecture as resolved

Change:
```markdown
3. **Multi-architecture builds**: Currently x86_64 only - ARM support not configured
```

To:
```markdown
3. ~~**Multi-architecture builds**: Currently x86_64 only - ARM support not configured~~ **RESOLVED**: Multi-platform builds enabled for `linux/amd64` and `linux/arm64`
```

### Success Criteria:

#### Automated Verification:
- [ ] Documentation file exists and is readable

#### Manual Verification:
- [ ] Documentation accurately reflects the implemented state

---

## Testing Strategy

### Integration Testing:
After merging changes, the next release will automatically trigger the multi-platform build. Monitor:
1. GitHub Actions workflow execution time (expect ~2-3x longer due to QEMU emulation)
2. Docker Hub image tags update correctly
3. Manifest includes both platforms

### Manual Testing Steps:
1. After a release, run: `docker manifest inspect axelnyman/balance-backend:latest`
2. Verify output shows both `linux/amd64` and `linux/arm64` platforms
3. On a Raspberry Pi or ARM64 Mac:
   ```bash
   docker pull axelnyman/balance-backend:latest
   docker run -e DATABASE_URL=... -e DATABASE_USERNAME=... -e DATABASE_PASSWORD=... -e SPRING_PROFILES_ACTIVE=docker axelnyman/balance-backend:latest
   ```
4. Verify the application starts and responds to health checks

## Performance Considerations

- **Build Time**: ARM64 builds via QEMU emulation are ~2-3x slower than native builds. For a Spring Boot application with Maven dependencies, expect total build time to increase from ~5 minutes to ~10-15 minutes.
- **Cache Usage**: Multi-platform builds generate more cache data. The `mode=max` setting is retained for faster builds; GitHub will automatically evict old entries if the 10GB limit is reached.
- **Image Size**: No change - each platform has its own layers, but users only download their platform's layers.

## Rollback Strategy

If issues occur, revert by:
1. Remove the QEMU setup step
2. Remove the `platforms` parameter from build-push-action

The workflow will return to building single-platform (amd64) images.

## References

- Research document: `.claude/thoughts/research/2026-01-09-docker-infrastructure.md`
- Current workflow: `.github/workflows/release.yml`
- Docker Buildx multi-platform documentation: https://docs.docker.com/build/building/multi-platform/
- docker/setup-qemu-action: https://github.com/docker/setup-qemu-action
- docker/build-push-action platforms: https://github.com/docker/build-push-action#inputs
