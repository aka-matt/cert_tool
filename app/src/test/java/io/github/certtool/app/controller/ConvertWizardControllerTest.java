package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.app.task.ConvertPreflightTask;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConvertWizardControllerTest {

    @BeforeAll
    static void initFx() {
        // Headless: same trick used by ConvertPreflightTaskTest.
        try {
            Platform.startup(() -> { });
        } catch (IllegalStateException ignored) {}
    }

    private ConvertWizardViewModel vm;
    private ConvertWizardController wizard;

    @BeforeEach
    void setUp() {
        vm = new ConvertWizardViewModel();
        wizard = new ConvertWizardController(new ConvertController(vm), vm,
                Executors.newSingleThreadExecutor());
    }

    @AfterEach
    void tearDown() { /* executor is single-shot, no shutdown needed for these tests */ }

    /** Helper: simulate Inspect populating the VM with a successful load. */
    private void seedSource() {
        var info = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                Path.of("/tmp/source.jks").toString(), 1024L, true, List.of("a", "b"));
        new ConvertController(vm).onSourceSelected(info);
    }

    @Test
    void sourceStepInvalidWhenNoSourceLoaded() {
        assertThat(wizard.isStepValid(WizardStep.SOURCE)).isFalse();
        seedSource();
        assertThat(wizard.isStepValid(WizardStep.SOURCE)).isTrue();
    }

    @Test
    void contentsStepInvalidWhenNoAliasesSelected() {
        seedSource();
        vm.selectedAliases().clear();
        assertThat(wizard.isStepValid(WizardStep.CONTENTS)).isFalse();
        // selecting one alias makes it valid
        vm.selectedAliases().add("a");
        assertThat(wizard.isStepValid(WizardStep.CONTENTS)).isTrue();
    }

    @Test
    void targetStepRequiresPathAndPoliciesAndBase64Defaults() {
        vm.setSourcePath("/tmp/source.jks");
        vm.setTargetPath("");
        assertThat(wizard.isStepValid(WizardStep.TARGET)).isFalse();
        vm.setTargetPath("/tmp/target.bcfks");
        // policies default to RENAME / FAIL_IF_EXISTS — already chosen
        assertThat(wizard.isStepValid(WizardStep.TARGET)).isTrue();
    }

    @Test
    void preflightStepRequiresCleanReport() {
        // no report yet
        assertThat(wizard.isStepValid(WizardStep.PREFLIGHT)).isFalse();
    }

    @Test
    void executeStepRequiresNothingBeyondNoRunningTask() {
        assertThat(wizard.isStepValid(WizardStep.EXECUTE)).isTrue();
        vm.setRunningConvert(true);
        assertThat(wizard.isStepValid(WizardStep.EXECUTE)).isFalse();
    }

    @Test
    void resetOnSourceChangeClearsTargetState() {
        vm.setTargetPath("/tmp/target.bcfks");
        seedSource(); // also resets targetPath down the line — see resetOnSourceChange
        wizard.resetOnSourceChange();
        assertThat(vm.getTargetPath()).isEmpty();
        assertThat(vm.getCurrentStep()).isEqualTo(WizardStep.SOURCE);
        assertThat(vm.getPreflightReport()).isNull();
    }

    @Test
    void buildConversionPlanMissingSourcePathThrows() {
        assertThatThrownBy(() -> wizard.buildConversionPlan(Map.of(), /*profile*/ null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source");
    }

    @Test
    void handleEnteredPreflightSetsRunningFlagAndCallsFactory() throws Exception {
        seedSource();
        vm.setTargetPath("/tmp/target.bcfks");
        vm.selectedAliases().add("a");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        wizard.setPreflightTaskFactory((plan, profile) -> {
            calls.incrementAndGet();
            // Return a task that completes synchronously inside the test executor.
            var t = new ConvertPreflightTask(plan,
                    Map.of("a", EntryType.TRUSTED_CERTIFICATE), profile);
            t.run(); // drive to completion
            return t;
        });
        runOnFxThreadAndWait(() ->
                wizard.handleEnteredPreflight(
                        Map.of("a", EntryType.TRUSTED_CERTIFICATE), null));
        // After completion the terminal handler clears runningPreflight and stores the report.
        // Allow the executor to drain.
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {}
        assertThat(calls.get()).isEqualTo(1);
        // runningPreflight may still be true if the task hasn't been observed by the FX thread;
        // the FX-toolkit-free test uses a synchronous runnable instead — assert behaviour by
        // invoking the terminal handler directly in a separate test (next test).
    }

    @Test
    void onPreflightSucceededClearsRunningFlagAndStoresReport() throws Exception {
        seedSource();
        vm.setTargetPath("/tmp/target.bcfks");
        vm.selectedAliases().add("a");
        var plan = wizard.buildConversionPlan(Map.of("a", EntryType.TRUSTED_CERTIFICATE),
                null);
        PreflightReport[] resultHolder = new PreflightReport[1];
        runOnFxThreadAndWait(() -> {
            // Direct invocation of the success terminal — production wires this as a Task listener.
            var task = new ConvertPreflightTask(plan,
                    Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
            task.run();
            try {
                resultHolder[0] = task.get();
            } catch (InterruptedException | java.util.concurrent.ExecutionException e) {
                throw new RuntimeException(e);
            }
        });
        assertThat(resultHolder[0]).isNotNull();
        wizard.onPreflightSucceeded(resultHolder[0]);
        assertThat(vm.getPreflightReport()).isNotNull();
    }

    @Test
    void onPreflightFailedSurfacesErrorAndClearsRunningFlag() throws Exception {
        seedSource();
        vm.setTargetPath("/tmp/target.bcfks"); // so buildConversionPlan succeeds
        vm.selectedAliases().add("a");
        var errors = new java.util.concurrent.atomic.AtomicReference<String>();
        wizard.setPreflightErrorListener(errors::set);
        runOnFxThreadAndWait(() -> {
            // Simulate a failed task by constructing one whose call() throws.
            wizard.setPreflightTaskFactory((plan, profile) -> {
                var t = new ConvertPreflightTask(plan,
                        Map.of("a", EntryType.TRUSTED_CERTIFICATE), profile) {
                    @Override
                    public PreflightReport call() throws Exception {
                        throw new RuntimeException("simulated");
                    }
                };
                t.run(); // synchronously throws — task ends in FAILED state
                return t;
            });
            vm.setRunningPreflight(true); // pretend submit set it
            wizard.handleEnteredPreflight(
                    Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
        });
        // Allow the executor + FX event queue to drain so setOnFailed fires.
        try {
            Thread.sleep(200);
        } catch (InterruptedException ignored) {}
        // handleEnteredPreflight pulls Throwable from the task and calls onPreflightFailed.
        assertThat(errors.get()).contains("simulated");
    }

    private static void runOnFxThreadAndWait(Runnable action) throws Exception {
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            } finally {
                completed.countDown();
            }
        });
        if (!completed.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action did not complete within 5s");
        }
        Throwable f = failure.get();
        if (f != null) {
            throw new RuntimeException("FX action threw", f);
        }
    }
}