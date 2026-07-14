# ADR-0008: Settings storage

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §15 enumerates exactly what may be persisted and explicitly forbids persisting any secret. Spec §15 also requires a versioned schema and graceful fallback to defaults on a corrupted file.

The settings file lives somewhere outside the application bundle so users can wipe state without reinstalling, and so multiple installs do not collide.

## Decision

- **Location**: OS-conventional user-config directory via Java `Preferences` API, with file fallback.
  - Linux: `${XDG_CONFIG_HOME:-~/.config}/cert-tool/settings.json`
  - macOS: `~/Library/Application Support/cert-tool/settings.json`
  - Windows: `%APPDATA%\cert-tool\settings.json`
- **Format**: JSON, written by Jackson. UTF-8, LF newlines, no BOM, two-space indent.
- **Schema**: versioned (`"schemaVersion": 1`). On read, unknown fields are dropped; missing fields take defaults. On a JSON parse error or schema mismatch, the file is renamed `settings.json.broken-<timestamp>` and defaults are used.
- **Serializer hardening**: a Jackson `Module` registers mix-ins on sensitive types so they never serialize to JSON (defense in depth — the type system already forbids it, but the serializer is a second line).
- **Persisted keys** (exactly the set in spec §15):
  - `theme` (`LIGHT` | `DARK` | `SYSTEM`)
  - `systemThemeFallback` (`LIGHT` | `DARK`)
  - `windowBounds` (`{x, y, width, height}`)
  - `dividerPositions` (map of named divider to fraction)
  - `recentFilePaths` (list of strings, paths only)
  - `defaultReportDirectory` (string)
  - `base64LineWidth` (integer, 64 or 76)
  - `defaultAssessmentProfile` (profile id)
  - `maxInputSizeBytes` (long, default 100 MiB)
  - `expirationWarningDays` (integer, default 30)
  - `lastExportFormat` (`PEM` | `DER` | `JSON` | `HTML` | `MARKDOWN`)
- **Never persisted**: passwords, key material, recent file *contents*, Base64 blobs, anything from `KeyStore`, `Certificate`, `PrivateKey`, `SecretKey`.
- **Tests**: `SettingsStoreTest` round-trips each key, asserts absence of any secret-shape substring after a fake save of every persisted key, and asserts graceful fallback on corrupted input.

## Consequences

- **Positive**: A clean wipe is one file deletion.
- **Positive**: Schema versioning means future versions can read old files (or rename and start fresh, with the user warned via the UI).
- **Negative**: Corrupted settings can be silently lost if the user never sees the rename. The UI shows a one-line toast: "Settings reset (corrupted file moved to settings.json.broken-...)."
- **Risk**: Recent-file paths can leak directory structure. Acceptable per spec §15; documented in `THREAT-MODEL.md`.

## Follow-ups

- The `platform` module owns the storage service; `app` consumes it via DI.
- A separate ADR (`0011-settings-migration.md`, future) handles future schema bumps.