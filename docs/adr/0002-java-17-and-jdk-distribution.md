# ADR-0002: Java 17 and JDK distribution

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §1 mandates Java 17 LTS. JavaFX 17 (and the matching AtlantaFX) is the most mature line for Java 17. JavaFX dropped out of the JDK starting with JDK 11, so we must explicitly pull the OpenJFX runtime artifacts. The build must work on Linux, macOS, and Windows without environment-specific glue.

We need a stable, license-clean JDK reference for development, CI, and contributors.

## Decision

- **Language level**: Java 17 (`maven.compiler.release = 17`).
- **Source/target compatibility**: 17.
- **Build JDK**: Eclipse Temurin 17 (latest LTS). CI uses Temurin 17; the README instructs contributors to install Temurin 17 or any other Java 17 distribution.
- **Runtime**: Java 17 LTS. The `app` module's executable jar documents the minimum as Java 17.
- **JavaFX**: 17.0.x, pulled from the OpenJFX Maven coordinates (`org.openjfx:javafx-controls`, `javafx-graphics`, `javafx-base`, `javafx-fxml`, `javafx-web` for future report preview). Platform-classified natives handled by OpenJFX's classifier artifacts.
- **Module path**: The `app` module uses classpath (not JPMS) to avoid JavaFX module-path friction and to keep executable-jar packaging simple. This is consistent with AtlantaFX examples.

## Consequences

- **Positive**: Broad compatibility with corporate Java 17 baselines.
- **Positive**: OpenJFX Maven artifacts work on every OS without manual `PATH`/`--module-path` tricks.
- **Negative**: No JPMS enforcement of module boundaries. ADR-0010 lays out review-level enforcement instead.
- **Risk**: Newer Java 21+ features may tempt contributors. The parent POM fails the build if `--release` is overridden; PR review must catch feature use beyond Java 17.

## Follow-ups

- Set up `enforcer-plugin` rule banning Java 21+ APIs (e.g., via `signature-check` or `banned-dependencies`).
- Document the Temurin install in `README.md`.