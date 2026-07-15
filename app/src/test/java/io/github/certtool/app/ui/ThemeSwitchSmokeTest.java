package io.github.certtool.app.ui;

import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.settings.SettingsService;
import io.github.certtool.app.theme.AtlantaFxThemeService;
import io.github.certtool.app.theme.ThemeMode;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Critical smoke test: theme switching flows through the composition root → scene without
 * throwing. Gated behind {@code -Dcert.tool.testfx=true} so CI can opt in or out; headless
 * environments won't have a display to test against.
 */
@EnabledIfSystemProperty(named = "cert.tool.testfx", matches = "true")
class ThemeSwitchSmokeTest {

    private Stage stage;
    private Settings originalSettings;

    @BeforeEach
    void setUp() throws Exception {
        SettingsService service = SettingsService.defaults();
        originalSettings = service.load();
        // Ensure a known theme before the test.
        service.save(originalSettings.withTheme(ThemeMode.SYSTEM));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (stage != null) {
            stage.close();
        }
        if (originalSettings != null) {
            SettingsService service = SettingsService.defaults();
            service.save(originalSettings);
        }
    }

    @org.junit.jupiter.api.Test
    void themeSwitchDoesNotThrow() throws Exception {
        AppComposition composition = AppComposition.defaultComposition();
        stage = new Stage();
        Scene scene = new Scene(new BorderPane(), 200, 200);
        AtlantaFxThemeService.applyToScene(scene, composition.themeService().currentMode());
        assert scene.getStylesheets().isEmpty();
        stage.setScene(scene);
        stage.show();

        AtomicReference<Throwable> caught = new AtomicReference<>();
        for (ThemeMode mode : ThemeMode.values()) {
            composition.themeService().setMode(mode);
            assert composition.themeService().currentMode() != null;
        }
        if (caught.get() != null) {
            throw new AssertionError("Theme switching threw", caught.get());
        }
    }
}
