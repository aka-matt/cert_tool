package io.github.certtool.app.controller;

import io.github.certtool.app.AppComposition;
import io.github.certtool.app.settings.Settings;
import io.github.certtool.app.task.AnalyzeKeyStoreTask;
import io.github.certtool.app.task.AutoDetectKeyStoreLoadTask;
import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;
import javafx.concurrent.Task;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
    private SplitPane inspectSplit;
    private ProgressBar progress;
    private Label statusMessage;
    private volatile Task<?> currentLoadTask;
    private volatile AnalyzeKeyStoreTask currentAnalyzeTask;

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

        restoreInspectDividerPosition(s);

        stage.setTitle("Cert Tool");
        stage.setScene(scene);
        stage.show();
    }

    /** Persists the current window bounds and Inspect divider position back to settings. */
    public void persistBounds() {
        try {
            Settings current = composition.settingsService().load();
            Settings updated = current
                    .withWindowBounds(stage.getX(), stage.getY(),
                            stage.getWidth(), stage.getHeight())
                    .withLeftDividerPosition(inspectDividerPosition());
            composition.settingsService().save(updated);
        } catch (IOException e) {
            LOG.warn("Failed to persist window layout", e);
        }
    }

    /** Package-private accessor for tests: lazily builds and returns the Inspect pane root. */
    Node inspectView() {
        if (inspectViewNode == null) {
            inspectViewNode = buildInspectView();
        }
        return inspectViewNode;
    }

    void restoreInspectDividerPosition(Settings settings) {
        if (inspectSplit == null) {
            return;
        }
        double position = 0.25;
        Double saved = settings.leftDividerPosition();
        if (saved != null && Double.isFinite(saved) && saved > 0.0 && saved < 1.0) {
            position = Math.max(0.05, Math.min(0.95, saved));
        }
        inspectSplit.setDividerPositions(position);
    }

    double inspectDividerPosition() {
        if (inspectSplit == null || inspectSplit.getDividerPositions().length == 0) {
            return 0.25;
        }
        return inspectSplit.getDividerPositions()[0];
    }

    private Node buildMenuBar() {
        MenuBar mb = new MenuBar();

        Menu file = new Menu("File");
        MenuItem openFile = new MenuItem("Open KeyStore or TrustStore…");
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
        SplitPane split = new SplitPane();
        inspectSplit = split;
        split.setDividerPositions(0.25);
        split.setVisible(false); // hidden until a keystore is loaded

        TreeView<String> tree = buildInspectTree();
        TabPane tabs = buildInspectTabs();

        split.getItems().addAll(tree, tabs);
        StackPane center = new StackPane(placeholder, split);
        shell.setCenter(center);

        // React to load results coming through the VM. (tree, tabs, placeholder, split are
        // effectively-final locals captured by these lambda listeners.)
        composition.inspectVm().loadResultProperty().addListener((obs, oldV, newV) -> {
            boolean loaded = newV != null && newV.isSuccess();
            placeholder.setVisible(!loaded);
            split.setVisible(loaded);
            // Refresh placeholder text so a failed load gets a visible reason instead of silently
            // looking like nothing happened.
            if (!loaded) {
                placeholder.setText(buildPlaceholderText(newV));
            } else {
                placeholder.setText("Open a KeyStore to inspect.");
            }
            tabs.getTabs().get(0).setContent(new ScrollPane(buildOverviewContent(
                    composition.inspectVm().getInspected(), newV)));
        });
        composition.inspectVm().inspectedProperty().addListener((obs, oldV, newV) -> {
            rebuildInspectTree(tree);
            tabs.getTabs().get(0).setContent(new ScrollPane(buildOverviewContent(
                    newV, composition.inspectVm().getLoadResult())));
        });
        composition.inspectVm().selectedAliasProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });
        composition.inspectVm().currentEntryProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });
        composition.inspectVm().currentCertificateIndexProperty().addListener((obs, oldV, newV) -> {
            updateInspectDetailTabs(tabs);
        });

        return shell;
    }

    /**
     * Returns a placeholder text appropriate for the current load state. Used by
     * {@link #buildInspectView()} to give the user feedback when nothing is loaded — including
     * distinguishing "never loaded" from "failed to load" from "failed mid-load".
     */
    private static String buildPlaceholderText(KeyStoreLoadResult result) {
        if (result == null) {
            return "Open a KeyStore to inspect.";
        }
        if (result.isSuccess()) {
            return "Analyzing entries…";
        }
        if (result.failure() != null) {
            return "Load failed: " + result.failure().userMessage();
        }
        return "Load failed.";
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
            if (newV instanceof AliasTreeItem aliased) {
                composition.inspectVm().setSelectedAlias(aliased.alias());
            }
        });
        return tree;
    }

    private TabPane buildInspectTabs() {
        TabPane tabs = new TabPane();
        Tab overview = new Tab("Overview");
        overview.setClosable(false);
        overview.setContent(new ScrollPane(buildOverviewContent(
                composition.inspectVm().getInspected(), composition.inspectVm().getLoadResult())));
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
                TreeItem<String> leaf = new AliasTreeItem(n.alias(), n.alias() + " — " + n.entryType());
                group.getChildren().add(leaf);
            }
            root.getChildren().add(group);
        }
        // Re-select current alias.
        String alias = vm.getSelectedAlias();
        if (alias != null) {
            for (TreeItem<String> group : root.getChildren()) {
                for (TreeItem<String> leaf : group.getChildren()) {
                    if (leaf instanceof AliasTreeItem aliased && aliased.alias().equals(alias)) {
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

    Node buildOverviewContent(
            InspectedKeyStore inspected, KeyStoreLoadResult loadResult) {
        VBox box = new VBox(8);
        box.setPadding(new Insets(8));
        if (inspected == null) {
            String message = loadResult != null && loadResult.isSuccess()
                    ? "Analyzing entries…" : "No keystore loaded.";
            box.getChildren().add(new Label(message));
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

    private Node buildChainContent(InspectedEntry entry) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        if (entry == null) {
            box.getChildren().add(new Label("Select an alias to view its chain."));
            return box;
        }
        Label header = new Label(entry.certificates().size() + " certificate(s) in this chain.");
        header.setStyle("-fx-font-weight: bold;");
        box.getChildren().add(header);
        int i = 0;
        for (var c : entry.certificates()) {
            var a = c.analysis();
            String txt = (i) + ". " + a.subject() + "  |  issuer: " + a.issuer()
                    + "  |  valid to: " + a.validity().notAfter()
                    + "  |  " + (a.selfSigned().isFullySelfSigned() ? "self-signed" : "—");
            box.getChildren().add(new Label(txt));
            i++;
        }
        if (entry.certificates().isEmpty()) {
            box.getChildren().add(new Label("Entry has no certificate chain."));
        }
        return box;
    }

    private Node buildExtensionsContent(InspectedCertificate cert) {
        VBox box = new VBox(4);
        box.setPadding(new Insets(8));
        if (cert == null) {
            box.getChildren().add(new Label("Select a certificate to view its extensions."));
            return box;
        }
        var exts = cert.analysis().extensions();
        if (!exts.unrecognizedCriticalOids().isEmpty()) {
            Label warn = new Label("⚠ Unrecognized critical extensions: "
                    + String.join(", ", exts.unrecognizedCriticalOids()));
            warn.setStyle("-fx-text-fill: #a04000; -fx-font-weight: bold;");
            box.getChildren().add(warn);
        }
        addRow(box, "Basic Constraints (CA)",
                String.valueOf(exts.basicConstraints().isCa()));
        addRow(box, "Path Length",
                exts.basicConstraints().pathLength() == null
                        ? "n/a" : exts.basicConstraints().pathLength().toString());
        var ku = exts.keyUsage();
        java.util.List<String> kuBits = new java.util.ArrayList<>();
        if (ku.digitalSignature()) kuBits.add("digitalSignature");
        if (ku.nonRepudiation()) kuBits.add("nonRepudiation");
        if (ku.keyEncipherment()) kuBits.add("keyEncipherment");
        if (ku.dataEncipherment()) kuBits.add("dataEncipherment");
        if (ku.keyAgreement()) kuBits.add("keyAgreement");
        if (ku.keyCertSign()) kuBits.add("keyCertSign");
        if (ku.cRLSign()) kuBits.add("cRLSign");
        if (ku.encipherOnly()) kuBits.add("encipherOnly");
        if (ku.decipherOnly()) kuBits.add("decipherOnly");
        addRow(box, "Key Usage", String.join(", ", kuBits));
        addRow(box, "EKU OIDs", String.join(", ", exts.extendedKeyUsageOids()));
        addRow(box, "Subject Alternative Names", formatSans(exts.subjectAlternativeNames()));
        addRow(box, "Issuer Alternative Names", formatSans(exts.issuerAlternativeNames()));
        addRow(box, "Subject Key Identifier", String.valueOf(exts.subjectKeyIdentifier()));
        addRow(box, "Authority Key Identifier", String.valueOf(exts.authorityKeyIdentifier()));
        addRow(box, "Certificate Policy OIDs", String.join(", ", exts.certificatePolicyOids()));
        addRow(box, "CRL Distribution Points", String.join(", ", exts.crlDistributionPointUris()));
        addRow(box, "AIA OCSP", String.join(", ", exts.aiaOcspUris()));
        addRow(box, "AIA CA Issuer", String.join(", ", exts.aiaCaIssuerUris()));
        addRow(box, "Critical OIDs", String.join(", ", exts.criticalOids()));
        addRow(box, "Non-Critical OIDs", String.join(", ", exts.nonCriticalOids()));
        return box;
    }

    private Node buildPemContent(InspectedCertificate cert) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(8));
        if (cert == null) {
            box.getChildren().add(new Label("Select a certificate to view its PEM."));
            return box;
        }
        TextArea area = new TextArea(cert.analysis().pem());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(20);
        Button copy = new Button("Copy");
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getText());
            Clipboard.getSystemClipboard().setContent(content);
        });
        box.getChildren().addAll(copy, area);
        return box;
    }

    private static String formatSans(java.util.List<io.github.certtool.domain.certificate.SubjectAlternativeName> sans) {
        if (sans.isEmpty()) return "(none)";
        StringBuilder sb = new StringBuilder();
        for (var san : sans) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(typeName(san.generalNameType())).append('=').append(san.value());
        }
        return sb.toString();
    }

    private static String typeName(int type) {
        return switch (type) {
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_RFC822_NAME -> "email";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_DNS_NAME -> "DNS";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_DIRECTORY_NAME -> "dirName";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_URI -> "URI";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_IP_ADDRESS -> "IP";
            case io.github.certtool.domain.certificate.SubjectAlternativeName.TYPE_REGISTERED_ID -> "registeredID";
            default -> "type" + type;
        };
    }

    private static void addRow(VBox box, String label, String value) {
        HBox row = new HBox(8);
        Label l = new Label(label + ":");
        l.setStyle("-fx-font-weight: bold;");
        l.setMinWidth(220);
        row.getChildren().addAll(l, new Label(value == null || value.isEmpty() ? "—" : value));
        box.getChildren().add(row);
    }

    private void updateInspectDetailTabs(TabPane tabs) {
        InspectedEntry currentEntry = composition.inspectVm().getCurrentEntry();
        int index = composition.inspectVm().getCurrentCertificateIndex();
        int chainSize = currentEntry == null ? 0 : currentEntry.certificates().size();
        InspectedCertificate cert = null;
        if (currentEntry != null && !currentEntry.certificates().isEmpty()) {
            int safe = Math.min(index, currentEntry.certificates().size() - 1);
            if (safe < 0) safe = 0;
            cert = currentEntry.certificates().get(safe);
        }
        Tab certificate = tabs.getTabs().get(1);
        certificate.setContent(new ScrollPane(buildCertificateContent(cert, index, chainSize)));
        Tab chain = tabs.getTabs().get(2);
        chain.setContent(new ScrollPane(buildChainContent(currentEntry)));
        Tab extensions = tabs.getTabs().get(3);
        extensions.setContent(new ScrollPane(buildExtensionsContent(cert)));
        Tab pem = tabs.getTabs().get(4);
        pem.setContent(new ScrollPane(buildPemContent(cert)));
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

    /** Opens a file chooser and dispatches an auto-detecting load task on the background executor. */
    private void onOpenKeyStore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open KeyStore or TrustStore");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "JKS, BCFKS, and PKCS12 stores",
                        "*.jks", "*.bcfks", "*.keystore", "*.truststore", "*.p12", "*.pfx"),
                new FileChooser.ExtensionFilter("JKS", "*.jks"),
                new FileChooser.ExtensionFilter("BCFKS", "*.bcfks"),
                new FileChooser.ExtensionFilter("PKCS12", "*.p12", "*.pfx"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        java.io.File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            // User dismissed the chooser without selecting a file — expected, not an error.
            LOG.debug("Open KeyStore dialog cancelled by user.");
            return;
        }
        AutoDetectKeyStoreLoadTask task = composition.autoDetectLoadTask(selected.toPath());
        activateLoadTask(task);
        task.stateProperty().addListener((obs, oldS, newS) -> updateProgress(newS, task.getProgress()));
        task.messageProperty().addListener((obs, oldM, newM) -> {
            if (newM != null && !newM.isEmpty()) {
                statusMessage.setText(newM);
            }
        });
        task.setOnSucceeded(evt -> {
            if (task != currentLoadTask) {
                return;
            }
            KeyStoreLoadResult result = task.getValue();
            composition.inspectController().onLoadResult(result);
            composition.inspectController().applyInspection(null);
            AnalyzeKeyStoreTask analyze = composition.analyzeTask(result, ContentEncoding.BINARY);
            submitAnalyzeTask(analyze, task, () -> setStatus("Analyzed keystore or truststore."));
            statusMessage.setText("Loaded keystore or truststore.");
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
        input.setWrapText(true);
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
            composition.inspectController().applyInspection(null);
            AnalyzeKeyStoreTask analyze = composition.analyzeTask(result, ContentEncoding.BASE64);
            submitAnalyzeTask(analyze, currentLoadTask,
                    () -> setStatus("Analyzed pasted keystore."));
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
                KeyStoreContainerType.JKS,
                KeyStoreContainerType.JKS,
                KeyStoreContainerType.BCFKS,
                KeyStoreContainerType.PKCS12);
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
                composition.inspectController().applyInspection(null);
                AnalyzeKeyStoreTask analyze = composition.analyzeTask(result, ContentEncoding.BASE64);
                submitAnalyzeTask(analyze, task,
                        () -> setStatus("Analyzed pasted keystore."));
                setStatus("Pasted keystore loaded.");
            } else {
                setStatus("Could not load pasted keystore.");
            }
        });
    }

    private void bindLoadTask(Task<KeyStoreLoadResult> task, Runnable onSucceeded) {
        activateLoadTask(task);
        task.stateProperty().addListener((obs, oldS, newS) -> updateProgress(newS, task.getProgress()));
        task.messageProperty().addListener((obs, oldM, newM) -> {
            if (newM != null && !newM.isEmpty()) {
                setStatus(newM);
            }
        });
        task.setOnSucceeded(evt -> {
            if (task == currentLoadTask) {
                onSucceeded.run();
            }
        });
        task.setOnFailed(evt -> setStatus("Could not load pasted keystore."));
        composition.backgroundExecutor().submit(task);
    }

    void activateLoadTask(Task<?> task) {
        Task<?> previousLoad = currentLoadTask;
        if (previousLoad != null && previousLoad != task && !previousLoad.isDone()) {
            previousLoad.cancel();
        }
        AnalyzeKeyStoreTask previousAnalyze = currentAnalyzeTask;
        if (previousAnalyze != null && !previousAnalyze.isDone()) {
            previousAnalyze.cancel();
        }
        currentAnalyzeTask = null;
        currentLoadTask = task;
    }

    void submitAnalyzeTask(AnalyzeKeyStoreTask analyze, Task<?> loadTask, Runnable onAnalyzed) {
        if (loadTask != currentLoadTask) {
            analyze.cancel();
            return;
        }
        AnalyzeKeyStoreTask previousAnalyze = currentAnalyzeTask;
        if (previousAnalyze != null && previousAnalyze != analyze && !previousAnalyze.isDone()) {
            previousAnalyze.cancel();
        }
        currentLoadTask = loadTask;
        currentAnalyzeTask = analyze;
        bindAnalyzeTask(analyze, loadTask, onAnalyzed);
        composition.backgroundExecutor().submit(analyze);
    }

    private void bindAnalyzeTask(
            AnalyzeKeyStoreTask analyze, Task<?> loadTask, Runnable onAnalyzed) {
        analyze.stateProperty().addListener((obs, oldS, newS) ->
                updateProgress(newS, analyze.getProgress()));
        analyze.messageProperty().addListener((obs, oldM, newM) -> {
            if (newM != null && !newM.isEmpty()) {
                setStatus(newM);
            }
        });
        analyze.setOnSucceeded(evt -> {
            if (loadTask != currentLoadTask || analyze != currentAnalyzeTask) {
                return;
            }
            composition.inspectController().applyInspection(analyze.getValue());
            onAnalyzed.run();
        });
    }

    Task<?> currentLoadTask() {
        return currentLoadTask;
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
        if (progress == null) {
            return;
        }
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

    /**
     * A leaf {@link TreeItem} that remembers its alias alongside the display string. JavaFX's
     * {@code TreeItem} is not a {@code Node} and has no {@code setUserData}; co-locating the
     * alias on the leaf lets the selection listener and the re-select-after-rebuild path match
     * by alias rather than by the brittle "display string starts with alias + separator" check.
     */
    private static final class AliasTreeItem extends TreeItem<String> {
        private final String alias;

        AliasTreeItem(String alias, String display) {
            super(display);
            this.alias = alias;
        }

        String alias() {
            return alias;
        }
    }
}
