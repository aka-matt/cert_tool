# ADR-0010: Module boundaries and dependency direction

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §11 mandates strict boundaries: the UI layer must not directly operate on `KeyStore`, `CertificateFactory`, or `Provider`; Controllers must not contain cryptographic business rules; no global mutable singletons. Dependency direction must be testable.

Without enforcement, Java module-system-style discipline has to be done by review, which is unreliable.

## Decision

Dependency direction (downward only):

```
app  ──▶ platform
       ▶ keystore-core ──▶ domain
       ▶ certificate-analysis ──▶ domain
       ▶ compliance-engine ──▶ {domain, keystore-core, certificate-analysis}
       ▶ conversion ──▶ {domain, keystore-core, certificate-analysis, platform}
       ▶ reporting ──▶ {domain, compliance-engine, keystore-core, certificate-analysis, conversion}
       ▶ platform

test-fixtures ──▶ (no internal dependencies; pure BC)
```

Specifically forbidden edges:
- `domain` → anything internal.
- `keystore-core` → `certificate-analysis` (or vice versa) — they share types via `domain` only.
- `app` → `org.bouncycastle.*` (must go through `keystore-core`/`certificate-analysis`).
- `compliance-engine` → `conversion` or `reporting`.
- `reporting` → `app`.

Enforcement:
- A `dependencyConvergence` rule and explicit `<exclusions>` in each child POM.
- A `forbidden-apis` rule (via `dependency-check` or a custom Enforcer rule) that fails the build if `app` references `java.security.KeyStore`, `java.security.cert.CertificateFactory`, or `org.bouncycastle.*` directly.
- ArchUnit tests in `app` (run as part of `mvn verify`) that load each module's bytecode and assert the forbidden edges do not appear.
- SpotBugs `find-sec-bugs` configured to flag direct `KeyStore` use outside `keystore-core`.

## Consequences

- **Positive**: Code review has a mechanical check behind it.
- **Positive**: Test fixtures can be reused without polluting production classes.
- **Negative**: ArchUnit adds a small build cost. Acceptable for the boundary guarantee it provides.
- **Risk**: ArchUnit tests can become noisy during refactors. Keep the rule set minimal: only forbidden edges, not stylistic rules.

## Follow-ups

- During Phase 7, add ArchUnit tests verifying the forbidden edges listed above.
- A "Composition Root" pattern in `app/CertToolApp.java` wires all dependencies via constructor injection (no static `ServiceLocator`).