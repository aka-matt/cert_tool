# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: Cert Tool

A production-quality offline desktop GUI tool for reading, parsing, inspecting, and converting JKS and BCFKS keystores (binary or Base64). Includes a static FIPS 140-2/140-3 compatibility assessment engine.

The full product specification is in `cert_tool.md`. Read it before making non-trivial changes — it is the source of truth for security rules, FIPS assessment semantics, error model, and module boundaries.

## Build and Test Commands

Use the Maven Wrapper (no system Maven required):

```bash
./mvnw verify              # Full build + all quality gates
./mvnw test                # Run unit tests only
./mvnw test -pl <module>   # Test a single module
./mvnw test -pl <module> -Dtest=<ClassName>#<methodName>   # Run a single test method
./mvnw spotless:check      # Formatting check
./mvnw spotless:apply      # Auto-format
./mvnw checkstyle:check    # Static analysis
./mvnw verify -DskipTests  # Compile + static checks only (do NOT use to claim a green build)
```

CI runs `./mvnw verify` and gates on Spotless, Checkstyle/SpotBugs, JaCoCo coverage, OWASP Dependency-Check, and CycloneDX SBOM generation. **Never bypass tests to make a build pass.**

## Tech Stack

- Java 17 LTS, JavaFX, AtlantaFX (theming)
- Maven multi-module, locked dependency versions (no dynamic ranges)
- Bouncy Castle + Bouncy Castle FIPS Provider (distinguish carefully — see Security below)
- JUnit, AssertJ, Mockito (only when truly needed), TestFX (small set of critical UI tests only)
- SLF4J + Logback, Jackson for rule configs and settings
- Do NOT introduce Spring, Jakarta EE, databases, or web services

## Module Layout

```
keystore-inspector-fx/
├── pom.xml                  (parent)
├── app/                     JavaFX Application, Views, Controllers/ViewModels, ThemeService, Dialogs, Composition Root
├── domain/                  Immutable domain objects, enums, value objects — NO JavaFX dependency
├── keystore-core/           Input detection, Base64 decoding, KeyStore loading, Alias enumeration, PasswordProvider, Provider selection, error classification
├── certificate-analysis/    X.509 parsing, extensions, fingerprints, key sizes, curves, chain checks
├── compliance-engine/       Profile, Rule, RuleContext, Finding, AssessmentReport, rule loading, FIPS assessment
├── conversion/              ConversionPlan, Preflight, Entry Copy, Alias Conflict Resolution, Atomic Writer, Verification
├── reporting/               JSON / HTML / Markdown reporters, versioned report schema
├── platform/                System theme detection, file ops, clipboard, recent files, settings
└── test-fixtures/           Generated test keystores and certificates (no real production material)
```

**Boundary rules:**
- UI layer must not touch `KeyStore`, `CertificateFactory`, or Providers directly.
- Controllers must not contain cryptographic business rules.
- Avoid global mutable singletons; wire dependencies in a Composition Root.
- Rules belong in `compliance-engine`, not in Controllers.

## Core Security Rules (non-negotiable)

These come from `cert_tool.md` §2 and must be enforced everywhere:

1. Never log: store password, key password, private key, secret key, full Base64 KeyStore, full KeyStore binary, or pasted sensitive input. Test that logs do not contain these.
2. Passwords are `char[]`; call `Arrays.fill()` to zero them as soon as possible.
3. Never write passwords to config, recent-files, crash reports, or exception messages.
4. Never create temp plaintext files unless the user explicitly asks.
5. Prefer in-memory Base64 decoding.
6. All output files use atomic write: write to temp in same dir → reload and verify → atomic move → delete temp on failure.
7. Conversion must never modify the source file.
8. Default 100 MB max input size (configurable in Settings).
9. Password fields are masked by default; no "remember password" feature.
10. Error messages must be useful but never leak key material.

When in doubt: do not log it, do not include it in the report, do not persist it.

## Input Model

Four user-visible forms: `{JKS, BCFKS} × {BINARY, BASE64}`. Internally model as two enums — `KeyStoreContainerType` and `ContentEncoding` — not four independent code paths.

Inputs come from: file chooser (binary or Base64 text), paste dialog, drag-and-drop, recent files (paths only — never passwords), clipboard.

Base64 parsing supports: standard, MIME (with newlines), leading/trailing whitespace, optional PEM-style BEGIN/END wrappers; produce explicit errors for illegal chars, truncation, empty input.

**Do not decide format by extension.** Auto-detect by trying safe loads; if "wrong password" vs "corrupted" is ambiguous, surface that ambiguity rather than guessing. When container type is uncertain, prompt the user.

## PasswordProvider

UI must depend on this abstraction, not on JavaFX dialogs directly:

```java
public interface PasswordProvider {
    char[] requestStorePassword(StorePasswordRequest request);
    char[] requestEntryPassword(EntryPasswordRequest request);
}
```

Tests must use a fake `PasswordProvider`. Support: store password, per-entry key password, store == key, TrustStore (no key password), user cancel, limited retries, skip-undeductible-private-key option.

## Error Model

Use `LoadFailureReason` and typed error objects (user-readable message, technical reason, retryable, needs-password, original exception for controlled diagnostics, non-sensitive context). Do not parse Provider exception text as the sole signal — when password-vs-corruption is indeterminate, say "indeterminate."

## FIPS Assessment Semantics

Function names must be **FIPS Compatibility Assessment** / **FIPS Readiness Assessment** (Chinese: FIPS 兼容性评估). Never claim **FIPS Certification**, **Official FIPS Validation**, **NIST Certified**, or **正式认证结论**.

Always show the disclaimer: this tool performs static compatibility assessment; FIPS 140-2/140-3 validation applies to specific cryptographic modules, versions, operating modes, and operational environments; the report is not a formal certification conclusion by NIST, CMVP, an accredited lab, or an auditor.

Use `AssessmentStatus { PASS, WARNING, FAIL, NOT_ASSESSABLE, NOT_APPLICABLE }` and `AssessmentFinding(ruleId, title, status, severity, summary, evidence, remediation, references)`. Never reduce to a single green/red icon — always show evidence, reason, rule id, and remediation.

Distinguish carefully: regular Bouncy Castle Provider vs Bouncy Castle FIPS Provider. If Approved-Only Mode cannot be confirmed reliably, return `NOT_ASSESSABLE` or `WARNING` — never guess.

Built-in profiles: `FIPS_140_2_LEGACY_ASSESSMENT`, `FIPS_140_3_ASSESSMENT`, `CUSTOM`. Rules are versioned JSON; users may import custom rule sets.

## Conversion Wizard (5 steps)

1. Source (format, encoding, path/in-memory, store password, detection result)
2. Contents (alias, type, include, chain length, key algorithm, risk status — let user pick entries)
3. Target (container, encoding, path, store password, Base64 line width, optional header/footer wrappers, alias conflict policy, overwrite policy)
4. Compatibility preflight (unconvertible entries, re-prompt entries, format-unsupported entries, FIPS warnings, data loss risk) — must be visually prominent
5. Execute and verify (write temp, reload, compare aliases/counts/fingerprints/chains, atomic replace, generate report)

Binary ↔ Base64 of the same format must not uselessly regenerate keystore bytes; re-encoding/decoding alone is fine but the decoded keystore must still be verified. BCFKS → JKS downgrades must be flagged when they weaken security attributes or fail the selected Profile.

## Export

- Single cert PEM, single cert DER, full chain PEM
- Copy SHA-256 fingerprint, copy cert summary
- Report as JSON (versioned schema), HTML, Markdown
- **Default: private-key export disabled.** No plaintext private-key export in this version.
- Reports must not contain passwords, private keys, or secret-key material.

## UI

AtlantaFX with Light / Dark / System themes. Use `ThemeService`; do not modify theme inside Controllers. Detect Windows/macOS/Linux system theme where supported, fall back to configured theme, listen for system changes where possible, persist user choice, decouple from business logic.

Main window: `BorderPane` with MenuBar + Toolbar (top), TreeView/grouped list (left), entry/cert details (center), collapsible Compliance Inspector (right), status bar (bottom).

Left nav groups: Private Keys, Trusted Certificates, Secret Keys, Unreadable Entries. Each node shows alias, type icon, PASS/WARNING/FAIL, expiry hint.

Center tabs: Overview, Certificate, Chain, Extensions, PEM, Findings. Overview uses card + two-column layout. PEM is monospace, read-only, with a copy button.

Compliance page: Profile selector, Run Assessment, Export Report; summary card with PASS/WARNING/FAIL counts; filterable Findings table; evidence + remediation on the right.

Convert page: stepped Wizard, not one giant form.

Runtime page: JVM, vendor, OS, providers list, provider versions, BCFIPS status, Approved Mode status, default KeyStore type, current rule profile.

All long operations go through JavaFX `Task` or a background Executor. Never block the JavaFX Application Thread. Support progress indication, cancellation, success notifications, copyable error details, empty states, keyboard navigation, accessibility text, HiDPI, persisted window bounds and divider positions.

## Testing (TDD)

Strict TDD: failing test → minimal impl → green → refactor → next item.

Core logic must be covered by plain unit tests — do not lean on fragile UI automation.

Required coverage:
- Base64: standard, MIME newlines, whitespace, BEGIN/END wrappers, illegal chars, truncation, empty, oversize
- KeyStore load: right/wrong password for JKS and BCFKS, TrustStore, PrivateKeyEntry, SecretKeyEntry, multi-alias, distinct entry passwords, corrupted file, wrong-format hint, missing Provider
- Certificate analysis fixtures (generated locally, never external): RSA 2048/3072, weak RSA, EC, SHA-1-signed, SHA-256-signed, expired, not-yet-valid, self-signed, root/intermediate/leaf chains, broken chain signature, broken Basic Constraints, unrecognized critical extension
- Rule engine: each rule in isolation, profile switching, unknown algorithms, missing runtime evidence, status aggregation, disclaimer presence, schema validation
- Conversion: JKS↔BCFKS, Binary↔Base64, alias preservation, chain preservation, distinct entry passwords, alias conflicts, unsupported entries, write failure, verification failure, temp file cleanup, source file unchanged
- Security tests: no password in logs, no private key in logs, no full Base64 in errors, no password in config, no secrets in reports
- TestFX: only theme switch, open-file flow, Base64 paste dialog, select-alias-shows-details, run assessment, wizard validation, password-cancel — nothing else

Test certs must be generated in-test or stored in dedicated non-productive test resources. No network. No real user keystores.

## Implementation Phases

Work in this order — do not jump to UI:

0. Repo inspection + ADR for dependencies, providers, module design
1. Domain model + test fixtures + security test harness
2. Input + load (Binary/Base64, JKS/BCFKS, PasswordProvider, error classification, auto-detection)
3. Certificate parsing
4. Rule engine + default rules + disclaimer
5. Conversion engine + atomic write + reload verification + conversion report
6. Reporters (JSON/HTML/Markdown)
7. JavaFX UI

After each phase: run the relevant tests → run full `./mvnw verify` → fix failures → update docs.

Before modifying code, output (per `cert_tool.md` §18): understanding of the requirement, ambiguities/risks found, proposed module architecture, provider strategy, FIPS judgment boundaries, test strategy, phased plan, expected file list.

## Completion Criteria

A feature is not done until ALL of: JKS and BCFKS both loadable; Binary and Base64 both readable; all four forms convertible; multi-alias + distinct entry passwords handled; full cert and chain details displayed; FIPS assessment uses the rule engine; report explicitly distinguishes static assessment from formal certification; Light/Dark/System themes work; long ops do not block UI; logs and reports leak no secrets; conversions are reload-verified; tests pass; `./mvnw verify` passes; docs complete.

Do not claim a command was run if it was not. Do not claim formal FIPS certification.