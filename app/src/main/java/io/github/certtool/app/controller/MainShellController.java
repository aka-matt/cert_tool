package io.github.certtool.app.controller;

import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.task.LoadKeyStoreTask;
import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
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
    private Node inspectViewNode;
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
        inspectViewNode = null; // force lazy rebuild on next access

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

    /** Package-private accessor for tests: lazily builds and returns the Inspect pane root. */
    Node inspectView() {
        if (inspectViewNode == null) {
            inspectViewNode = buildInspectView();
        }
        return inspectViewNode;
    }

    private Node buildMenuBar() {
        MenuBar mb = new MenuBar();

        Menu file = new Menu("File");
        MenuItem openFile = new MenuItem("Open KeyStore…");
        openFile.setOnAction(evt -> onOpenKeyStore());
        MenuItem openBase64 = new MenuItem("Paste Base64…");
        openBase64.setOnAction(evt -> onPasteBase64());
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
        BorderPane shell = new BorderPane();
        shell.setPadding(new Insets(12));

        Label placeholder = new Label("Open a KeyStore to inspect.");
        shell.setCenter(placeholder);

        SplitPane split = new SplitPane();
        split.setDividerPositions(0.25);
        split.setVisible(false); // hidden until a keystore is loaded

        TreeView<String> tree = buildInspectTree();
        TabPane tabs = buildInspectTabs();

        split.getItems().addAll(tree, tabs);
        shell.setCenter(split);

        // React to load results coming through the VM. (tree, tabs, placeholder, split are
        // effectively-final locals captured by these lambda listeners.)
        composition.inspectVm().loadResultProperty().addListener((obs, oldV, newV) -> {
            boolean loaded = newV != null && newV.isSuccess();
            placeholder.setVisible(!loaded);
            split.setVisible(loaded);
        });
        composition.inspectVm().inspectedProperty().addListener((obs, oldV, newV) -> {
            rebuildInspectTree(tree);
            tabs.getTabs().get(0).setContent(new ScrollPane(buildOverviewContent(newV)));
        });
        composition.inspectVm().selectedAliasProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });
        composition.inspectVm().currentCertificateIndexProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });

        return shell;
    }

    private TreeView<String> buildInspectTree() {
        TreeItem<String> root = new TreeItem<>("KeyStore");
        root.setExpanded(true);
        TreeView<String> tree = new TreeView<>(root);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
            }
        });
        tree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV == null || newV.getParent() == null) {
                return;
            }
            composition.inspectVm().setSelectedAlias(newV.getValue());
        });
        return tree;
    }

    private TabPane buildInspectTabs() {
        TabPane tabs = new TabPane();
        Tab overview = new Tab("Overview");
        overview.setClosable(false);
        overview.setContent(new ScrollPane(buildOverviewContent(null)));
        Tab certificate = new Tab("Certificate");
        certificate.setClosable(false);
        certificate.setContent(new ScrollPane(buildCertificateContent(null, 0, 0)));
        Tab chain = new Tab("Chain");
        chain.setClosable(false);
        chain.setContent(new ScrollPane(new VBox(new Label("Chain — select an alias."))));
        Tab extensions = new Tab("Extensions");
        extensions.setClosable(false);
        extensions.setContent(new ScrollPane(new VBox(new Label("Extensions — select an alias."))));
        Tab pem = new Tab("PEM");
        pem.setClosable(false);
        pem.setContent(new ScrollPane(new VBox(new Label("PEM — select an alias."))));
        tabs.getTabs().addAll(overview, certificate, chain, extensions, pem);
        return tabs;
    }

    private void rebuildInspectTree(TreeView<String> tree) {
        var vm = composition.inspectVm();
        TreeItem<String> root = tree.getRoot();
        root.getChildren().clear();
        Map<InspectViewModel.Group, List<InspectViewModel.NavNode>> buckets = new LinkedHashMap<>();
        buckets.put(InspectViewModel.Group.PRIVATE_KEYS, new ArrayList<>());
        buckets.put(InspectViewModel.Group.TRUSTED_CERTIFICATES, new ArrayList<>());
        buckets.put(InspectViewModel.Group.SECRET_KEYS, new ArrayList<>());
        buckets.put(InspectViewModel.Group.UNREADABLE_ENTRIES, new ArrayList<>());
        for (var n : vm.navNodes()) {
            buckets.get(n.group()).add(n);
        }
        for (var entry : buckets.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            String label = labelForGroup(entry.getKey()) + " (" + entry.getValue().size() + ")";
            TreeItem<String> group = new TreeItem<>(label);
            for (var n : entry.getValue()) {
                group.getChildren().add(new TreeItem<>(n.alias() + " — " + n.entryType()));
            }
            root.getChildren().add(group);
        }
        // Re-select current alias.
        String alias = vm.getSelectedAlias();
        if (alias != null) {
            for (TreeItem<String> group : root.getChildren()) {
                for (TreeItem<String> leaf : group.getChildren()) {
                    if (leaf.getValue().startsWith(alias + " — ")) {
                        tree.getSelectionModel().select(leaf);
                        return;
                    }
                }
            }
        }
    }

    private static String labelForGroup(InspectViewModel.Group g) {
        return switch (g) {
            case PRIVATE_KEYS -> "Private Keys";
            case TRUSTED_CERTIFICATES -> "Trusted Certificates";
            case SECRET_KEYS -> "Secret Keys";
            case UNREADABLE_ENTRIES -> "Unreadable Entries";
        };
    }

    private Node buildOverviewContent(InspectedKeyStore inspected) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));
        if (inspected == null) {
            box.getChildren().add(new Label("No keystore loaded."));
            return box;
        }
        var s = inspected.summary();
        var header = new Label(String.format(
                "Container: %s | Provider: %s %s | Entries: %d | Certificates: %d",
                s.containerType(), s.providerName(), s.providerVersion(),
                s.totalEntries(), s.totalCertificates()));
        header.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(header);
        for (var e : inspected.entries()) {
            TitledPane tp = new TitledPane();
            tp.setText(e.alias() + " — " + e.entryType() + " — " + e.certificates().size() + " cert(s)");
            tp.setContent(buildEntrySummaryBox(e));
            box.getChildren().add(tp);
        }
        return box;
    }

    private Node buildEntrySummaryBox(InspectedEntry e) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        box.getChildren().add(new Label("Key algorithm: " + (e.keyAlgorithm() == null ? "n/a" : e.keyAlgorithm())));
        box.getChildren().add(new Label("Key size: " + (e.keySize() == null ? "n/a" : e.keySize() + " bits")));
        if (!e.warnings().isEmpty()) {
            box.getChildren().add(new Label("Warnings: " + String.join("; ", e.warnings())));
        }
        if (!e.certificates().isEmpty()) {
            InspectedCertificate first = e.certificates().get(0);
            var a = first.analysis();
            box.getChildren().add(new Label("Subject: " + a.subject()));
            box.getChildren().add(new Label("Issuer:  " + a.issuer()));
            box.getChildren().add(new Label("Valid:   " + a.validity().notBefore() + " → " + a.validity().notAfter()
                    + " (" + a.currentValidity() + ")"));
            box.getChildren().add(new Label("Fingerprint (SHA-256): " + a.fingerprints().sha256Formatted()));
        }
        return box;
    }

    private Node buildCertificateContent(InspectedCertificate inspectedCert, int chainIndex, int chainSize) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        if (inspectedCert == null) {
            box.getChildren().add(new Label("Select an alias to view a certificate."));
            return box;
        }
        if (chainSize > 1) {
            HBox nav = new HBox(6);
            Button back = new Button("◀ Prev");
            Button fwd = new Button("Next ▶");
            Label counter = new Label((chainIndex + 1) + " / " + chainSize);
            back.setOnAction(e -> composition.inspectVm().decrementCurrentCertificateIndex());
            fwd.setOnAction(e -> composition.inspectVm().incrementCurrentCertificateIndex());
            nav.getChildren().addAll(back, fwd, counter);
            box.getChildren().add(nav);
        }
        var a = inspectedCert.analysis();
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(4);
        int row = 0;
        addRow(grid, row++, "Subject", a.subject());
        addRow(grid, row++, "Issuer", a.issuer());
        addRow(grid, row++, "Serial (hex)", a.serialNumberHex());
        addRow(grid, row++, "Serial (dec)", a.serialNumberDecimal());
        addRow(grid, row++, "Version", "v" + a.x509Version());
        addRow(grid, row++, "Not Before", a.validity().notBefore().toString());
        addRow(grid, row++, "Not After", a.validity().notAfter().toString());
        addRow(grid, row++, "Validity", a.currentValidity().name());
        addRow(grid, row++, "Signature Algorithm", a.signatureAlgorithm());
        addRow(grid, row++, "Signature Algorithm OID", a.signatureAlgorithmOid());
        addRow(grid, row++, "Public Key Algorithm", a.publicKeyInfo().algorithm().name());
        addRow(grid, row++, "Public Key Size", a.publicKeyInfo().rsaKeySize() == null
                ? (a.publicKeyInfo().ecCurveName() == null ? "n/a" : a.publicKeyInfo().ecCurveName())
                : a.publicKeyInfo().rsaKeySize() + " bits");
        addRow(grid, row++, "SHA-256", a.fingerprints().sha256Formatted());
        addRow(grid, row++, "SHA-1", a.fingerprints().sha1Formatted());
        addRow(grid, row++, "Self-signed", a.selfSigned().isFullySelfSigned() ? "YES" : "no");
        box.getChildren().add(grid);
        return box;
    }

    private static void addRow(GridPane grid, int row, String label, String value) {
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-weight: bold;");
        grid.add(l, 0, row);
        grid.add(new Label(value == null ? "" : value), 1, row);
    }

    private void updateInspectDetailTabs(TabPane tabs) {
        // Real binding for the Certificate / Chain / Extensions / PEM tabs lands in Tasks 9–11.
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

    /** Opens a modal multi-line dialog and submits non-blank pasted Base64 for loading. */
    private void onPasteBase64() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Paste Base64");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextArea input = new TextArea();
        input.setPromptText("Paste Base64-encoded keystore data");
        input.setPrefColumnCount(72);
        input.setPrefRowCount(16);
        dialog.getDialogPane().setContent(input);
        dialog.setResultConverter(button -> button == ButtonType.OK ? input.getText() : null);
        dialog.showAndWait().ifPresent(this::handlePastedBase64);
    }

    /** Submits non-blank Base64 input without exposing it through logs, status text, or errors. */
    void handlePastedBase64(String input) {
        if (input == null || input.isBlank()) {
            return;
        }
        submitPastedLoadTask(composition.pasteBase64LoadTask(input), input);
    }

    private void submitPastedLoadTask(javafx.concurrent.Task<KeyStoreLoadResult> task, String input) {
        bindLoadTask(task, () -> handlePastedLoadResult(input, task.getValue(), this::chooseContainerType));
    }

    /** Routes a completed initial paste probe without exposing the pasted value in UI output. */
    void handlePastedLoadResult(
            String input, KeyStoreLoadResult result, ContainerTypeSelector containerTypeSelector) {
        if (result != null && result.isSuccess()) {
            composition.inspectController().onLoadResult(result);
            setStatus("Pasted keystore loaded.");
        } else if (isAmbiguous(result)) {
            containerTypeSelector.choose().ifPresent(container -> submitSelectedContainerLoad(input, container));
        } else {
            setStatus("Could not load pasted keystore.");
        }
    }

    private boolean isAmbiguous(KeyStoreLoadResult result) {
        return result != null
                && result.failure() != null
                && result.failure().reason() == io.github.certtool.domain.error.LoadFailureReason.AMBIGUOUS_CONTAINER;
    }

    private Optional<KeyStoreContainerType> chooseContainerType() {
        ChoiceDialog<KeyStoreContainerType> dialog = new ChoiceDialog<>(
                KeyStoreContainerType.JKS, KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS);
        dialog.setTitle("Select KeyStore format");
        dialog.setHeaderText("Could not determine the keystore container.");
        dialog.setContentText("Format:");
        dialog.initOwner(stage);
        return dialog.showAndWait();
    }

    private void submitSelectedContainerLoad(String input, KeyStoreContainerType container) {
        javafx.concurrent.Task<KeyStoreLoadResult> task = composition.selectedBase64LoadTask(input, container);
        bindLoadTask(task, () -> {
            KeyStoreLoadResult result = task.getValue();
            if (result != null && result.isSuccess()) {
                composition.inspectController().onLoadResult(result);
                setStatus("Pasted keystore loaded.");
            } else {
                setStatus("Could not load pasted keystore.");
            }
        });
    }

    private void bindLoadTask(javafx.concurrent.Task<KeyStoreLoadResult> task, Runnable onSucceeded) {
        task.stateProperty().addListener((obs, oldS, newS) -> updateProgress(newS, task.getProgress()));
        task.messageProperty().addListener((obs, oldM, newM) -> {
            if (newM != null && !newM.isEmpty()) {
                setStatus(newM);
            }
        });
        task.setOnSucceeded(evt -> onSucceeded.run());
        task.setOnFailed(evt -> setStatus("Could not load pasted keystore."));
        composition.backgroundExecutor().submit(task);
    }

    private void setStatus(String message) {
        if (statusMessage != null) {
            statusMessage.setText(message);
        }
    }

    @FunctionalInterface
    interface ContainerTypeSelector {
        Optional<KeyStoreContainerType> choose();
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
