# ADR-0005: Testing strategy

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §13 mandates strict TDD and lists coverage areas that must be tested. Spec §1 names the tools: JUnit, AssertJ, Mockito (sparingly), TestFX (small set of critical UI tests only).

We must:
- Cover core cryptographic and rule logic with deterministic unit tests.
- Generate test keystores and certificates locally (no network, no real user material).
- Surface security regressions early (log scrubber, report scrubber, secret-in-config tests).
- Keep UI tests minimal because TestFX is inherently flaky.

## Decision

- **Test runner**: JUnit 5 (`junit-jupiter`). Vintage engine disabled.
- **Assertions**: AssertJ (`assertj-core`).
- **Mocking**: Mockito 5 (`mockito-core`, `mockito-junit-jupiter`) **only** when a hand-rolled fake is awkward. Default rule: prefer fakes; reach for Mockito in tests of `app` glue code where collaborators are JavaFX or `Task`-based.
- **UI**: TestFX (`testfx-junit5`). Used **only** for the seven flows enumerated in spec §13: theme switch, open file, Base64 paste dialog, alias selection shows details, run assessment, conversion wizard validation, password-cancel.
- **Fixture generation**: `test-fixtures` module exposes utility classes (e.g., `KeyStoreGenerator`, `CertificateGenerator`) that build keystores and certificates via BC's `X509v3CertificateBuilder` and `JcaContentSignerBuilder`. They are reused across modules; production code never depends on them.
- **Security tests** (first-class):
  - Log scrubber test — captures Logback output and asserts no password/private-key/secret-key/full-Base64 keystore appears.
  - Report scrubber test — runs each reporter and asserts the same.
  - Config scrubber test — writes a settings file and asserts no secrets persisted.
- **Coverage** (JaCoCo):
  - `domain`, `keystore-core`, `certificate-analysis`, `compliance-engine`, `conversion`: line ≥ 80%, branch ≥ 70%.
  - `reporting`: line ≥ 70% (branch lower).
  - `platform`, `app`: not enforced; UI/platform code is tested where deterministic.
- **Test execution in CI**: full `./mvnw verify`. No skipping.
- **Network in tests**: forbidden. The `enforcer-plugin` rule for `banned-dependencies` and a Surefire `<systemPropertyVariables>` block listing forbidden hosts are documented for Phase 1.

## Consequences

- **Positive**: Coverage gates catch silent dead code in security-critical paths.
- **Positive**: Fixture reuse avoids the most common source of "test data rots" in crypto code.
- **Negative**: Hand-rolled fakes are more code than Mockito one-liners. Mitigated by accepting that some tests are a few extra lines for much higher reliability.
- **Risk**: JaCoCo thresholds may be brittle when adding code. Always run `mvn verify` before claiming a phase is complete.

## Follow-ups

- Phase 1 introduces `LogCaptureExtension` (JUnit 5 extension that attaches a Logback `ListAppender`) and `ReportScrubber` utility.
- Phase 1 introduces `KeyStoreGenerator` with at least the cert types enumerated in spec §13.