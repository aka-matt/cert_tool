package io.github.certtool.app.theme;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.settings.SettingsService;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AtlantaFX-backed implementation of {@link ThemeService}.
 *
 * <p>Behavior:
 * <ul>
 *   <li>{@link ThemeMode#LIGHT}/{@link ThemeMode#DARK} → apply explicitly and persist.</li>
 *   <li>{@link ThemeMode#SYSTEM} → query {@link SystemThemeDetector}; if detection fails, fall
 *       back to the persisted default (LIGHT on first run).</li>
 *   <li>Persists the user's choice via {@link SettingsService}.</li>
 *   <li>Notifies registered listeners when the effective theme changes (system toggle).</li>
 * </ul>
 *
 * <p>The AtlantaFX {@code UserAgentTheme} static API mutates a singleton; this class is therefore
 * a process-singleton in practice. Tests use a {@link ThemeApplier} seam to apply the theme
 * without touching real JavaFX scenes.
 */
public final class AtlantaFxThemeService implements ThemeService {

    private static final Logger LOG = LoggerFactory.getLogger(AtlantaFxThemeService.class);

    private final SettingsService settingsService;
    private final ThemeApplier applier;
    private final SystemThemeSupplier systemSupplier;
    private final CopyOnWriteArrayList<Consumer<ThemeMode>> listeners = new CopyOnWriteArrayList<>();

    private volatile ThemeMode chosen;
    private volatile ThemeMode current;

    public AtlantaFxThemeService(SettingsService settingsService, ThemeApplier applier) {
        this(settingsService, applier, SystemThemeDetector::resolveSystemTheme);
    }

    /** Test seam: inject a deterministic system-theme detector. */
    public AtlantaFxThemeService(
            SettingsService settingsService,
            ThemeApplier applier,
            SystemThemeSupplier systemSupplier) {
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.systemSupplier = Objects.requireNonNull(systemSupplier, "systemSupplier");

        Settings initial;
        try {
            initial = settingsService.load();
        } catch (IOException e) {
            LOG.warn("Failed to load settings; using defaults", e);
            initial = Settings.defaults();
        }
        this.chosen = initial.theme();
        this.current = resolveEffective(initial.theme());
        applier.apply(current);
    }

    @Override
    public ThemeMode currentMode() {
        return current;
    }

    @Override
    public ThemeMode chosenMode() {
        return chosen;
    }

    @Override
    public void setMode(ThemeMode mode) {
        Objects.requireNonNull(mode, "mode");
        this.chosen = mode;
        this.current = resolveEffective(mode);
        applier.apply(current);
        notifyListeners(current);
        try {
            Settings s = settingsService.load().withTheme(mode);
            settingsService.save(s);
        } catch (IOException e) {
            LOG.warn("Failed to persist theme choice", e);
        }
    }

    @Override
    public boolean supportsSystemListener() {
        // System-listener is platform-specific; we keep this honest by returning false on
        // platforms where polling is the only viable strategy. Future work: register a
        // JNI-based listener on Windows / macOS.
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("mac"); // macOS supports NSAppearance change notifications
    }

    /**
     * Re-evaluates the system theme (call from a polling timer or system listener). Idempotent —
     * only notifies listeners and re-applies if the resolved mode actually changed.
     */
    public void reevaluateSystemTheme() {
        if (chosen != ThemeMode.SYSTEM) {
            return;
        }
        ThemeMode resolved = systemSupplier.get();
        if (resolved != current) {
            current = resolved;
            applier.apply(current);
            notifyListeners(current);
        }
    }

    /** Registers a listener that fires whenever the effective theme changes. */
    public void addListener(Consumer<ThemeMode> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private ThemeMode resolveEffective(ThemeMode mode) {
        return mode == ThemeMode.SYSTEM ? systemSupplier.get() : mode;
    }

    private void notifyListeners(ThemeMode mode) {
        for (Consumer<ThemeMode> l : listeners) {
            try {
                l.accept(mode);
            } catch (RuntimeException e) {
                LOG.warn("Theme listener threw", e);
            }
        }
    }

    /** Pluggable theme application — production wires to AtlantaFX; tests substitute a no-op. */
    @FunctionalInterface
    public interface ThemeApplier {
        void apply(ThemeMode mode);
    }

    /** Pluggable system-theme lookup. */
    @FunctionalInterface
    public interface SystemThemeSupplier {
        ThemeMode get();
    }

    /**
     * Builds the production {@link ThemeApplier} that delegates to AtlantaFX's
     * {@code UserAgentTheme}. {@link Application#setUserAgentStylesheet} cannot be called before
     * the FX runtime is initialised, so callers MUST defer this supplier's first invocation.
     */
    public static ThemeApplier atlantaFxApplier() {
        return mode -> {
            switch (mode) {
                case LIGHT -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                case DARK -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                case SYSTEM -> {
                    // System mode is resolved before this point; applier only sees LIGHT or DARK.
                    Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                }
            }
        };
    }

    /**
     * Applies the theme through JavaFX's application-wide user-agent stylesheet.
     *
     * <p>Do not attach an AtlantaFX stylesheet directly to a scene: a scene-level stylesheet
     * remains in effect after {@link ThemeService#setMode(ThemeMode)} changes the global
     * stylesheet, preventing a visible theme switch.
     */
    public static void applyToScene(Scene scene, ThemeMode mode) {
        Objects.requireNonNull(scene, "scene");
        atlantaFxApplier().apply(mode == ThemeMode.SYSTEM ? ThemeMode.LIGHT : mode);
    }
}
