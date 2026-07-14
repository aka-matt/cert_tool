# ADR-0007: Logging strategy

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §2 prohibits logging passwords, private keys, secret keys, full Base64 keystores, full keystore binaries, or pasted sensitive input. Spec §1 names SLF4J and Logback.

We need logging that:
- Defaults to **off** for the most sensitive operations and only emits at INFO for diagnostic events that cannot include key material.
- Supports a "security test" mode that scans emitted log lines against known sensitive shapes (password regex, PEM blocks, full-Base64 blobs).
- Has a single shared configuration file.

## Decision

- **API**: SLF4J 2.x.
- **Implementation**: Logback 1.4.x with `logback-classic`.
- **Configuration**: A single `logback.xml` under `app/src/main/resources`. Profile-aware: production default emits INFO to console and a rolling file under the OS-conventional user-config dir; `dev` profile adds DEBUG; `security-test` profile routes through a `ListAppender<ILoggingEvent>` that the test harness reads.
- **Log scrubbing — proactive**:
  - Domain types for sensitive material (`Password`, `PrivateKeyHolder`) implement `toString()` that returns a fixed string such as `"<redacted>"` rather than the actual bytes. SLF4J will never accidentally print a secret.
  - Logger calls that take a structured value use a wrapper: `Secrets.safe(password)` returns either `null` or a `toString()` that prints length only.
- **Log scrubbing — reactive**:
  - A custom Logback `Filter` (`SensitiveDataFilter`) inspects every log message and rejects (or rewrites to a placeholder) any line matching patterns for: PEM `-----BEGIN ... PRIVATE KEY-----`, base64 blobs ≥ 256 chars, `password=...` style key=value pairs, known certificate chain tokens.
- **What is logged at INFO**: file open (path only, never bytes), detection outcome (container type, encoding, provider), assessment summary (count of findings by status, never the content), conversion start/end (paths only), UI navigation.
- **What is never logged at any level**: passwords, key bytes, full keystore bytes, full Base64 keystore blobs.
- **Tests**: `LogCaptureExtension` (Phase 1) attaches the `ListAppender` and provides assertions like `assertNoSensitiveContent(logs)`.

## Consequences

- **Positive**: Two layers of defense — domain type redaction plus reactive filter.
- **Positive**: Tests assert the absence of secrets in logs, not just the presence of expected messages.
- **Negative**: The reactive filter is a heuristic; a determined adversary could craft a secret that slips past. The domain-type redaction is the real guarantee.
- **Risk**: Performance overhead of the reactive filter. Mitigated by anchoring on a small set of high-confidence patterns only.

## Follow-ups

- Phase 1 introduces `Password`, `PrivateKeyHolder`, and `Base64Blob` value types with redacted `toString()`.
- Phase 1 introduces the Logback filter and the JUnit extension.