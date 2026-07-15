package io.github.certtool.app.settings;

import io.github.certtool.app.theme.ThemeMode;
import java.util.List;
import java.util.Objects;

/**
 * User-visible application settings, persisted as JSON by {@link SettingsService}.
 *
 * <p>This is intentionally a record so adding a new field is a one-liner and Jackson handles
 * (de)serialization with no extra annotation work. Defaults are set via the canonical no-arg
 * factory {@link #defaults()}.
 *
 * <p>Security: passwords and key material MUST NEVER appear here — the service refuses to write a
 * {@link Settings} instance whose serialization contains forbidden substrings (see
 * {@link SettingsService}).
 */
public record Settings(
        ThemeMode theme,
        List<String> recentFiles,
        String lastExportFormat,
        String lastAssessmentProfileId,
        Double windowWidth,
        Double windowHeight,
        Double windowX,
        Double windowY,
        Double leftDividerPosition,
        Double rightDividerPosition) {

    public Settings {
        Objects.requireNonNull(theme, "theme");
        recentFiles = recentFiles == null ? List.of() : List.copyOf(recentFiles);
        lastExportFormat = lastExportFormat == null ? "JSON" : lastExportFormat;
        lastAssessmentProfileId = lastAssessmentProfileId == null
                ? "FIPS_140_3_ASSESSMENT" : lastAssessmentProfileId;
    }

    /** Returns the canonical defaults — first-run experience. */
    public static Settings defaults() {
        return new Settings(
                ThemeMode.SYSTEM,
                List.of(),
                "JSON",
                "FIPS_140_3_ASSESSMENT",
                1280.0,
                800.0,
                null,
                null,
                0.25,
                0.75);
    }

    /** Returns a copy with {@code theme} replaced. */
    public Settings withTheme(ThemeMode newTheme) {
        return new Settings(newTheme, recentFiles, lastExportFormat,
                lastAssessmentProfileId, windowWidth, windowHeight,
                windowX, windowY, leftDividerPosition, rightDividerPosition);
    }

    /** Returns a copy with a new file added to the front of {@code recentFiles}, capped at 10. */
    public Settings withRecentFile(String path) {
        if (path == null || path.isBlank()) {
            return this;
        }
        List<String> updated = new java.util.ArrayList<>();
        updated.add(path);
        for (String existing : recentFiles) {
            if (!existing.equals(path)) {
                updated.add(existing);
                if (updated.size() == 10) {
                    break;
                }
            }
        }
        return new Settings(theme, updated, lastExportFormat,
                lastAssessmentProfileId, windowWidth, windowHeight,
                windowX, windowY, leftDividerPosition, rightDividerPosition);
    }

    /** Returns a copy with the left divider position replaced. */
    public Settings withLeftDividerPosition(double position) {
        return new Settings(theme, recentFiles, lastExportFormat,
                lastAssessmentProfileId, windowWidth, windowHeight,
                windowX, windowY, position, rightDividerPosition);
    }

    /** Returns a copy with window bounds replaced. */
    public Settings withWindowBounds(double x, double y, double w, double h) {
        return new Settings(theme, recentFiles, lastExportFormat,
                lastAssessmentProfileId, w, h, x, y, leftDividerPosition, rightDividerPosition);
    }
}