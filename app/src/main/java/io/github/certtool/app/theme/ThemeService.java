package io.github.certtool.app.theme;

/**
 * Owns the active application theme. Implementations are responsible for:
 * <ul>
 *   <li>Applying the theme to the running scene (AtlantaFX or plain JavaFX).</li>
 *   <li>Persisting the user's choice via {@link io.github.certtool.app.settings.SettingsService}.</li>
 *   <li>Listening for system theme changes when supported; otherwise polling the platform
 *       module.</li>
 *   <li>Never exposing direct JavaFX theme modification to controllers — controllers call
 *       {@link #setMode(ThemeMode)} and observe {@link #currentMode()}.</li>
 * </ul>
 */
public interface ThemeService {

    /** Returns the current effective theme. May differ from the user-chosen mode when SYSTEM is
     *  active and the system has just toggled. */
    ThemeMode currentMode();

    /** Returns the user-chosen mode (independent of currentMode() when SYSTEM + a system
     *  change occurred). */
    ThemeMode chosenMode();

    /** Switches the theme and persists the choice. Idempotent. */
    void setMode(ThemeMode mode);

    /** Returns true if the implementation supports listening for system theme changes. */
    boolean supportsSystemListener();
}