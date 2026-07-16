package io.github.certtool.app.view;

import io.github.certtool.app.controller.ConvertWizardController;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.result.ConversionResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

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
            default -> stepCache.get(step); // Tasks 7–10 register themselves
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
}