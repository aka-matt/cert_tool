package io.github.certtool.app.controller;

import io.github.certtool.app.task.ConvertPreflightTask;
import io.github.certtool.app.view.ConvertView;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.keystorecore.password.StorePasswordRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
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

    private final AtomicReference<char[]> targetPasswordRef =
            new AtomicReference<>(new char[0]);

    private final ConvertController convertController;
    private final ConvertWizardViewModel vm;
    private final ExecutorService executor;
    private final Consumer<String> onStatusMessage;
    private final Consumer<ConversionResult> onConvertSucceeded;
    private PasswordProvider passwordProvider;
    private char[] entryPasswordOverride = new char[0];

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

    /** Tracks the in-flight convert task for re-entry guard and test seam. */
    private volatile javafx.concurrent.Task<?> currentTask;

    /** Test seam: returns the currently-executing convert task, or null. */
    public javafx.concurrent.Task<?> currentTaskForTest() {
        return currentTask;
    }

    public ConvertWizardController(
            ConvertController convertController,
            ConvertWizardViewModel vm,
            ExecutorService executor) {
        this(convertController, vm, executor, r -> {}, s -> {});
    }

    public ConvertWizardController(
            ConvertController convertController,
            ConvertWizardViewModel vm,
            ExecutorService executor,
            Consumer<String> onStatusMessage) {
        this(convertController, vm, executor, r -> {}, onStatusMessage);
    }

    public ConvertWizardController(
            ConvertController convertController,
            ConvertWizardViewModel vm,
            ExecutorService executor,
            Consumer<ConversionResult> onConvertSucceeded,
            Consumer<String> onStatusMessage) {
        this.convertController = Objects.requireNonNull(convertController, "convertController");
        this.vm = Objects.requireNonNull(vm, "vm");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.onConvertSucceeded = Objects.requireNonNull(onConvertSucceeded, "onConvertSucceeded");
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
                              && vm.getOverwritePolicy() != null
                              && !(vm.getTargetContainerType() == KeyStoreContainerType.BCFKS
                                   && targetPasswordRef.get().length == 0);
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
     * Receives the target store password from the Target step view.
     * Called by the view whenever the password field changes.
     */
    public void setTargetStorePassword(char[] pwd) {
        if (pwd == null || pwd.length == 0) {
            targetPasswordRef.set(new char[0]);
            return;
        }
        targetPasswordRef.set(pwd.clone());
        recomputeNextEnabled();
    }

    public char[] getTargetStorePassword() {
        char[] p = targetPasswordRef.get();
        return p == null ? new char[0] : p.clone();
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
        // Determine target container + encoding from VM-owned bookkeeping.
        KeyStoreContainerType targetContainer = vm.getTargetContainerType();
        ContentEncoding targetEncoding = vm.getTargetEncoding();
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
                getTargetStorePassword(),
                vm.getAliasConflictPolicy(),
                vm.getOverwritePolicy(),
                included,
                entryPasswords);
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

    /** Terminal: surface a short error to the listener. The user can still navigate Back.
     *  Per CLAUDE.md §2 rule 10, exception message text is redacted — only the error class
     *  is surfaced to avoid leaking sensitive content into user-visible text. */
    public void onPreflightFailed(Throwable error) {
        String simpleName = error.getClass().getSimpleName();
        String msg = simpleName + " (message redacted for security)";
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
        ConversionPlan plan;
        try {
            plan = buildConversionPlan(sourceEntries, profile);
        } catch (RuntimeException pre) {
            onStatusMessage.accept("Cannot build plan: " + pre.getMessage());
            return;
        }
        if (vm.isRunningConvert()) {
            return;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        javafx.concurrent.Task<ConversionResult> task =
                (javafx.concurrent.Task) convertTaskRunner.apply(plan, profile);
        vm.setRunningConvert(true);
        this.currentTask = task;
        task.setOnSucceeded(e -> {
            if (currentTask == task) currentTask = null;
            ConversionResult r = task.getValue();
            vm.setLastResult(r);
            vm.setRunningConvert(false);
            onConvertSucceeded.accept(r);
            recomputeNextEnabled();
        });
        task.setOnFailed(e -> {
            if (currentTask == task) currentTask = null;
            vm.setRunningConvert(false);
            String failureNote = task.getException() == null
                    ? "unknown" : task.getException().getClass().getSimpleName();
            onStatusMessage.accept("Conversion failed: " + failureNote
                    + " (message redacted for security)");
            recomputeNextEnabled();
        });
        task.setOnCancelled(e -> {
            if (currentTask == task) currentTask = null;
            vm.setRunningConvert(false);
            recomputeNextEnabled();
        });
        executor.submit(task);
    }

    /** Test seam: assign the convert task runner. Production wires this in Task 12. */
    void setConvertTaskRunnerForTests(BiFunction<ConversionPlan, Profile,
            javafx.concurrent.Task<?>> runner) {
        this.convertTaskRunner = runner;
    }

    /**
     * Stores the password provider for lazy lookup at execution time.
     * Called by the composition root in Task 13.
     */
    public void setPasswordProvider(PasswordProvider provider) {
        this.passwordProvider = provider;
    }

    /**
     * Stores the per-entry key-password override from the preflight step's PasswordField.
     * Empty array means "prompt per-entry at execution time".
     */
    public void setEntryPasswordOverride(char[] override) {
        if (this.entryPasswordOverride != null) {
            Arrays.fill(this.entryPasswordOverride, '\0');
        }
        this.entryPasswordOverride = override == null ? new char[0] : override.clone();
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

    /** Called from the Convert step in the footer. Wires PasswordProvider + plan build. */
    public void runConvertCurrentSource() {
        if (passwordProvider == null) {
            onStatusMessage.accept("Password provider not available — cannot run conversion.");
            return;
        }
        // Lazily obtain source store password at execution time.
        var srcDesc = vm.getSource() != null
                ? vm.getSource().containerType() + " source"
                : "source keystore";
        char[] sourceStorePassword = passwordProvider.requestStorePassword(
                new StorePasswordRequest(srcDesc, 1, 3));
        if (sourceStorePassword == null) {
            // User cancelled
            onStatusMessage.accept("Conversion cancelled — source password not provided.");
            return;
        }
        // Build per-entry password list: one entry per selected alias.
        // If override is non-empty use it for all entries; otherwise let the engine prompt per-entry.
        List<char[]> entryPasswords = vm.selectedAliases().stream()
                .map(alias -> entryPasswordOverride.length > 0
                        ? entryPasswordOverride.clone()
                        : new char[0])
                .toList();
        // Patch the plan with the real passwords.
        var plan = buildConversionPlanWithPasswords(sourceStorePassword.clone(), entryPasswords);
        // Rest of conversion via the runner.
        runConvert(plan);
    }

    private void runConvert(ConversionPlan plan) {
        if (vm.isRunningConvert()) {
            return;
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        javafx.concurrent.Task<ConversionResult> task =
                (javafx.concurrent.Task) convertTaskRunner.apply(plan, null);
        vm.setRunningConvert(true);
        this.currentTask = task;
        task.setOnSucceeded(e -> {
            if (currentTask == task) currentTask = null;
            ConversionResult r = task.getValue();
            vm.setLastResult(r);
            vm.setRunningConvert(false);
            onConvertSucceeded.accept(r);
            recomputeNextEnabled();
        });
        task.setOnFailed(e -> {
            if (currentTask == task) currentTask = null;
            vm.setRunningConvert(false);
            String failureNote = task.getException() == null
                    ? "unknown" : task.getException().getClass().getSimpleName();
            onStatusMessage.accept("Conversion failed: " + failureNote
                    + " (message redacted for security)");
            recomputeNextEnabled();
        });
        task.setOnCancelled(e -> {
            if (currentTask == task) currentTask = null;
            vm.setRunningConvert(false);
            recomputeNextEnabled();
        });
        executor.submit(task);
    }

    /**
     * Builds the ConversionPlan using real passwords obtained at execution time.
     * Entry passwords are a parallel list with one entry per selected alias.
     */
    private ConversionPlan buildConversionPlanWithPasswords(
            char[] sourceStorePassword, List<char[]> entryPasswords) {
        List<String> included = List.copyOf(vm.selectedAliases());
        return new ConversionPlan(
                vm.getSource().containerType(),
                vm.getSource().encoding(),
                vm.getTargetContainerType(),
                vm.getTargetEncoding(),
                vm.getSourcePath(),
                vm.getTargetPath(),
                sourceStorePassword,
                getTargetStorePassword(),
                vm.getAliasConflictPolicy(),
                vm.getOverwritePolicy(),
                included,
                entryPasswords);
    }
}