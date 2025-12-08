## Description

Maven and Gradle builds fail in Claude Code Web's cloud environment when attempting to download dependencies from Maven Central. This occurs despite the [documentation](https://code.claude.com/docs/en/claude-code-on-the-web) explicitly listing "JVM: Maven Central, Gradle services" as allowed domains under the package managers allowlist.

## Environment

- Platform: Claude Code Web (cloud sandbox)
- Project type: Spring Boot 3.4.x with Maven
- Network setting: "Full access" enabled (also tried "Limited")

## Error Message

```
Downloading from central: https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/3.4.11/spring-boot-starter-parent-3.4.11.pom
[FATAL] Non-resolvable parent POM for com.template:spring-boot-rest-api-template:1.0.0-SNAPSHOT:
The following artifacts could not be resolved: org.springframework.boot:spring-boot-starter-parent:pom:3.4.11 (absent):
Could not transfer artifact org.springframework.boot:spring-boot-starter-parent:pom:3.4.11 from/to central (https://repo.maven.apache.org/maven2):
repo.maven.apache.org: Temporary failure in name resolution
```

## Root Cause Analysis

Based on investigation (see [this LinkedIn post](https://www.linkedin.com/pulse/fixing-maven-build-issues-claude-code-web-ccw-tarun-lalwani-8n7oc)):

1. **Maven doesn't respect environment-level proxy variables** - Unlike tools like `curl` or `npm`, Java's `mvn` command doesn't automatically honor `HTTP_PROXY`/`HTTPS_PROXY` environment variables
2. **The cloud environment routes traffic through a proxy** - Claude Code Web's sandbox uses an outbound proxy for network access
3. **Maven needs explicit proxy configuration** - Maven requires proxy settings in `settings.xml`, but since the proxy address changes each session, static configuration doesn't work

## Expected Behavior

Maven should be able to download dependencies from `repo.maven.apache.org` (Maven Central) as documented in the allowlist.

## Similar Issue - Now Fixed

This is similar to [#10307](https://github.com/anthropics/claude-code/issues/10307) where crates.io (Rust) was blocked despite being documented as allowed. That issue was fixed by adding domains to the NO_PROXY environment variable.

## Potential Solutions

1. **Add Maven Central domains to NO_PROXY** - Similar to the crates.io fix, add `repo.maven.apache.org`, `*.maven.apache.org`, `repo1.maven.org` to the NO_PROXY environment variable
2. **Pre-configure Maven settings.xml** - Generate Maven proxy configuration dynamically at session start using detected proxy settings
3. **Ensure DNS resolution works** - The error suggests DNS resolution is failing entirely, which may indicate the domain isn't being routed through the proxy correctly

## Affected Languages/Package Managers

This issue likely affects:
- **Java** - Maven, Gradle
- Potentially other JVM languages (Kotlin, Scala) using Maven Central

## Additional Context

- Multiple users have reported this issue (see [Reddit thread](https://www.reddit.com/r/ClaudeAI/comments/1oqvajp/claude_code_for_web_cannot_pull_in_dependencies/))
- A workaround exists using session hooks to dynamically generate `settings.xml`, but this shouldn't be necessary given the documentation claims Maven Central is allowed
- The issue completely blocks Java/Maven development in Claude Code Web

## Reproduction Steps

1. Open any Java/Maven project in Claude Code Web
2. Run `./mvnw test` or `./mvnw compile`
3. Observe DNS resolution failure for `repo.maven.apache.org`
