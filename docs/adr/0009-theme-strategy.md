# ADR-0009: Theme strategy

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §10 requires Light, Dark, and System themes with detection of OS theme on Windows, macOS, and Linux, fallback to a configured theme when detection fails, listener for system theme changes where the platform supports it, and a `ThemeService` so Controllers do not modify theme directly.

We must keep theme detection decoupled from business logic so unit tests of the rest of the system can stub it.

## Decision

- **ThemeService interface** lives in `platform` module (so `app` can consume it) with a single implementation `AtlantaFxThemeService` in `app`.
- **Static themes**: AtlantaFX ships `PrimerLight` and `PrimerDark`. `ThemeService.apply(LIGHT | DARK)` switches the active stylesheet and persists the choice.
- **System theme detection**:
  - **macOS**: `defaults read -g AppleInterfaceStyle` via `ProcessBuilder` (returns `Dark` or absent).
  - **Windows**: registry read via JNA (`HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Themes\Personalize\AppsUseLightTheme`, `0` = dark).
  - **Linux**: `$XDG_CONFIG_HOME/gtk-3.0/settings.ini` `gtk-application-prefer-dark-theme=true`, with `gsettings` fallback on GNOME (`org.gnome.desktop.interface color-scheme`).
  - **Fallback**: configured `systemThemeFallback` (default: `LIGHT`) per ADR-0008.
- **Live change listener**:
  - **macOS**: `ProcessBuilder` poll every 5 s (Apple does not expose a clean event API without native code).
  - **Windows**: JNA registry watch via `RegNotifyChangeKeyValue`.
  - **Linux**: inotify on the GTK settings file or D-Bus `org.freedesktop.portal.Settings` signal (best-effort).
- **Failure handling**: any detection failure returns `Result.UNKNOWN`, and the service applies the fallback. The UI shows a one-time "System theme could not be detected — using <fallback>." notice that the user can dismiss.
- **Threading**: detection runs on a background `ScheduledExecutorService`; scene updates dispatch back to the JavaFX Application Thread via `Platform.runLater`.
- **Tests**: `ThemeService` is an interface; tests use a `RecordingThemeService` fake. The OS-detection strategies each have a unit test using a stubbed `ProcessBuilder` and a fake filesystem. End-to-end "switch theme" coverage lives in the TestFX smoke list.

## Consequences

- **Positive**: Theme logic is testable in isolation; `app` controllers can be written without ever importing `javafx.scene.Scene`.
- **Positive**: Live detection on Windows uses native APIs; macOS and Linux degrade gracefully.
- **Negative**: macOS polling burns a small amount of CPU. Mitigated by 5-second interval and disabling when user has selected a static theme.
- **Risk**: Linux desktop fragmentation. GNOME, KDE, XFCE, and Sway each expose theme differently; we document support as "best-effort on GNOME and KDE."
- **Risk**: Windows JNA adds a native dependency. License is Apache-2.0; acceptable. Pinned in the parent BOM.

## Follow-ups

- Document the OS-specific detection paths in `README.md` and `THREAT-MODEL.md` (the OS-detection step touches local user config — no secrets, but it is a side channel).
- Add JNA dependency to the parent BOM and exclude transitive `jna-platform` only if needed.