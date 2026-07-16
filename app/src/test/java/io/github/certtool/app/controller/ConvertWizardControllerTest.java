package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConvertWizardControllerTest {

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
}