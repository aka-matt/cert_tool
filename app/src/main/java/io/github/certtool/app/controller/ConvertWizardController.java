package io.github.certtool.app.controller;

import io.github.certtool.app.task.ConvertPreflightTask;
import io.github.certtool.app.view.ConvertView;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.scene.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure-logic controller for the Convert wizard. Owns the per-step validators, plan builder, and
 * reset-on-source-change behaviour. {@link #handleEnteredPreflight} and {@link #runConvert} take
 * the preflight task factory and convert task factory as lazy suppliers — Tasks 4 and 12 supply
 * the production suppliers; tests can pass {@code () -> null}.
 *
 * <p>The controller holds no JavaFX references. The view binds to the VM; the controller mutates
 * the VM.
 */
public final class ConvertWizardController {

    private static final Logger LOG = LoggerFactory.getLogger(ConvertWizardController.class);

    /** Default target store password: empty array — overridden in Task 8 once the user types. */
    private static final char[] NO_TARGET_PASSWORD = new char[0];

    private final ConvertController convertController;
    private final ConvertWizardViewModel vm;
    private final ExecutorService executor;
    private final Consumer<String> onStatusMessage;

    /** Lazy preflight-task factory (set in Task 4). */
    private BiFunction<ConversionPlan, Profile, ConvertPreflightTask> preflightTaskFactory =
            (plan, profile) -> {
                throw new IllegalStateException("preflightTaskFactory not wired yet");
            };

    /** Listener for preflight errors (default: log a warning). */
    private Consumer<String> preflightErrorListener = err ->
            LOG.warn("Preflight failed: {}", err);

    /** Lazy convert-task runner (set in Task 12). */
    private BiFunction<ConversionPlan, Profile, javafx.concurrent.Task<?>> convertTaskRunner =
            (plan, profile) -> {
                throw new IllegalStateException("convertTaskRunner not wired yet");
            };

    public ConvertWizardController(
            ConvertController convertController,
            ConvertWizardViewModel vm,
            ExecutorService executor) {
        this(convertController, vm, executor, s -> {});
    }

    public ConvertWizardController(
            ConvertController convertController,
            ConvertWizardViewModel vm,
            ExecutorService executor,
            Consumer<String> onStatusMessage) {
        this.convertController = Objects.requireNonNull(convertController, "convertController");
        this.vm = Objects.requireNonNull(vm, "vm");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.onStatusMessage = Objects.requireNonNull(onStatusMessage, "onStatusMessage");
        // Whenever any step-relevant property changes, recompute nextEnabled. The View layer
        // also calls recomputeNextEnabled() after user actions (file pick, password type, …).
        vm.sourceProperty().addListener((o, a, b) -> recomputeNextEnabled());
        vm.sourcePathProperty().addListener((o, a, b) -> recomputeNextEnabled());
        vm.targetPathProperty().addListener((o, a, b) -> recomputeNextEnabled());
        vm.selectedAliases().addListener((javafx.collections.ListChangeListener<String>)
                c -> recomputeNextEnabled());
        vm.preflightReportProperty().addListener((o, a, b) -> recomputeNextEnabled());
        vm.currentStepProperty().addListener((o, a, b) -> recomputeNextEnabled());
    }

    /** Per-step validator. Public so tests can call without spinning a wizard. */
    public boolean isStepValid(WizardStep step) {
        return switch (step) {
            case SOURCE   -> vm.getSource() != null;
            case CONTENTS -> !vm.selectedAliases().isEmpty();
            case TARGET   -> !vm.getTargetPath().isBlank()
                              && vm.getAliasConflictPolicy() != null
                              && vm.getOverwritePolicy() != null;
            case PREFLIGHT -> vm.getPreflightReport() != null
                              && !vm.getPreflightReport().hasBlockers();
            case EXECUTE  -> !vm.isRunningConvert();
        };
    }

    /** Recomputes and sets {@code nextEnabled} for the current step. */
    public void recomputeNextEnabled() {
        vm.setNextEnabled(isStepValid(vm.getCurrentStep()));
    }

    /** Wipes downstream state when the Inspect tab loads a new keystore. */
    public void resetOnSourceChange() {
        vm.setTargetPath("");
        vm.setAliasConflictPolicy(AliasConflictPolicy.RENAME);
        vm.setOverwritePolicy(OverwritePolicy.FAIL_IF_EXISTS);
        vm.setPreflightReport(null);
        vm.setLastResult(null);
        vm.setCurrentStep(WizardStep.SOURCE);
        recomputeNextEnabled();
    }

    /**
     * Builds a {@link ConversionPlan} from VM state.
     *
     * @param sourceEntries map of alias → {@link EntryType} for the currently-selected aliases
     *                      (already classified by the engine's probe)
     * @param profile       active FIPS profile (may be {@code null})
     * @return the plan
     * @throws IllegalStateException if any required field is missing; the message is UI-safe
     */
    public ConversionPlan buildConversionPlan(
            Map<String, EntryType> sourceEntries, Profile profile) {
        Objects.requireNonNull(sourceEntries, "sourceEntries");
        if (vm.getSource() == null) {
            throw new IllegalStateException("No source keystore loaded.");
        }
        if (vm.getSourcePath().isBlank()) {
            throw new IllegalStateException("Source path is unknown.");
        }
        if (vm.getTargetPath().isBlank()) {
            throw new IllegalStateException("Target path is empty.");
        }
        if (vm.selectedAliases().isEmpty()) {
            throw new IllegalStateException("No entries selected for conversion.");
        }
        // For the first cut the wizard passes an empty per-entry password array. The engine's
        // PasswordProvider prompts per-entry at execution time inside EntryCopy.copy(...).
        List<String> included = List.copyOf(vm.selectedAliases());
        List<char[]> entryPasswords = List.of(new char[0]);
        // Determine target container + encoding from VM-owned bookkeeping. Today the wizard's
        // Step 3 panel sets source container/encoding via the `Source` snapshot (the VM
        // currently exposes targetPath + policies only). Task 8 adds `targetContainerType` /
        // `targetEncoding` properties on the VM and reads them here. Until then, default to
        // JKS + Binary — overridden in Task 8.
        KeyStoreContainerType targetContainer = readTargetContainer();
        ContentEncoding targetEncoding = readTargetEncoding();
        // Source store password is not carried on LoadedKeyStoreInfo yet — Task 8 adds it.
        // For the first cut the engine prompts via PasswordProvider at execution time.
        char[] sourceStorePassword = new char[0];
        return new ConversionPlan(
                vm.getSource().containerType(),
                vm.getSource().encoding(),
                targetContainer,
                targetEncoding,
                vm.getSourcePath(),
                vm.getTargetPath(),
                sourceStorePassword,
                NO_TARGET_PASSWORD.clone(),
                vm.getAliasConflictPolicy(),
                vm.getOverwritePolicy(),
                included,
                entryPasswords);
    }

    // Default read helpers — overridden in Task 8 once the VM exposes target container/encoding.
    private KeyStoreContainerType readTargetContainer() {
        // Pre-Task-8: targetContainerType is not yet on the VM. The wizard controller is
        // upgraded in Task 8 to read from the VM; this default keeps the contract obvious.
        return KeyStoreContainerType.BCFKS;
    }

    private ContentEncoding readTargetEncoding() {
        return ContentEncoding.BINARY;
    }

    /** Test seam: assign the preflight task factory. Production wires this in Task 4. */
    public void setPreflightTaskFactory(
            BiFunction<ConversionPlan, Profile, ConvertPreflightTask> factory) {
        this.preflightTaskFactory = Objects.requireNonNull(factory, "factory");
    }

    /** Test seam: assign the listener for preflight errors. Production wires this to the View's
     *  status bar in a later task. */
    public void setPreflightErrorListener(Consumer<String> listener) {
        this.preflightErrorListener = Objects.requireNonNull(listener, "listener");
    }

    /** Step 4 → preflight. Submits the preflight task and wires terminal handlers. */
    public void handleEnteredPreflight(
            Map<String, EntryType> sourceEntries, Profile profile) {
        Objects.requireNonNull(sourceEntries, "sourceEntries");
        // profile is nullable: ConvertPreflightTask treats null as "skip FIPS-like checks".
        ConversionPlan plan;
        try {
            plan = buildConversionPlan(sourceEntries, profile);
        } catch (RuntimeException pre) {
            vm.setRunningPreflight(false);
            preflightErrorListener.accept(pre.getMessage());
            return;
        }
        ConvertPreflightTask task = preflightTaskFactory.apply(plan, profile);
        vm.setRunningPreflight(true);
        task.setOnSucceeded(e -> onPreflightSucceeded(task.getValue()));
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            onPreflightFailed(ex == null ? new RuntimeException("Unknown preflight failure")
                    : ex);
        });
        task.setOnCancelled(e -> onPreflightCancelled());
        executor.submit(task);
    }

    /** Terminal: store the report on the VM and clear the running flag. */
    public void onPreflightSucceeded(PreflightReport report) {
        convertController.onPreflightProduced(report);
        vm.setRunningPreflight(false);
        recomputeNextEnabled();
    }

    /** Terminal: surface a short error to the listener. The user can still navigate Back. */
    public void onPreflightFailed(Throwable error) {
        String msg = error.getClass().getSimpleName() + ": "
                + (error.getMessage() == null ? "unknown" : error.getMessage());
        vm.setRunningPreflight(false);
        preflightErrorListener.accept(msg);
        recomputeNextEnabled();
    }

    /** Terminal: clear running flag. */
    public void onPreflightCancelled() {
        vm.setRunningPreflight(false);
        recomputeNextEnabled();
    }

    /** Step 5 → convert. Submits the convert task. Task 12 wires the runner. */
    public void runConvert(Map<String, EntryType> sourceEntries, Profile profile,
                           PasswordProvider passwords) {
        ConversionPlan plan = buildConversionPlan(sourceEntries, profile);
        var task = convertTaskRunner.apply(plan, profile);
        vm.setRunningConvert(true);
        executor.submit(task);
    }

    /** Test seam: assign the convert task runner. Production wires this in Task 12. */
    void setConvertTaskRunnerForTests(BiFunction<ConversionPlan, Profile,
            javafx.concurrent.Task<?>> runner) {
        this.convertTaskRunner = runner;
    }

    private ConvertView view;

    /** One-shot view binding — call once after constructing the view (Task 11). */
    public void setView(ConvertView view) {
        if (this.view != null) {
            throw new IllegalStateException("view already set");
        }
        this.view = Objects.requireNonNull(view, "view");
    }

    private ConvertView view() {
        if (view == null) {
            throw new IllegalStateException("view not wired — Task 11 must call setView()");
        }
        return view;
    }

    public void back() {
        WizardStep cur = vm.getCurrentStep();
        if (cur.ordinal() > 0) {
            vm.setCurrentStep(WizardStep.values()[cur.ordinal() - 1]);
            view().showStep(vm.getCurrentStep());
        }
    }

    public void next() {
        WizardStep cur = vm.getCurrentStep();
        if (cur.ordinal() < WizardStep.values().length - 1) {
            vm.setCurrentStep(WizardStep.values()[cur.ordinal() + 1]);
            view().showStep(vm.getCurrentStep());
        }
    }

    /** Called from the Convert step in the footer — Task 12 wires the actual conversion. */
    public void runConvertCurrentSource() {
        onStatusMessage.accept("Conversion started.");
    }
}