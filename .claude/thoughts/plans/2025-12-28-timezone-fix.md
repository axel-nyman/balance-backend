# Timezone Fix Implementation Plan

## Overview

Configure the JVM to use Stockholm timezone (`Europe/Stockholm`) to fix timestamp discrepancies between frontend and backend. Currently, the backend uses UTC while the frontend operates in Stockholm time, causing a 1-hour offset that breaks balance update validation.

## Current State Analysis

- **Issue:** Account created at 08:45 Stockholm time shows as 07:45 in the API response
- **Root Cause:** JPA Auditing uses `LocalDateTime.now()` which uses the JVM's default timezone (UTC in Docker)
- **Impact:** Balance updates with Stockholm timestamps are rejected as "future dates" because `LocalDateTime.now()` returns UTC time

### Key Files:
- `Dockerfile:38` - ENTRYPOINT runs `java -jar app.jar` without timezone config
- `DomainService.java:145` - Future date validation uses `LocalDateTime.now()`
- All entities use `@CreatedDate`/`@LastModifiedDate` which rely on JVM timezone

## Desired End State

- All timestamps stored and compared in Stockholm time
- Balance updates with Stockholm timestamps work correctly
- No "future date" errors when submitting current Stockholm time

## What We're NOT Doing

- Not migrating to `ZonedDateTime` or `Instant` (overkill for personal app)
- Not changing frontend behavior
- Not modifying database schema
- Not adding timezone info to API responses

## Implementation Approach

Add `-Duser.timezone=Europe/Stockholm` JVM argument to the Docker ENTRYPOINT.

## Phase 1: Configure JVM Timezone

### Overview
Single change to Dockerfile to set Stockholm timezone for the JVM.

### Changes Required:

#### 1. Dockerfile
**File**: `Dockerfile`
**Changes**: Add timezone JVM argument to ENTRYPOINT

```dockerfile
ENTRYPOINT ["java", "-Duser.timezone=Europe/Stockholm", "-jar", "app.jar"]
```

### Success Criteria:

#### Automated Verification:
- [ ] Docker image builds successfully: `docker build -t balance-backend .`
- [ ] All tests pass: `./mvnw test`

#### Manual Verification:
- [ ] Create a new bank account and verify `createdAt` shows correct Stockholm time
- [ ] Create a balance update with current Stockholm time - should succeed without "future date" error
- [ ] Verify timestamps in API responses match your local Stockholm time

## Phase 2 (Optional): Local Development

If you also run the app locally with Maven during development:

### Changes Required:

#### 1. Maven Run Command
When running locally, use:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.jvmArguments="-Duser.timezone=Europe/Stockholm"
```

Or add to `.mvn/jvm.config` (create if doesn't exist):
```
-Duser.timezone=Europe/Stockholm
```

## Testing Strategy

### Manual Testing Steps:
1. Rebuild and redeploy Docker container on Raspberry Pi
2. Create a new bank account via Swagger/frontend
3. Verify `createdAt` timestamp matches current Stockholm time
4. Attempt a balance update with current Stockholm time
5. Confirm no "future date" error and update succeeds

## Migration Notes

- Existing timestamps in the database will remain as they were stored (in UTC)
- New timestamps will be in Stockholm time
- This may cause a 1-hour discontinuity in existing balance history data
- For a personal app, this is acceptable - the data will self-correct over time

## References

- Root cause analysis: JPA auditing uses `LocalDateTime.now()` with JVM timezone
- Affected validation: `DomainService.java:145`
