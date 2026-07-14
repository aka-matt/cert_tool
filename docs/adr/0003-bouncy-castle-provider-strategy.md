# ADR-0003: Bouncy Castle provider strategy

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

The tool must load JKS and BCFKS keystores. JKS is built into the JRE; BCFKS is supplied by the Bouncy Castle Provider (`org.bouncycastle:bcprov-jdk18on`) or the Bouncy Castle FIPS Provider (`org.bouncycastle:bc-fips`). The FIPS Provider has different algorithms, a different provider name, and importantly a different **license**: production distribution of BC-FIPS requires a commercial License Agreement with the Legion of the Bouncy Castle.

Spec §6/§7 require us to distinguish BC from BCFIPS at runtime, surface Approved-Only Mode status, and never claim "FIPS certified."

## Decision

- **Default provider**: Bouncy Castle non-FIPS (`bcprov-jdk18on`, Apache-2.0). Loaded eagerly at application start via a `ProviderRegistry`. Used for general JKS parsing, BCFKS parsing outside FIPS-mode, certificate parsing, and signature verification.
- **FIPS Provider**: Bouncy Castle FIPS (`bc-fips`, Legion commercial license). **Loaded only when**:
  1. The user explicitly enables "FIPS Mode" in Settings, AND
  2. The BC-FIPS artifact is present on the classpath.
- **Approved-Only Mode detection**: read `BouncyCastleFipsProvider.isApprovedOnlyMode()` reflectively if available; if the call fails or the property is absent, return `NOT_ASSESSABLE` for any rule that depends on it. **Never infer** Approved-Only from absence of evidence.
- **Provider identity in reports**: every report records `providerName`, `providerVersion`, and whether it is `BC` or `BCFIPS`.
- **Class loading**: `ProviderRegistry` resolves providers by name; tests can swap them with fakes. We do **not** use `Security.removeProvider`/`insertProviderAt` on global state from inside rule execution — any provider interaction goes through the registry.
- **Dependency hygiene**: BC-FIPS is marked `<optional>true</optional>` in the parent BOM so the jar is only packaged when the user opts in (future work). For v1, the optional status is documentation only; we do not ship the BCFIPS jar by default.

## Consequences

- **Positive**: Default distribution stays Apache-2.0 clean.
- **Positive**: Approved-Only detection is conservative — we never claim what we cannot verify.
- **Negative**: Users who want true FIPS-provider runs must arrange the BC-FIPS jar themselves. The README documents this.
- **Risk**: Provider registration order affects which provider wins for `KeyStore.getInstance("BCFKS")`. We always specify the provider name explicitly; no bare `KeyStore.getInstance(type)`.
- **Risk**: BC-FIPS API differs slightly from BC (notably around `BcContentSignerBuilder` vs `ContentSignerBuilder`). Code that targets both must use only the common subset, or be gated by provider type.

## Follow-ups

- Document BC-FIPS install in `README.md` and `SECURITY.md`.
- Track BC API drift across minor versions — pin both `bcprov-jdk18on` and `bc-fips` versions in the parent BOM.