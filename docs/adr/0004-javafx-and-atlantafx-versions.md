# ADR-0004: JavaFX and AtlantaFX versions

- **Status**: Accepted
- **Date**: 2026-07-13

## Context

Spec §1 mandates JavaFX and AtlantaFX. Spec §10 requires Light/Dark/System themes with system-detection on Windows/macOS/Linux. AtlantaFX is a popular AtlantaFX-based theme collection for JavaFX, offering pre-built light/dark variants and accent colors.

Version selection must:
- Stay compatible with Java 17 (per ADR-0002).
- Stay license-clean (MIT/Apache-2.0).
- Not introduce monthly upgrade churn.

## Decision

- **JavaFX**: 17.0.x line, latest patch at start of Phase 0; bump only for security fixes or blocking bugs.
- **AtlantaFX**: latest 17.x release line that targets JavaFX 17.
- **ControlsFX**: latest 17.x release (for `StatusBar`, `NotificationPane`, monospace HTML rendering in PEM preview). License: BSD-3.
- **Ikonli** (icon set, optional): latest JavaFX-compatible release. License: Apache-2.0.
- **FXML**: used for layout only; controllers are written in Java (no FXML logic).

All four are declared in `<dependencyManagement>` of the parent POM and pinned to exact versions. The `app` module pulls in the JavaFX controls/graphics/fxml/web artifacts plus the platform-classified natives.

## Consequences

- **Positive**: Pinning avoids surprise regressions during a phased rollout.
- **Positive**: Themes come "for free" from AtlantaFX; we only build a `ThemeService` to switch them and to detect OS theme.
- **Negative**: Pinned versions lag upstream; periodic refresh task required.
- **Risk**: JavaFX web component is heavy. We only depend on it if Phase 6 HTML-preview genuinely needs in-app HTML rendering; otherwise the report is exported to a file the user opens externally.

## Follow-ups

- During Phase 7, confirm the system-theme detection strategy (`jna`/`jfx-platform`/`os-gui`) and record the chosen approach in ADR-0009.