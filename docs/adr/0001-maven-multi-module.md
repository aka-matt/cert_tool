# ADR-0001: Maven multi-module layout

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

The spec (`cert_tool.md` §11) recommends nine Maven modules with strict separation between UI (`app`), domain logic, and platform integration. The tool mixes cryptographic primitives, GUI code, and disk I/O for sensitive material. Without module boundaries, business rules will leak into Controllers and cryptographic APIs will leak into the view layer, breaking testability and reviewability.

We must also produce a runnable application from a single `mvn verify` invocation and gate CI on the same command (spec §14).

## Decision

Use Apache Maven 3.9.x as the build tool. Project layout matches spec §11 exactly:

```
keystore-inspector-fx/        (parent pom, packaging=pom)
├── app/                       JavaFX Application + Controllers/ViewModels
├── domain/                    Pure immutable types — NO JavaFX
├── keystore-core/             Input detection, Base64, KeyStore load, PasswordProvider
├── certificate-analysis/      X.509 parsing, extensions, fingerprints, chains
├── compliance-engine/         Rule engine, Profiles, Findings
├── conversion/                ConversionPlan, Atomic Writer, Verification
├── reporting/                 JSON / HTML / Markdown reporters
├── platform/                  OS theme detection, file ops, clipboard, recent files, settings
└── test-fixtures/             Generated cert/keystore test material
```

Dependencies flow strictly downward: `app → {compliance-engine, conversion, reporting, certificate-analysis, keystore-core, platform, domain}`. `domain` depends on nothing internal.

The Maven Wrapper (`./mvnw`) is the canonical entry point. The wrapper distribution is pinned in `.mvn/wrapper/maven-wrapper.properties`.

The application ships as an executable jar assembled by the `app` module's `shade`/`assembly` configuration; `jpackage` installers are explicitly **out of scope for v1**.

## Consequences

- **Positive**: Fast incremental builds; clear ownership of code per module; `app` cannot reach `KeyStore`/`CertificateFactory` directly because those types live in `keystore-core`/`certificate-analysis`, which the view may not depend on.
- **Positive**: Test fixtures isolated in their own module so production classes cannot accidentally depend on test code.
- **Negative**: Nine POMs to maintain. Mitigated by `<dependencyManagement>` in the parent and explicit property blocks.
- **Risk**: Developers may add cross-module dependencies to "fix" something quickly. Code review must check `mvn dependency:tree` for upward edges.

## Follow-ups

- Parent POM owns: Java version, dependency versions, plugin versions, Spotless/Checkstyle/SpotBugs/JaCoCo/OWASP/CycloneDX configuration, distribution management for `org.certtool` groupId.
- Group ID: `io.github.cert-tool` (TBD pending repo ownership).
- Version: `0.1.0-SNAPSHOT` until Phase 7 completes.