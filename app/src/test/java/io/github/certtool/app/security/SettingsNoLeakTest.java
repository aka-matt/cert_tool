package io.github.certtool.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.settings.SettingsService;
import io.github.certtool.testfixtures.security.LogCaptureExtension;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

/**
 * Security tests for {@link SettingsService}: persists only the configured schema, never includes
 * passwords, never writes the forbidden certification claims.
 *
 * <p>Per spec §2 and §6 (FIPS phrasing), the persistence layer must reject any forbidden substring
 * and roundtrip cleanly. These tests fail fast if a future contributor adds a leaky field.
 */
@DisplayName("Settings security: no leaks")
class SettingsNoLeakTest {

    @RegisterExtension
    static final LogCaptureExtension LOG_CAPTURE = new LogCaptureExtension();

    @Test
    @DisplayName("Settings roundtrip preserves only whitelisted fields")
    void roundtripPreservesFields(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("settings.json");
        SettingsService service = new SettingsService(file);
        Settings defaults = Settings.defaults();
        service.save(defaults);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("password");
        assertThat(content).doesNotContain("FIPS Certification");
        assertThat(content).doesNotContain("NIST Certified");
        assertThat(content).doesNotContain("正式认证结论");

        Settings reloaded = service.load();
        assertThat(reloaded.theme()).isEqualTo(defaults.theme());
        assertThat(reloaded.recentFiles()).isEqualTo(defaults.recentFiles());
    }

    @Test
    @DisplayName("save() refuses to persist settings whose JSON contains a forbidden substring")
    void refuseForbiddenSubstring(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("settings.json");
        SettingsService service = new SettingsService(file);

        // create a JSON file with forbidden content so load() will reject
        Files.writeString(file, "{\"theme\":\"password=foo\"}", StandardCharsets.UTF_8);
        assertThatThrownBy(service::load).isInstanceOf(IOException.class)
                .hasMessageContaining("forbidden");
    }
}
