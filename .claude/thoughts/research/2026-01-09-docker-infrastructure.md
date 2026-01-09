---
date: 2026-01-09T16:30:00+01:00
researcher: Claude
git_commit: b4c3a363a7af9a5c2dc28162a12c84b747bb7d89
branch: main
repository: balance-backend
topic: "Docker Infrastructure: Image Building and Docker Hub Publishing"
tags: [research, docker, ci-cd, docker-hub, github-actions, containerization]
status: complete
last_updated: 2026-01-09
last_updated_by: Claude
---

# Research: Docker Infrastructure - Image Building and Docker Hub Publishing

**Date**: 2026-01-09T16:30:00+01:00
**Researcher**: Claude
**Git Commit**: b4c3a363a7af9a5c2dc28162a12c84b747bb7d89
**Branch**: main
**Repository**: balance-backend

## Research Question
Research everything regarding Docker in this codebase, with extra focus on image building and Docker Hub pushing.

## Summary

The balance-backend project implements a comprehensive Docker infrastructure with:

1. **Multi-stage Dockerfile** for optimized production images (JDK builder → JRE runtime)
2. **Automated CI/CD pipeline** using GitHub Actions with Release-Please for versioned releases
3. **Docker Hub publishing** to `axelnyman/balance-backend` with semantic version tagging
4. **Local development environment** via Docker Compose with PostgreSQL and Adminer
5. **Docker-specific Spring Boot profile** for containerized deployment

The Docker workflow follows modern best practices: layer caching, non-root execution, multi-stage builds, and Conventional Commits-driven versioning.

## Detailed Findings

### 1. Dockerfile and Image Building

**File**: `Dockerfile`

The application uses a multi-stage Docker build optimized for production:

**Stage 1: Builder (lines 2-17)**
- Base image: `eclipse-temurin:17-jdk`
- Copies Maven wrapper and `pom.xml` first for dependency caching
- Downloads dependencies via `./mvnw dependency:go-offline -B`
- Copies source code after dependencies (maximizes cache hits)
- Builds JAR with `./mvnw package -DskipTests -B`

**Stage 2: Runtime (lines 20-38)**
- Base image: `eclipse-temurin:17-jre` (smaller JRE-only image)
- Creates non-root user `appuser` (UID 1001) for security
- Copies only the built JAR from builder stage
- Exposes port 8080
- Entrypoint: `java -jar app.jar` (exec form for proper signal handling)

**Key Optimization Techniques**:
- **Layer caching**: Dependencies downloaded before source code copy
- **Multi-stage build**: Build tools excluded from final image (~200-300 MB reduction)
- **Non-root execution**: Container runs as `appuser:appgroup`
- **Minimal runtime**: JRE-only base image

**.dockerignore** excludes:
- Git files, IDE configurations, build outputs
- Environment files (`.env`, `*.env`)
- Documentation (`*.md`, `docs/`, `todo/`)
- Test files (`src/test/`)
- Docker files themselves (prevents recursive context)

### 2. Docker Hub Publishing Pipeline

**File**: `.github/workflows/release.yml`

The release workflow implements a two-job architecture:

**Job 1: Release (lines 13-23)**
- Triggers on push to `main` branch
- Uses `googleapis/release-please-action@v4`
- Analyzes Conventional Commits since last release
- Creates/updates Release PR with changelog
- Outputs: `release_created` (boolean) and `tag_name` (version)

**Job 2: Docker (lines 25-61)**
- **Conditional execution**: Only runs when `release_created == 'true'`
- **Authentication**: Uses `DOCKER_USERNAME` and `DOCKER_PASSWORD` secrets
- **Buildx setup**: Enables advanced build features and caching
- **Metadata extraction**: Generates multiple tags from semantic version

**Docker Image Tagging Strategy** (lines 47-51):
For a release tagged `v1.2.3`, four tags are created:
1. `axelnyman/balance-backend:1.2.3` (full version)
2. `axelnyman/balance-backend:1.2` (major.minor)
3. `axelnyman/balance-backend:1` (major only)
4. `axelnyman/balance-backend:latest` (always points to latest)

**Build Caching**:
- Cache from: `type=gha` (GitHub Actions cache)
- Cache to: `type=gha,mode=max` (all layers cached)

**Required GitHub Secrets**:
- `DOCKER_USERNAME`: Docker Hub username (axelnyman)
- `DOCKER_PASSWORD`: Docker Hub access token

### 3. Version Management with Release-Please

**Release-Please Configuration Files**:
- `release-please-config.json` (repository root)
- `.release-please-manifest.json` (repository root)

**Conventional Commits Version Bumps**:
| Commit Type | Version Bump | Example |
|-------------|--------------|---------|
| `fix:` | PATCH (0.0.x) | `fix: correct null pointer` |
| `feat:` | MINOR (0.x.0) | `feat: add export endpoint` |
| `feat!:` or `BREAKING CHANGE:` | MAJOR (x.0.0) | `feat!: change API format` |
| `chore:`, `docs:`, `refactor:` | No release | Maintenance only |

**Release Workflow**:
1. Developer pushes commits to `main`
2. Release-please analyzes commit messages
3. If releasable changes exist, creates/updates Release PR
4. Merging Release PR creates GitHub release + tag
5. Tag triggers Docker build and push
6. SNAPSHOT PR created for next development version

### 4. Local Development Environment

**File**: `docker-compose.dev.yml`

Provides PostgreSQL database and Adminer UI for local development:

**PostgreSQL Service (`db`)**:
- Image: `postgres:15`
- Port: `5432:5432`
- Credentials: `user/password` (dev defaults)
- Database: `mydatabase`
- Volume: `postgres_dev_data` (persistent)
- Health check: `pg_isready` with 30s interval

**Adminer Service (`adminer`)**:
- Image: `adminer`
- Port: `8081:8080`
- Pre-configured to connect to `db` service

**Usage**:
```bash
# Start database
docker-compose -f docker-compose.dev.yml up -d

# Run application (connects to localhost:5432)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Access database UI
http://localhost:8081
```

### 5. Docker-Specific Application Profile

**File**: `src/main/resources/application-docker.yml`

Spring Boot configuration for containerized deployment:

**Database Configuration**:
```yaml
datasource:
  url: ${DATABASE_URL}
  username: ${DATABASE_USERNAME}
  password: ${DATABASE_PASSWORD}
```

**Hibernate Settings**:
- `ddl-auto: validate` (validates schema, no modifications)
- `show-sql: false` (production logging)

**Flyway Migration**:
- Enabled with migrations from `classpath:db/migration`
- `clean-disabled: true` (CRITICAL: prevents accidental data loss)

**Required Environment Variables**:
- `DATABASE_URL` (e.g., `jdbc:postgresql://db:5432/mydatabase`)
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `SPRING_PROFILES_ACTIVE=docker`

### 6. Profile Comparison

| Aspect | Local | Docker | Default |
|--------|-------|--------|---------|
| Database URL | Hardcoded localhost | Env var required | Env var with fallback |
| Credentials | Hardcoded dev values | Env var required | Env var with fallback |
| SQL Logging | DEBUG | OFF | OFF |
| Flyway Clean | Allowed | **Disabled** | Not specified |
| Use Case | Development | Production/Containers | Flexible |

## Code References

### Core Docker Files
- `Dockerfile:2-17` - Builder stage with Maven compilation
- `Dockerfile:20-38` - Runtime stage with non-root user
- `.dockerignore` - Build context exclusions

### CI/CD Pipeline
- `.github/workflows/release.yml:13-23` - Release-Please job
- `.github/workflows/release.yml:25-61` - Docker build and push job
- `.github/workflows/release.yml:47-51` - Semantic version tag patterns

### Application Configuration
- `src/main/resources/application-docker.yml:9-13` - Database connection
- `src/main/resources/application-docker.yml:24-27` - Flyway with clean protection

### Local Development
- `docker-compose.dev.yml:4-19` - PostgreSQL service configuration
- `docker-compose.dev.yml:21-30` - Adminer UI service

## Architecture Documentation

### Docker Image Build Flow
```
git push to main
    ↓
Release-Please analyzes commits
    ↓
Creates/updates Release PR (if releasable changes)
    ↓
PR merged → GitHub Release created
    ↓
Docker job triggered (release_created == true)
    ↓
docker/login-action authenticates to Docker Hub
    ↓
docker/metadata-action generates tags
    ↓
docker/build-push-action builds multi-stage Dockerfile
    ↓
Images pushed to axelnyman/balance-backend
    (tags: X.Y.Z, X.Y, X, latest)
```

### Container Runtime Architecture
```
┌─────────────────────────────────────────┐
│  Docker Host                            │
│  ┌────────────────┐  ┌────────────────┐ │
│  │ balance-backend│  │ PostgreSQL 15  │ │
│  │ (JRE 17)       │→→│                │ │
│  │ Port 8080      │  │ Port 5432      │ │
│  │ User: appuser  │  │                │ │
│  └────────────────┘  └────────────────┘ │
│         ↑                    ↑          │
│    External access     Volume mount     │
└─────────────────────────────────────────┘
```

### Security Measures
1. **Non-root execution**: Application runs as `appuser` (UID 1001)
2. **Minimal image**: JRE-only runtime excludes build tools
3. **Secret management**: Credentials via environment variables only
4. **Flyway protection**: Clean disabled in Docker profile
5. **No hardcoded secrets**: Docker profile requires explicit configuration

## Historical Context (from thoughts/)

### Related Research Documents
- `.claude/thoughts/research/2026-01-09-docker-compose-deployment.md`
  - Documents deployment configuration for Docker Compose
  - Notes CORS and authentication as unimplemented blockers
  - Provides complete environment variable reference

- `.claude/thoughts/research/2026-01-09-release-please-docker-hub.md`
  - Evaluates codebase compatibility with release-please
  - Documents critical bug: use `release_created` (singular) not `releases_created`
  - Details conventional commits → version bump mapping

- `.claude/thoughts/plans/2026-01-09-release-please-docker-hub.md`
  - 5-phase implementation plan for release automation
  - Includes rollback strategy and verification steps
  - Phase 3 requires manual GitHub secrets configuration

### Key Historical Decisions
1. **Docker Hub as registry**: Standard distribution method, public access
2. **Release-Please for automation**: Conventional Commits drive versioning
3. **Four-tag strategy**: Maximum flexibility for image consumers
4. **Separate development compose**: Application runs on host, only database containerized

## Open Questions

1. **CORS Configuration**: Not implemented - required for cross-origin frontend access
2. **Health Endpoint**: Spring Boot Actuator not included - consider `/actuator/health`
3. **Multi-architecture builds**: Currently x86_64 only - ARM support not configured
4. **Container scanning**: No security scanning in CI/CD pipeline
5. **Rate limiting**: Docker Hub free tier has pull rate limits

## Quick Reference

### Building Image Locally
```bash
docker build -t balance-backend:local .
```

### Running Container Locally
```bash
docker run -d \
  -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/mydatabase \
  -e DATABASE_USERNAME=user \
  -e DATABASE_PASSWORD=password \
  -e SPRING_PROFILES_ACTIVE=docker \
  balance-backend:local
```

### Triggering a Release
```bash
# Create a releasable commit
git commit -m "feat: add new feature"
git push origin main

# Release-Please will create a Release PR
# Merge the PR to trigger Docker Hub push
```

### Pulling from Docker Hub
```bash
# Latest version
docker pull axelnyman/balance-backend:latest

# Specific version
docker pull axelnyman/balance-backend:1.2.3
```
