# Repository Guidelines

## Project Structure & Module Organization

Cert Tool is an offline Java 17/JavaFX Maven multi-module application for inspecting and converting JKS and BCFKS keystores. Production code lives in each module's `src/main/java`; tests mirror packages under `src/test/java`.

- `domain/` contains immutable domain models and must not depend on JavaFX.
- `keystore-core/`, `certificate-analysis/`, `compliance-engine/`, `conversion/`, and `reporting/` contain the core workflows.
- `app/` is the JavaFX composition root, controllers, view models, and UI services.
- `platform/` provides OS-facing helpers; `test-fixtures/` generates non-production crypto fixtures.
- `docs/adr/` records architecture decisions; `cert_tool.md` is the authoritative product and security specification.

Keep crypto and compliance rules out of controllers. UI code must use the core abstractions rather than directly accessing keystores, certificate factories, or providers.

## Build, Test, and Development Commands

Use the Maven Wrapper with JDK 17:

```bash
./mvnw verify                         # Full build and quality gates
./mvnw test                           # Unit tests
./mvnw test -pl conversion            # One module's tests
./mvnw test -pl conversion -Dtest=PreflightTest#blocksInvalidPlan
./mvnw spotless:apply                 # Apply Java formatting
./mvnw spotless:check && ./mvnw checkstyle:check
```

`verify` is the required pre-merge command; it runs formatting, static analysis, coverage, dependency checks, and SBOM generation. Do not use skipped tests to claim a green build.

## Coding Style & Naming Conventions

Write Java 17 code and let Spotless with Palantir Java Format determine layout (four-space indentation and a 120-character maximum). Use `PascalCase` for types, `camelCase` for members, and `UPPER_SNAKE_CASE` for constants. Keep domain objects immutable and use typed error/result models instead of parsing exception text.

## Testing & Security

Use JUnit 5 with AssertJ; prefer fakes to Mockito, and reserve TestFX for critical UI flows. Name tests `*Test` and methods for the behavior under test. Generate fixtures locally in `test-fixtures/`; never add real keystores, certificates, passwords, or network-dependent tests.

Treat secrets as non-loggable and non-persistable: passwords stay in `char[]` and are cleared promptly; reports, logs, settings, and errors must never expose key material or full Base64 input. Conversion must preserve the source and use reload-verified atomic output.

## Commits & Pull Requests

Existing history uses short imperative subjects such as `fix runable` and `Update ConvertControllerTest.java`. Use a concise imperative summary; add a focused body when behavior, security, or module boundaries change. PRs should state the affected modules, validation performed (normally `./mvnw verify`), linked issue/spec section, and screenshots for UI changes. Call out security-sensitive changes explicitly.
