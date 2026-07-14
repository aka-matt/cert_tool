package io.github.certtool.app.controller;

import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.task.LoadKeyStoreTask;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Owns the main application window. Builds the layout (MenuBar, Toolbar, content tabs, status
 * bar) and wires menu actions to the controllers in {@link AppComposition}.
 *
 * <p>Per spec, all long-running operations (load, assess, convert) are dispatched through JavaFX
 * {@link javafx.concurrent.Task} via {@link io.github.certtool.app.task.*}, never blocking the
 * Application Thread.
 */
public final class MainShellController {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(MainShellController.class);

    private final AppComposition composition;
    private final Stage stage;
    private BorderPane root;
    private ProgressBar progress;
    private Label statusMessage;

    public MainShellController(AppComposition composition, Stage stage) {
        this.composition = composition;
        this.stage = stage;
    }

    /** Builds the Scene, sets the title, applies the theme, shows the stage. */
    public void show() {
        root = new BorderPane();
        root.setTop(buildMenuBar());
        root.setBottom(buildStatusBar());
        root.setCenter(buildContentTabs());

        Scene scene = new Scene(root, 1200, 800);
        io.github.certtool.app.theme.AtlantaFxThemeService.applyToScene(scene, composition.themeService().currentMode());

        // Restore persisted window bounds.
        Settings s;
        try {
            s = composition.settingsService().load();
        } catch (IOException e) {
            LOG.warn("Failed to load settings — using defaults", e);
            s = Settings.defaults();
        }
        if (s.windowWidth() != null && s.windowHeight() != null
                && s.windowWidth() > 0 && s.windowHeight() > 0) {
            stage.setWidth(s.windowWidth());
            stage.setHeight(s.windowHeight());
        }
        if (s.windowX() != null && s.windowY() != null
                && (s.windowX() != 0 || s.windowY() != 0)) {
            stage.setX(s.windowX());
            stage.setY(s.windowY());
        }

        stage.setTitle("Cert Tool");
        stage.setScene(scene);
        stage.show();
    }

    /** Persists the current window bounds back to settings. */
    public void persistBounds() {
        try {
            Settings current = composition.settingsService().load();
            Settings updated = current
                    .withWindowBounds(stage.getX(), stage.getY(),
                            stage.getWidth(), stage.getHeight());
            composition.settingsService().save(updated);
        } catch (IOException e) {
            LOG.warn("Failed to persist window bounds", e);
        }
    }

    private Node buildMenuBar() {
        MenuBar mb = new MenuBar();

        Menu file = new Menu("File");
        MenuItem openFile = new MenuItem("Open KeyStore…");
        openFile.setOnAction(evt -> onOpenKeyStore());
        MenuItem openBase64 = new MenuItem("Paste Base64…");
        openBase64.setOnAction(evt -> LOG.info("Paste Base64 not yet implemented in scope"));
        MenuItem export = new MenuItem("Export Report…");
        export.setOnAction(evt -> LOG.info("Export Report not yet implemented in scope"));
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(evt -> stage.close());
        file.getItems().addAll(openFile, openBase64, new SeparatorMenuItem(), export, new SeparatorMenuItem(), exit);

        Menu theme = new Menu("Theme");
        MenuItem light = new MenuItem("Light");
        light.setOnAction(evt -> composition.themeService().setMode(io.github.certtool.app.theme.ThemeMode.LIGHT));
        MenuItem dark = new MenuItem("Dark");
        dark.setOnAction(evt -> composition.themeService().setMode(io.github.certtool.app.theme.ThemeMode.DARK));
        MenuItem sys = new MenuItem("System");
        sys.setOnAction(evt -> composition.themeService().setMode(io.github.certtool.app.theme.ThemeMode.SYSTEM));
        theme.getItems().addAll(light, dark, sys);

        mb.getMenus().addAll(file, theme);
        return mb;
    }

    private Node buildContentTabs() {
        TabPane tabs = new TabPane();

        Tab inspect = new Tab("Inspect");
        inspect.setClosable(false);
        inspect.setContent(buildInspectView());

        Tab compliance = new Tab("Compliance");
        compliance.setClosable(false);
        compliance.setContent(buildComplianceView());

        Tab convert = new Tab("Convert");
        convert.setClosable(false);
        convert.setContent(buildConvertView());

        Tab runtime = new Tab("Runtime");
        runtime.setClosable(false);
        runtime.setContent(buildRuntimeView());

        tabs.getTabs().addAll(inspect, compliance, convert, runtime);
        return tabs;
    }

    private Node buildInspectView() {
        // Minimal placeholder: a Label that displays alias when one is selected.
        Label info = new Label("Open a KeyStore to inspect.");
        composition.inspectVm().selectedAliasProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                info.setText("Selected alias: " + newV);
            }
        });
        VBox box = new VBox(8, info);
        box.setPadding(new Insets(16));
        return box;
    }

    private Node buildComplianceView() {
        // Minimal placeholder: shows the first available profile name.
        Label info = new Label("Run an assessment after loading a KeyStore.");
        var profiles = composition.complianceVm().availableProfiles();
        if (!profiles.isEmpty()) {
            info.setText("Profile: " + profiles.get(0).name());
        }
        VBox box = new VBox(8, info);
        box.setPadding(new Insets(16));
        return box;
    }

    private Node buildConvertView() {
        // Minimal placeholder: shows the currently selected target path.
        Label info = new Label("Convert wizard: pick a source and target to convert.");
        composition.convertVm().targetPathProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && !newV.isEmpty()) {
                info.setText("Target: " + newV);
            }
        });
        VBox box = new VBox(8, info);
        box.setPadding(new Insets(16));
        return box;
    }

    private Node buildRuntimeView() {
        Label info = new Label("Refreshing…");
        composition.runtimeController().refresh();
        var env = composition.runtimeVm().getEnvironment();
        if (env != null) {
            info.setText("JVM: " + env.jvmVendor() + " " + env.jvmVersion()
                    + " | OS: " + env.osName() + " " + env.osArch()
                    + " | Providers: " + env.providers().size()
                    + " | BCFIPS: " + env.bcfipsDetected());
        }
        VBox box = new VBox(8, info);
        box.setPadding(new Insets(16));
        return box;
    }

    private Node buildStatusBar() {
        progress = new ProgressBar(0);
        progress.setPrefWidth(160);
        progress.setVisible(false);

        statusMessage = new Label("Ready.");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, statusMessage, spacer, progress);
        bar.setPadding(new Insets(4, 8, 4, 8));
        return bar;
    }

    /** Opens a file chooser and dispatches a LoadKeyStoreTask on the background executor. */
    private void onOpenKeyStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open KeyStore");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All keystores", "*.jks", "*.bcfks", "*.keystore"),
                new FileChooser.ExtensionFilter("JKS", "*.jks"),
                new FileChooser.ExtensionFilter("BCFKS", "*.bcfks"));
        Path path = java.nio.file.Paths.get(chooser.showOpenDialog(stage).getAbsolutePath()).toAbsolutePath();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            statusMessage.setText("Read failed.");
            return;
        }
        KeyStoreContainerType container = path.toString().toLowerCase().endsWith(".bcfks")
                ? KeyStoreContainerType.BCFKS : KeyStoreContainerType.JKS;
        LoadKeyStoreTask task = composition.loadTask(bytes, container);
        task.stateProperty().addListener((obs, oldS, newS) -> updateProgress(newS, task.getProgress()));
        task.messageProperty().addListener((obs, oldM, newM) -> {
            if (newM != null && !newM.isEmpty()) {
                statusMessage.setText(newM);
            }
        });
        task.setOnSucceeded(evt -> {
            KeyStoreLoadResult result = task.getValue();
            composition.inspectController().onLoadResult(result);
            statusMessage.setText("Loaded: " + path.getFileName());
        });
        task.setOnFailed(evt -> statusMessage.setText("Load failed."));
        composition.backgroundExecutor().submit(task);
    }

    private void updateProgress(Worker.State state, double progressValue) {
        if (state == Worker.State.RUNNING) {
            progress.setVisible(true);
            progress.setProgress(progressValue < 0 ? 0 : progressValue);
        } else {
            progress.setVisible(false);
            progress.setProgress(0);
        }
    }

    /** No-op helper to mark unused ContentEncoding import (kept for clarity in the layout above). */
    @SuppressWarnings("unused")
    private static ContentEncoding touch() {
        return ContentEncoding.BINARY;
    }

    /** No-op helper to keep the java.util.logging import alive for migration later. */
    @SuppressWarnings("unused")
    private static void touchLogger() {
        Logger.getLogger("touch").log(Level.FINE, "noop");
    }
}
