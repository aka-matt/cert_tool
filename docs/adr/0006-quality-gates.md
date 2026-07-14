# ADR-0006: Quality gates

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §14 mandates Spotless, Checkstyle (or equivalent), SpotBugs, JaCoCo, OWASP Dependency-Check, CycloneDX SBOM, and a CI configuration that runs `./mvnw verify`. We want gates that catch regressions quickly but do not generate noise that gets ignored.

## Decision

Configured in the parent POM and inherited by all modules.

- **Spotless** with `palantir-java-format` (5.16.x). `spotless:check` runs in `verify`; `spotless:apply` is the auto-fixer. Failure on any file.
- **Checkstyle** with `checkstyle/maven-checkstyle-plugin` using the `google_checks.xml` baseline, customized in `config/checkstyle/checkstyle.xml` to allow our line-length ceiling (120) and project-specific suppressions. Failure on `Error` level.
- **SpotBugs** 4.8.x with `find-sec-bugs` and `maven-spotbugs-plugin`. Goal: zero `BugInstance` at `priority` 1–2.
- **JaCoCo** per ADR-0005 thresholds. Aggregated report in `target/site/jacoco-aggregate/index.html`.
- **OWASP Dependency-Check** 9.x with `dependency-check-maven`. CVSS threshold `failBuildOnCVSS=7`. Suppressions live in `config/owasp/suppressions.xml` with comments explaining each entry.
- **CycloneDX** 2.x SBOM generator bound to `package` phase. Both `cyclonedx-maven-plugin` outputs `target/sbom.cdx.json` per module.
- **Maven Enforcer Plugin**:
  - Ban `commons-lang:commons-lang` (use `commons-lang3`).
  - Require Java 17 (`requireJavaVersion`).
  - Require Maven 3.8+ (`requireMavenVersion`).
  - Ban duplicate dependency declarations across modules.
  - Fail on SNAPSHOT dependencies in non-test scope.
- **GitHub Actions** (`.github/workflows/ci.yml`): single job that runs `./mvnw -B verify`. Caches `~/.m2/repository`. Uploads JaCoCo, SpotBugs, and CycloneDX reports as artifacts.

## Consequences

- **Positive**: One command gates every change; no "works on my machine" drift.
- **Positive**: SBOM feeds downstream supply-chain tooling.
- **Negative**: Spotless + Checkstyle both run; we accept the redundancy because they catch different classes of issue.
- **Risk**: OWASP false positives on transitive deps. The suppressions file is the only acceptable mitigation; every entry must have a comment.
- **Risk**: JaCoCo gates can flip red during refactors. Phases that materially change coverage run `./mvnw verify` before claiming completion (per spec §17).

## Follow-ups

- Add `enforcer/banned-dependencies` rules once the dependency list stabilizes in Phase 1.
- Tune the OWASP CVSS threshold after Phase 1 produces a real first report.