package io.github.certtool.app.view;

import io.github.certtool.app.controller.ConvertWizardController;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import java.io.File;

/**
 * Top-level UI builder for the Convert wizard. Builds a {@link BorderPane} with:
 *
 * <ol>
 *   <li>A {@link #buildStepListHeader step-list header} (5 dots).</li>
 *   <li>A center pane whose child swaps when {@code currentStep} changes.</li>
 *   <li>A footer with {@code Back / Next / Close} (and {@code Convert} on the EXECUTE step).</li>
 * </ol>
 *
 * <p>The view holds no business state — it binds to {@link ConvertWizardViewModel} and calls
 * methods on {@link ConvertWizardController}. Step panels are cached per {@link WizardStep}.
 */
public final class ConvertView {

    private final ConvertWizardViewModel vm;
    private final ConvertWizardController wizard;
    private final ExecutorService executor;
    private final Supplier<Runnable> preflightRunner;
    private final Consumer<ConversionResult> onConvertSucceeded;
    private final Consumer<String> onStatusMessage;

    private final BorderPane root = new BorderPane();
    private final Map<WizardStep, Node> stepCache = new EnumMap<>(WizardStep.class);
    private final Button backButton = new Button("← Back");
    private final Button nextButton = new Button("Next →");
    private final Button convertButton = new Button("Convert");
    private final Button closeButton = new Button("Close");
    private final Button reRunPreflightButton = new Button("Re-run preflight");
    private final Label stepTitleLabel = new Label();

    public ConvertView(
            ConvertWizardViewModel vm,
            ConvertWizardController wizard,
            ExecutorService executor,
            Supplier<Runnable> preflightRunner,
            Consumer<ConversionResult> onConvertSucceeded,
            Consumer<String> onStatusMessage) {
        this.vm = Objects.requireNonNull(vm, "vm");
        this.wizard = Objects.requireNonNull(wizard, "wizard");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.preflightRunner = Objects.requireNonNull(preflightRunner, "preflightRunner");
        this.onConvertSucceeded = Objects.requireNonNull(onConvertSucceeded, "onConvertSucceeded");
        this.onStatusMessage = Objects.requireNonNull(onStatusMessage, "onStatusMessage");
        build();
    }

    public Node root() { return root; }

    private void build() {
        root.setTop(buildStepListHeader());
        root.setCenter(buildCenterPlaceholder());
        root.setBottom(buildFooter());
        wireNavigation();
        bindStepTitle();
        showStep(vm.getCurrentStep());
    }

    private Node buildStepListHeader() {
        HBox header = new HBox(8);
        header.setPadding(new Insets(8, 12, 8, 12));
        header.setAlignment(Pos.CENTER_LEFT);
        for (WizardStep step : WizardStep.values()) {
            Label dot = new Label("•  " + step.title());
            header.getChildren().add(dot);
            Label sep = new Label("›");
            sep.setStyle("-fx-opacity: 0.5");
            header.getChildren().add(sep);
        }
        // Trailing close button on the right side of the header.
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);
        return header;
    }

    private Node buildCenterPlaceholder() {
        // Concrete step panels are built by Tasks 6–10 and swapped into this Node. For the shell
        // task we ship a placeholder so the binding tests pass.
        VBox placeholder = new VBox(new Label("Convert wizard — step panel placeholder."));
        placeholder.setId("convert-step-placeholder");
        return placeholder;
    }

    private Node buildFooter() {
        HBox footer = new HBox(8);
        footer.setPadding(new Insets(8, 12, 8, 12));
        footer.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(backButton, spacer, stepTitleLabel, new Region(),
                reRunPreflightButton, nextButton, convertButton);
        // Hide convertButton by default; enable only on EXECUTE step.
        convertButton.setVisible(false);
        reRunPreflightButton.setVisible(false);
        return footer;
    }

    private void wireNavigation() {
        backButton.setOnAction(e -> wizard.back());
        nextButton.setOnAction(e -> wizard.next());
        convertButton.setOnAction(e -> wizard.runConvertCurrentSource());
        closeButton.setOnAction(e -> onStatusMessage.accept("Convert wizard closed."));
        reRunPreflightButton.setOnAction(e -> preflightRunner.get());

        vm.currentStepProperty().addListener((o, oldStep, newStep) -> {
            boolean isExecute = newStep == WizardStep.EXECUTE;
            boolean isPreflight = newStep == WizardStep.PREFLIGHT;
            convertButton.setVisible(isExecute);
            nextButton.setVisible(!isExecute);
            reRunPreflightButton.setVisible(isPreflight);
            // Automatically show the panel for the new step.
            if (newStep != null) {
                showStep(newStep);
            }
        });
    }

    private void bindStepTitle() {
        stepTitleLabel.textProperty().bind(vm.stepTitleProperty());
    }

    /** Exposed so step-panel builders (Tasks 6–10) can register themselves. */
    public void registerStep(WizardStep step, Node node) {
        stepCache.put(step, node);
        if (vm.getCurrentStep() == step) {
            root.setCenter(node);
        }
    }

    /** Exposed for the controller to call when entering a step. */
    public void showStep(WizardStep step) {
        Node panel = switch (step) {
            case SOURCE -> sourcePanel();
            case CONTENTS -> contentsPanel();
            case TARGET -> targetPanel();
            default -> stepCache.get(step); // Tasks 9–10 register themselves
        };
        if (panel == null) {
            panel = buildCenterPlaceholder();
        }
        root.setCenter(panel);
    }

    /** Public so the controller can request a re-render; first-call builds and caches. */
    public Node sourcePanel() {
        return stepCache.computeIfAbsent(WizardStep.SOURCE, step -> buildSourcePanel());
    }

    private Node buildSourcePanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setId("convert-step-source");
        // Subscribe to source + sourcePath changes to rebuild the labels when they change.
        Label containerLabel = new Label();
        Label encodingLabel = new Label();
        Label pathLabel = new Label();
        Label entryCountLabel = new Label();
        Runnable refresh = () -> {
            var src = vm.getSource();
            if (src == null) {
                containerLabel.setText("");
                encodingLabel.setText("");
                pathLabel.setText("");
                entryCountLabel.setText("");
                return;
            }
            containerLabel.setText("Container: " + src.containerType());
            encodingLabel.setText("Encoding: " + src.encoding());
            pathLabel.setText("Source path: " + (src.sourcePath() != null ? src.sourcePath() : "(in-memory)"));
            entryCountLabel.setText("Entries: " + vm.sourceAliases().size());
        };
        refresh.run();
        vm.sourceProperty().addListener((o, a, b) -> refresh.run());
        vm.sourceAliases().addListener((javafx.collections.ListChangeListener<String>) c -> refresh.run());

        box.getChildren().addAll(
                new Label("Step 1 — Source"),
                new Label("Loaded keystore summary:"),
                containerLabel, encodingLabel, pathLabel, entryCountLabel);
        // Empty-state line, hidden when source is non-null:
        Label empty = new Label("Open a keystore on the Inspect tab to populate this wizard.");
        empty.setId("convert-step-source-empty");
        empty.visibleProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> vm.getSource() == null, vm.sourceProperty()));
        empty.managedProperty().bind(empty.visibleProperty());
        box.getChildren().add(empty);

        // Register the panel for showStep() to swap it in:
        registerStep(WizardStep.SOURCE, box);
        return box;
    }

    /** Public so the controller can request a re-render; first-call builds and caches. */
    public Node contentsPanel() {
        return stepCache.computeIfAbsent(WizardStep.CONTENTS, step -> buildContentsPanel());
    }

    private Node buildContentsPanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setId("convert-step-contents");

        TableView<EntryRow> table = new TableView<>();
        TableColumn<EntryRow, Boolean> includeCol = new TableColumn<>("Include");
        includeCol.setCellValueFactory(c -> c.getValue().include);
        includeCol.setCellFactory(CheckBoxTableCell.forTableColumn(includeCol));
        includeCol.setEditable(true);

        TableColumn<EntryRow, String> aliasCol = new TableColumn<>("Alias");
        aliasCol.setCellValueFactory(c -> c.getValue().alias);

        TableColumn<EntryRow, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> c.getValue().type);

        table.getColumns().addAll(includeCol, aliasCol, typeCol);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        for (String alias : vm.sourceAliases()) {
            table.getItems().add(new EntryRow(alias, "<unknown>", true));
        }

        Button selectAll = new Button("Select all");
        selectAll.setOnAction(e -> {
            for (EntryRow r : table.getItems()) {
                r.include.set(true);
            }
            rebuildSelectedAliases(table);
        });
        Button deselectAll = new Button("Deselect all");
        deselectAll.setOnAction(e -> {
            for (EntryRow r : table.getItems()) {
                r.include.set(false);
            }
            rebuildSelectedAliases(table);
        });
        Button invert = new Button("Invert");
        invert.setOnAction(e -> {
            for (EntryRow r : table.getItems()) {
                r.include.set(!r.include.get());
            }
            rebuildSelectedAliases(table);
        });
        HBox actions = new HBox(8, selectAll, deselectAll, invert);

        Label banner = new Label();
        banner.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() -> {
            long sel = table.getItems().stream().filter(r -> r.include.get()).count();
            return sel + " of " + table.getItems().size() + " entries selected.";
        }, vm.selectedAliases()));
        banner.setId("convert-step-contents-banner");

        box.getChildren().addAll(new Label("Step 2 — Contents"), actions, table, banner);
        registerStep(WizardStep.CONTENTS, box);
        return box;
    }

    private void rebuildSelectedAliases(TableView<EntryRow> table) {
        vm.selectedAliases().setAll(
                table.getItems().stream()
                        .filter(r -> r.include.get())
                        .map(r -> r.alias.get())
                        .toList());
    }

    /** Public so the controller can request a re-render; first-call builds and caches. */
    public Node targetPanel() {
        return stepCache.computeIfAbsent(WizardStep.TARGET, step -> buildTargetPanel());
    }

    private Node buildTargetPanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(16));
        box.setId("convert-step-target");

        // Target container combo
        ComboBox<KeyStoreContainerType> containerCombo = new ComboBox<>();
        containerCombo.getItems().addAll(KeyStoreContainerType.JKS,
                KeyStoreContainerType.PKCS12, KeyStoreContainerType.BCFKS);
        containerCombo.valueProperty().bindBidirectional(vm.targetContainerTypeProperty());

        // Target encoding combo
        ComboBox<ContentEncoding> encodingCombo = new ComboBox<>();
        encodingCombo.getItems().addAll(ContentEncoding.BINARY, ContentEncoding.BASE64);
        encodingCombo.valueProperty().bindBidirectional(vm.targetEncodingProperty());

        // Target path with Browse
        TextField pathField = new TextField();
        pathField.textProperty().bindBidirectional(vm.targetPathProperty());
        Button browse = new Button("Browse…");
        browse.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose target keystore");
            fc.setInitialFileName(suggestTargetFilename(vm.getTargetPath()));
            File chosen = fc.showSaveDialog(
                    root.getScene() == null ? null : root.getScene().getWindow());
            if (chosen != null) {
                vm.setTargetPath(chosen.toString());
            }
        });

        // Target store password
        PasswordField targetPassword = new PasswordField();
        targetPassword.setId("convert-target-password");
        // Push password changes to the wizard controller.
        targetPassword.textProperty().addListener((o, a, b) ->
                wizard.setTargetStorePassword(targetPassword.getText().toCharArray()));

        // Show/hide password toggle
        CheckBox showTargetPwd = new CheckBox("Show password");
        BooleanProperty showPwd = new SimpleBooleanProperty(false);
        TextField targetPasswordPlain = new TextField();
        targetPasswordPlain.textProperty().bindBidirectional(targetPassword.textProperty());
        showPwd.addListener((o, a, b) -> {
            if (b) {
                targetPassword.textProperty().unbind();
                targetPassword.setText(targetPasswordPlain.getText());
                targetPasswordPlain.textProperty().bindBidirectional(targetPassword.textProperty());
            } else {
                targetPasswordPlain.textProperty().unbind();
                targetPassword.textProperty().bindBidirectional(targetPasswordPlain.textProperty());
            }
        });
        showTargetPwd.selectedProperty().bindBidirectional(showPwd);

        // Base64 options
        ComboBox<Integer> lineWidthCombo = new ComboBox<>();
        lineWidthCombo.getItems().addAll(32, 48, 64, 76, 100);
        lineWidthCombo.setValue(vm.getTargetBase64Options().lineWidth());
        lineWidthCombo.valueProperty().addListener((o, a, b) -> {
            int w = (b == null) ? 64 : b;
            vm.setTargetBase64Options(new ConvertWizardViewModel.Base64Options(
                    w, vm.getTargetBase64Options().wrapHeaders()));
        });
        CheckBox wrapHeaders = new CheckBox("Wrap with PEM-style BEGIN/END headers");
        wrapHeaders.setSelected(vm.getTargetBase64Options().wrapHeaders());
        wrapHeaders.selectedProperty().addListener((o, a, b) -> {
            int w = vm.getTargetBase64Options().lineWidth();
            vm.setTargetBase64Options(new ConvertWizardViewModel.Base64Options(w, b));
        });
        lineWidthCombo.disableProperty().bind(
                javafx.beans.binding.Bindings.createBooleanBinding(
                        () -> vm.getTargetEncoding() != ContentEncoding.BASE64,
                        vm.targetEncodingProperty()));
        wrapHeaders.disableProperty().bind(lineWidthCombo.disableProperty());

        // Alias conflict + overwrite policies
        ComboBox<AliasConflictPolicy> aliasPolicyCombo = new ComboBox<>();
        aliasPolicyCombo.getItems().addAll(AliasConflictPolicy.values());
        aliasPolicyCombo.valueProperty().bindBidirectional(vm.aliasConflictPolicyProperty());

        ComboBox<OverwritePolicy> overwriteCombo = new ComboBox<>();
        overwriteCombo.getItems().addAll(OverwritePolicy.values());
        overwriteCombo.valueProperty().bindBidirectional(vm.overwritePolicyProperty());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Target container:"), 0, 0);
        grid.add(containerCombo, 1, 0);
        grid.add(new Label("Target encoding:"), 0, 1);
        grid.add(encodingCombo, 1, 1);
        grid.add(new Label("Target path:"), 0, 2);
        grid.add(pathField, 1, 2);
        grid.add(browse, 2, 2);
        grid.add(new Label("Target store password:"), 0, 3);
        grid.add(targetPassword, 1, 3);
        grid.add(showTargetPwd, 1, 4);
        grid.add(new Label("Base64 line width:"), 0, 5);
        grid.add(lineWidthCombo, 1, 5);
        grid.add(wrapHeaders, 1, 6);
        grid.add(new Label("Alias conflict policy:"), 0, 7);
        grid.add(aliasPolicyCombo, 1, 7);
        grid.add(new Label("Overwrite policy:"), 0, 8);
        grid.add(overwriteCombo, 1, 8);

        box.getChildren().addAll(new Label("Step 3 — Target"), grid);
        registerStep(WizardStep.TARGET, box);
        return box;
    }

    private String suggestTargetFilename(String currentPath) {
        if (currentPath != null && !currentPath.isBlank()) {
            java.nio.file.Path p = java.nio.file.Paths.get(currentPath);
            return p.getFileName().toString();
        }
        return "target.keystore";
    }

    /** Local row type for the contents table. Public to keep tests honest. */
    public static final class EntryRow {
        public final StringProperty alias = new SimpleStringProperty();
        public final StringProperty type = new SimpleStringProperty();
        public final javafx.beans.property.BooleanProperty include = new SimpleBooleanProperty();

        public EntryRow(String alias, String type, boolean include) {
            this.alias.set(alias);
            this.type.set(type);
            this.include.set(include);
        }
    }
}