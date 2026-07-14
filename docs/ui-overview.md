# Cert Tool — UI overview

This document describes the JavaFX UI built in Phase 7. For the authoritative spec, see
`cert_tool.md`. For the build/test commands, see `CLAUDE.md`.

## Entry point

`io.github.certtool.app.App` extends `javafx.application.Application`. The `start(Stage)` method:

1. Builds an `AppComposition` (the composition root) via `defaultComposition()`.
2. Hands the composition + stage to `MainShellController#show`.
3. Builds the dialog-backed `JavaFxPasswordProvider` and pushes it into the composition
   (it needs an FX owner, so this happens after `show`).
4. Registers a close handler that persists window bounds via `SettingsService`.

`App#stop` shuts down the background `ExecutorService`.

## Architecture

```
+----------------------+
| App                  |
+----------------------+
        |
        v
+----------------------+
| AppComposition       | composition root — wires settings, theme, password, loader,
+----------------------+ controllers, view-models, executor
        |
        v
+----------------------+
| MainShellController  | BorderPane: MenuBar / Toolbar / Tabs / Status bar
+----------------------+
   |        |       |        |
   v        v       v        v
Inspect  Compliance Convert Runtime       (each owns a Node, binds to its view-model)
```

View-models expose JavaFX observable properties (`ObjectProperty`, `StringProperty`,
`ObservableList`). Views only bind; controllers mutate state via
controller methods (e.g., `InspectController.onLoadResult`).

## Tasks (background work)

`io.github.certtool.app.task` provides FX `Task` wrappers:

| Task                  | Result type            |
|-----------------------|------------------------|
| LoadKeyStoreTask      | `KeyStoreLoadResult`   |
| AssessmentTask        | `AssessmentReport`     |
| ConvertTask           | `ConversionResult`     |
| ExportReportTask      | `Path`                 |

All four use a `tryMessage(...)` defensive helper that swallows
`IllegalStateException` from `updateMessage(...)` when the FX toolkit is not
initialised — this lets headless tests drive `call()` directly. Production code
runs the tasks on the composition's background executor and binds
`stateProperty`, `messageProperty`, and `exceptionProperty` to the UI.

## Views

Each view binds to a view-model owned by the composition:

| Tab        | View-model        | Notes                                          |
|------------|-------------------|------------------------------------------------|
| Inspect    | InspectViewModel  | Left nav groups entries; tabs: Overview/PEM    |
| Compliance | ComplianceViewModel | Profile picker + Findings table + evidence  |
| Convert    | ConvertViewModel  | 5-step wizard                                  |
| Runtime    | RuntimeViewModel  | JVM/OS info, providers, BCFIPS detection       |

The current implementation provides the wiring, the TabPane, and the bindings; the
rich certificate visualisation and the full 5-step wizard sit in follow-up work.

## Theme service

`ThemeService` (in `app.theme`) is the only thing views talk to about colour. The
production implementation is `AtlantaFxThemeService` (Light / Dark / System via
`ThemeMode`). System-mode reads the OS preference (Windows / macOS / Linux) and
re-evaluates on change. The service persists the chosen mode via
`SettingsService`.

`AtlantaFxThemeService.applyToScene(Scene, ThemeMode)` is the one-shot convenience
used by `App` and tests; runtime theme changes go through `setMode(...)` which
calls the registered `ThemeApplier`.

## Password provider

`PasswordProvider` is the seam the loader uses; production wires `JavaFxPasswordProvider`,
which is built once the FX stage is showing and anchored to that stage. The dialog
returns `char[]` or `null` (cancel). The factory seam `BiFunction<String,String,char[]>`
makes it testable without booting JavaFX.

## Settings

`SettingsService` reads/writes `~/.cert-tool/settings.json` atomically (write-temp + move).
The service contains a defensive guard list of forbidden substrings (password markers,
PEM private-key markers, FIPS-certification phrasing, Chinese formal-certification phrasing)
that mirror `SanitizationGuard`. A regression here would store a secret to disk.

## Security

In addition to the keystore-core / conversion security tests, the `app` module
adds:

- `SettingsNoLeakTest` — persisted settings JSON contains no passwords, no PEM
  markers, no certification claims.
- `LoggerNoSecretTest` — drives a real conversion through `KeystoreConversion.execute`
  and asserts the captured logger output contains no password / no JKS bytes.

## Testing

The JavaFX tests are gated behind `-Dcert.tool.testfx=true` (see
`ThemeSwitchSmokeTest`). CI runs `./mvnw verify` for the full quality gate. The
production code path is verified end-to-end by the task + controller tests.

## Layered boundaries (recap)

- UI layer never touches `KeyStore`, `CertificateFactory`, or providers directly.
- Controllers hold logic for view state; rules live in `compliance-engine`.
- Tasks are the only thing that produces observables updated from background
  threads.
- The composition root wires everything; views and controllers receive their
  dependencies through it.
