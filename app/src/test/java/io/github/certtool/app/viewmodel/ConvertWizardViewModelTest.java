package io.github.certtool.app.viewmodel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConvertWizardViewModelTest {

    @Test
    void currentStepDefaultsToSource() {
        var vm = new ConvertWizardViewModel();
        assertThat(vm.getCurrentStep())
                .isEqualTo(ConvertWizardViewModel.WizardStep.SOURCE);
    }

    @Test
    void currentStepRoundTrips() {
        var vm = new ConvertWizardViewModel();
        vm.setCurrentStep(ConvertWizardViewModel.WizardStep.TARGET);
        assertThat(vm.getCurrentStep())
                .isEqualTo(ConvertWizardViewModel.WizardStep.TARGET);
    }

    @Test
    void backEnabledFalseOnSourceAndTrueElsewhere() {
        var vm = new ConvertWizardViewModel();
        // SOURCE — back disabled
        assertThat(vm.backEnabledProperty().get()).isFalse();
        // CONTENTS — back enabled
        vm.setCurrentStep(ConvertWizardViewModel.WizardStep.CONTENTS);
        assertThat(vm.backEnabledProperty().get()).isTrue();
        // EXECUTE — back enabled
        vm.setCurrentStep(ConvertWizardViewModel.WizardStep.EXECUTE);
        assertThat(vm.backEnabledProperty().get()).isTrue();
    }

    @Test
    void runningFlagsRoundTrip() {
        var vm = new ConvertWizardViewModel();
        assertThat(vm.isRunningPreflight()).isFalse();
        assertThat(vm.isRunningConvert()).isFalse();
        vm.setRunningPreflight(true);
        assertThat(vm.isRunningPreflight()).isTrue();
        vm.setRunningConvert(true);
        assertThat(vm.isRunningConvert()).isTrue();
    }

    @Test
    void stepTitleMatchesCurrentStep() {
        var vm = new ConvertWizardViewModel();
        // Initial SOURCE
        assertThat(vm.stepTitleProperty().get()).contains("Source");
        vm.setCurrentStep(ConvertWizardViewModel.WizardStep.PREFLIGHT);
        assertThat(vm.stepTitleProperty().get()).contains("Preflight");
    }

    @Test
    void targetBase64OptionsDefaultToWidth64NoHeaders() {
        var vm = new ConvertWizardViewModel();
        assertThat(vm.getTargetBase64Options())
                .isEqualTo(ConvertWizardViewModel.Base64Options.DEFAULT);
    }
}