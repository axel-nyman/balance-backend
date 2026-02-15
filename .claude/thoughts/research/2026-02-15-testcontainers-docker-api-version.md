---
date: 2026-02-15T12:00:00+01:00
researcher: Claude
git_commit: d08a844a8ac4b7f954c09fbc849f1e31b9040d42
branch: main
repository: balance-backend
topic: "Testcontainers Docker API version incompatibility in CI/CD"
tags: [research, testcontainers, docker, ci-cd, github-actions, version-compatibility]
status: complete
last_updated: 2026-02-15
last_updated_by: Claude
---

# Research: Testcontainers Docker API Version Incompatibility in CI/CD

**Date**: 2026-02-15
**Researcher**: Claude
**Git Commit**: d08a844a8ac4b7f954c09fbc849f1e31b9040d42
**Branch**: main
**Repository**: balance-backend

## Research Question
CI/CD test regressions after commit dd04c10. Error: "client version 1.32 is too old. Minimum supported API version is 1.44". Research the root cause and how version compatibilities can be resolved across the codebase.

## Summary

The test failures in CI/CD are **not caused by commit dd04c10** (the month/year due date refactor). The error is a Docker API version mismatch between the Testcontainers library (v1.19.3, which uses docker-java defaulting to API v1.32) and the Docker Engine on GitHub Actions `ubuntu-latest` runners (Docker 29.0.0+, which requires minimum API v1.44). The timing is coincidental - GitHub updated their runner images with Docker 29.0.0+, which broke all projects using older Testcontainers versions.

## Detailed Findings

### Root Cause: Docker Engine 29.0.0 Breaking Change

Docker Engine 29.0.0 (released November 2024) increased the minimum required Docker API version from 1.24 to 1.44. The project's Testcontainers 1.19.3 uses docker-java which defaults to API version 1.32, which is now rejected.

**Error chain:**
1. GitHub Actions `ubuntu-latest` updated to Docker Engine 29.0.0+
2. Testcontainers 1.19.3 → docker-java → connects with API v1.32
3. Docker Engine rejects: "client version 1.32 is too old. Minimum supported API version is 1.44"
4. All 3 integration test classes fail (they all use `@Testcontainers` with `PostgreSQLContainer`)

### Current Testcontainers Configuration

**pom.xml** (`pom.xml:20`):
```xml
<testcontainers.version>1.19.3</testcontainers.version>
```

**Dependencies** (`pom.xml:64-80`):
- `org.testcontainers:junit-jupiter`
- `org.testcontainers:postgresql`
- `org.testcontainers:jdbc`
- All managed via BOM at version 1.19.3

**Test classes using Testcontainers:**
- `src/test/java/.../integration/BankAccountIntegrationTest.java` - `PostgreSQLContainer<>("postgres:15-alpine")`
- `src/test/java/.../integration/RecurringExpenseIntegrationTest.java` - `PostgreSQLContainer<>("postgres:15-alpine")`
- `src/test/java/.../integration/BudgetIntegrationTest.java` - `PostgreSQLContainer<>("postgres:15-alpine")`

### CI/CD Configuration

**GitHub Actions** (`.github/workflows/test.yml`):
- Runs on `ubuntu-latest` (no pinned version)
- No Docker version pinning
- Simply runs `./mvnw clean test`
- No `DOCKER_API_VERSION` environment variable set

### Docker Compose Version

**docker-compose.dev.yml** uses `version: '3.8'` which is deprecated in newer Docker Compose versions. This is unrelated to the CI issue but is another version compatibility surface.

### Available Fixes

#### Option 1: Upgrade Testcontainers (Recommended)
Update `pom.xml` property:
```xml
<testcontainers.version>1.21.4</testcontainers.version>
```
Or jump to the 2.x line:
```xml
<testcontainers.version>2.0.3</testcontainers.version>
```
- 1.21.4: Last 1.x release with Docker 29 compatibility
- 2.0.2+: First version with Docker API 1.44 default
- 2.0.3: Latest stable (December 2024)

**Note**: 2.x is a major version bump and may have breaking API changes. 1.21.4 is the safer upgrade path.

#### Option 2: docker-java.properties Workaround (Quick Fix)
Create `src/test/resources/docker-java.properties`:
```properties
api.version=1.44
```
This overrides the docker-java default without upgrading Testcontainers.

#### Option 3: Set DOCKER_API_VERSION in CI
In `.github/workflows/test.yml`:
```yaml
env:
  DOCKER_API_VERSION: "1.44"
```

#### Option 4: Pin Docker Version in CI (Not Recommended)
Use an older ubuntu runner or install a specific Docker version. Not recommended as it delays the inevitable upgrade.

### Version Compatibility Matrix Across Codebase

| Component | Current Version | Compatibility Notes |
|-----------|----------------|---------------------|
| Testcontainers | 1.19.3 | **BROKEN** - defaults to Docker API v1.32 |
| Spring Boot | 3.4.11 | Compatible with Testcontainers 1.21.x and 2.x |
| Java | 17 | Compatible with all Testcontainers versions |
| PostgreSQL image | 15-alpine | Compatible with all Testcontainers versions |
| Docker Compose file | version 3.8 | Deprecated but functional; remove `version` key |
| GitHub Actions runner | ubuntu-latest | Docker 29.0.0+ (requires API 1.44+) |

## Code References

- `pom.xml:20` - Testcontainers version property
- `pom.xml:64-80` - Testcontainers dependencies
- `pom.xml:89-97` - Testcontainers BOM
- `.github/workflows/test.yml:10` - `runs-on: ubuntu-latest`
- `docker-compose.dev.yml:1` - `version: '3.8'` (deprecated format)
- `src/test/java/.../integration/BankAccountIntegrationTest.java:40` - PostgreSQLContainer usage
- `src/test/java/.../integration/RecurringExpenseIntegrationTest.java:37` - PostgreSQLContainer usage
- `src/test/java/.../integration/BudgetIntegrationTest.java:46` - PostgreSQLContainer usage

## Architecture Documentation

All three integration test classes follow the same Testcontainers pattern:
```java
@Testcontainers
class XxxIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
}
```
This pattern is compatible with both Testcontainers 1.x and 2.x - no test code changes needed for the upgrade.

## Historical Context

- `2025-12-28-timestamp-date-handling.md` - Previous CI issue was timestamp precision differences between PostgreSQL (microseconds) and Java (nanoseconds). Resolved with `TestDateTimeMatchers.matchesTimestampIgnoringNanos()`.
- The current issue is infrastructure-level (Docker API version), not application-level.

## Related Issues

- [Testcontainers #11210](https://github.com/testcontainers/testcontainers-java/issues/11210) - "client version 1.32 is too old"
- [Testcontainers #11212](https://github.com/testcontainers/testcontainers-java/issues/11212) - Docker 29.0.0 environment detection
- [Testcontainers PR #11216](https://github.com/testcontainers/testcontainers-java/pull/11216) - Fix: set default API version to 1.44

## Open Questions

1. Should the project jump to Testcontainers 2.x or stay on 1.21.4? The 2.x line may have breaking changes worth evaluating.
2. Should `ubuntu-latest` be pinned to a specific version (e.g., `ubuntu-24.04`) to prevent future runner image surprises?
3. Should `docker-compose.dev.yml` remove the deprecated `version: '3.8'` key?
