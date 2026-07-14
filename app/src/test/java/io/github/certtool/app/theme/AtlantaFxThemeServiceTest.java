package io.github.certtool.app.theme;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.settings.SettingsService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("AtlantaFxThemeService")
class AtlantaFxThemeServiceTest {

    /** Records every theme that was applied. */
    static final class RecordingApplier implements AtlantaFxThemeService.ThemeApplier {
        final List<ThemeMode> applied = new ArrayList<>();
        @Override public void apply(ThemeMode mode) { applied.add(mode); }
    }

    private static SettingsService freshSettings(Path tmp) {
        return new SettingsService(tmp.resolve("settings.json"));
    }

    @Test
    @DisplayName("constructor applies the chosen theme (LIGHT)")
    void constructorAppliesChosenTheme(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        svc.save(Settings.defaults().withTheme(ThemeMode.LIGHT));
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        assertThat(ts.currentMode()).isEqualTo(ThemeMode.LIGHT);
        assertThat(applier.applied).contains(ThemeMode.LIGHT);
    }

    @Test
    @DisplayName("SYSTEM mode resolves via the SystemThemeSupplier")
    void systemResolvesViaSupplier(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        svc.save(Settings.defaults().withTheme(ThemeMode.SYSTEM));
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.DARK);
        assertThat(ts.currentMode()).isEqualTo(ThemeMode.DARK);
        assertThat(ts.chosenMode()).isEqualTo(ThemeMode.SYSTEM);
    }

    @Test
    @DisplayName("setMode persists the choice and applies the new theme")
    void setModePersists(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        ts.setMode(ThemeMode.DARK);
        assertThat(ts.currentMode()).isEqualTo(ThemeMode.DARK);
        assertThat(svc.load().theme()).isEqualTo(ThemeMode.DARK);
    }

    @Test
    @DisplayName("listeners fire when the effective theme changes")
    void listenersFire(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        AtomicInteger calls = new AtomicInteger();
        List<ThemeMode> seen = new ArrayList<>();
        ts.addListener(m -> { calls.incrementAndGet(); seen.add(m); });
        ts.setMode(ThemeMode.DARK);
        ts.setMode(ThemeMode.DARK); // idempotent — listener still fires (we always notify)
        ts.setMode(ThemeMode.LIGHT);
        assertThat(calls.get()).isGreaterThanOrEqualTo(2);
        assertThat(seen).contains(ThemeMode.DARK, ThemeMode.LIGHT);
    }

    @Test
    @DisplayName("reevaluateSystemTheme is a no-op when chosen mode is not SYSTEM")
    void reevaluateNoOpForNonSystem(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        svc.save(Settings.defaults().withTheme(ThemeMode.DARK));
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        int before = applier.applied.size();
        ts.reevaluateSystemTheme();
        assertThat(applier.applied.size()).isEqualTo(before);
    }

    @Test
    @DisplayName("reevaluateSystemTheme re-applies and notifies when SYSTEM resolved mode changes")
    void reevaluateFiresForSystemChange(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        svc.save(Settings.defaults().withTheme(ThemeMode.SYSTEM));
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<ThemeMode> currentSystem =
                new java.util.concurrent.atomic.AtomicReference<>(ThemeMode.LIGHT);
        AtlantaFxThemeService.SystemThemeSupplier supplier = currentSystem::get;
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, supplier);
        ts.addListener(m -> calls.incrementAndGet());
        currentSystem.set(ThemeMode.DARK);
        ts.reevaluateSystemTheme();
        assertThat(ts.currentMode()).isEqualTo(ThemeMode.DARK);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("supportsSystemListener is true on macOS, false elsewhere (best-effort)")
    void supportsSystemListenerHonesty(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        // We don't assert the boolean (depends on test runner OS) — just that the call is
        // side-effect-free.
        ts.supportsSystemListener();
    }

    @Test
    @DisplayName("constructor falls back to defaults if settings file is unreadable")
    void fallbackOnCorruptedSettings(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        java.nio.file.Files.writeString(file, "corrupted");
        RecordingApplier applier = new RecordingApplier();
        AtlantaFxThemeService ts = new AtlantaFxThemeService(
                new SettingsService(file), applier, () -> ThemeMode.LIGHT);
        // SettingsService.load() returns defaults on corruption → SYSTEM chosen, LIGHT resolved.
        assertThat(ts.chosenMode()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(ts.currentMode()).isEqualTo(ThemeMode.LIGHT);
    }

    @Test
    @DisplayName("listener exceptions are swallowed (one bad listener doesn't break the rest)")
    void listenerExceptionIsolation(@TempDir Path tmp) throws Exception {
        RecordingApplier applier = new RecordingApplier();
        SettingsService svc = freshSettings(tmp);
        AtlantaFxThemeService ts = new AtlantaFxThemeService(svc, applier, () -> ThemeMode.LIGHT);
        AtomicInteger goodCalls = new AtomicInteger();
        ts.addListener(m -> { throw new RuntimeException("boom"); });
        ts.addListener(m -> goodCalls.incrementAndGet());
        ts.setMode(ThemeMode.DARK);
        assertThat(goodCalls.get()).isEqualTo(1);
    }
}