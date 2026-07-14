package io.github.certtool.app.settings;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.theme.ThemeMode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("SettingsService")
class SettingsServiceTest {

    @Test
    @DisplayName("load() returns defaults when settings file does not exist")
    void loadReturnsDefaultsWhenMissing(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        Settings s = svc.load();
        assertThat(s.theme()).isEqualTo(ThemeMode.SYSTEM);
        assertThat(s.recentFiles()).isEmpty();
    }

    @Test
    @DisplayName("round-trip: save then load returns the same Settings")
    void roundTrip(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        Settings original = Settings.defaults().withTheme(ThemeMode.DARK)
                .withRecentFile("/tmp/a.jks")
                .withRecentFile("/tmp/b.jks");
        svc.save(original);
        Settings reloaded = svc.load();
        assertThat(reloaded.theme()).isEqualTo(ThemeMode.DARK);
        assertThat(reloaded.recentFiles()).containsExactly("/tmp/b.jks", "/tmp/a.jks");
    }

    @Test
    @DisplayName("save() creates parent directory if missing")
    void saveCreatesParent(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("nested/dir/settings.json");
        SettingsService svc = new SettingsService(file);
        svc.save(Settings.defaults());
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    @DisplayName("save() refuses to persist settings that contain forbidden secret substrings")
    void saveRefusesSecrets(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        SettingsService svc = new SettingsService(file);
        Settings poisoned = new Settings(ThemeMode.LIGHT,
                List.of("/tmp/password=hunter2"), "JSON", "FIPS_140_3_ASSESSMENT",
                null, null, null, null, null, null);
        try {
            svc.save(poisoned);
            org.junit.jupiter.api.Assertions.fail("expected IOException for poisoned settings");
        } catch (IOException expected) {
            assertThat(expected).hasMessageContaining("forbidden");
        }
    }

    @Test
    @DisplayName("load() returns defaults on corrupted JSON (defensive fallback)")
    void loadCorruptedFallback(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("settings.json");
        Files.writeString(file, "this is not json");
        SettingsService svc = new SettingsService(file);
        Settings s = svc.load();
        assertThat(s.theme()).isEqualTo(ThemeMode.SYSTEM);
    }

    @Test
    @DisplayName("withRecentFile deduplicates and caps at 10 entries")
    void recentFilesDedupeAndCap() throws Exception {
        Settings s = Settings.defaults();
        for (int i = 0; i < 15; i++) {
            s = s.withRecentFile("/tmp/file-" + i + ".jks");
        }
        assertThat(s.recentFiles()).hasSize(10);
        // Most recently added is at the front.
        assertThat(s.recentFiles().get(0)).isEqualTo("/tmp/file-14.jks");
    }

    @Test
    @DisplayName("withRecentFile moves an existing entry to the front instead of duplicating")
    void recentFileMovesToFront() throws Exception {
        Settings s = Settings.defaults()
                .withRecentFile("/tmp/a.jks")
                .withRecentFile("/tmp/b.jks")
                .withRecentFile("/tmp/a.jks");
        assertThat(s.recentFiles()).containsExactly("/tmp/a.jks", "/tmp/b.jks");
    }

    @Test
    @DisplayName("withWindowBounds sets x/y/w/h")
    void withWindowBounds() throws Exception {
        Settings s = Settings.defaults().withWindowBounds(10.0, 20.0, 1024.0, 768.0);
        assertThat(s.windowX()).isEqualTo(10.0);
        assertThat(s.windowY()).isEqualTo(20.0);
        assertThat(s.windowWidth()).isEqualTo(1024.0);
        assertThat(s.windowHeight()).isEqualTo(768.0);
    }
}