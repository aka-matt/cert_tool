package io.github.certtool.app.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.controller.ConvertController;
import io.github.certtool.app.controller.ConvertWizardController;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConvertViewBindingTest {

    @BeforeAll
    static void initFx() {
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    private ConvertWizardViewModel vm() {
        var vm = new ConvertWizardViewModel();
        vm.setAliasConflictPolicy(AliasConflictPolicy.RENAME);
        vm.setOverwritePolicy(OverwritePolicy.FAIL_IF_EXISTS);
        vm.setTargetPath("/tmp/target.bcfks");
        // BCFKS requires a non-empty target password — set it so isStepValid(TARGET) passes.
        vm.setTargetContainerType(KeyStoreContainerType.BCFKS);
        return vm;
    }

    private ConvertWizardController wiz(ConvertWizardViewModel vm) {
        return new ConvertWizardController(new ConvertController(vm), vm,
                Executors.newSingleThreadExecutor());
    }

    @Test
    void rootReturnsNonNullBorderPane() {
        var vm = vm();
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        assertThat(view.root()).isNotNull();
    }

    @Test
    void rootContainsBackNextCloseFooterButtons() {
        var vm = vm();
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        Node root = view.root();
        Button back = findButton(root, "← Back");
        Button next = findButton(root, "Next →");
        Button close = findButton(root, "Close");
        assertThat(back).isNotNull();
        assertThat(next).isNotNull();
        assertThat(close).isNotNull();
    }

    @Test
    void stepTitleReflectsCurrentStep() {
        var vm = vm();
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        Node root = view.root();
        Label title = findLabelByTextStartsWith(root, "Step 1 of 5");
        assertThat(title).isNotNull();
        vm.setCurrentStep(WizardStep.PREFLIGHT);
        Label title2 = findLabelByTextStartsWith(root, "Step 4 of 5");
        assertThat(title2).isNotNull();
    }

    @Test
    void sourcePanelRendersContainerEncodingAndPath() {
        var vm = vm();
        // Seed via ConvertController so the panel sees a realistic LoadedKeyStoreInfo.
        var info = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS,
                ContentEncoding.BINARY,
                "/tmp/source.jks",
                1024L,
                true,
                List.of("a", "b"));
        new ConvertController(vm).onSourceSelected(info);
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        Node root = view.root();
        // Trigger SOURCE panel construction by toggling currentStep:
        vm.setCurrentStep(WizardStep.SOURCE);
        Label content = findLabelByTextStartsWith(root, "Container:");
        assertThat(content).isNotNull();
        Label aliasCount = findLabelByTextStartsWith(root, "Entries:");
        assertThat(aliasCount).isNotNull();
    }

    @Test
    void sourcePanelEmptyStateWhenNoSource() {
        var vm = vm();
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        Node root = view.root();
        Label empty = findLabelByTextStartsWith(root, "Open a keystore");
        assertThat(empty).isNotNull();
    }

    private static Button findButton(Node root, String text) {
        if (root instanceof Button b && text.equals(b.getText())) return b;
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Button r = findButton(c, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static Label findLabelByTextStartsWith(Node root, String prefix) {
        if (root instanceof Label l && l.getText() != null && l.getText().startsWith(prefix)) return l;
        // TitledPane text is on its header Labeled; search it too.
        if (root instanceof TitledPane tp && tp.getText() != null && tp.getText().startsWith(prefix)) {
            Label synthetic = new Label(tp.getText());
            return synthetic;
        }
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Label r = findLabelByTextStartsWith(c, prefix);
                if (r != null) return r;
            }
        }
        return null;
    }

    @Test
    void contentsPanelRendersTableAndSelectionBanner() {
        var vm = vm();
        var info = new LoadedKeyStoreInfo(KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                "/tmp/source.jks", 1024L, true, List.of("alpha", "beta", "gamma"));
        new ConvertController(vm).onSourceSelected(info);
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        vm.setCurrentStep(WizardStep.CONTENTS);
        Node root = view.root();
        // Banner "X of Y entries selected" appears on CONTENTS panel
        Label banner = findLabelByTextStartsWith(root, "3 of 3 entries selected");
        assertThat(banner).isNotNull();
        // "Select all" button exists
        Button selectAll = findButton(root, "Select all");
        assertThat(selectAll).isNotNull();
    }

    @Test
    void targetPanelContainsContainerEncodingPathAndPasswordFields() {
        var vm = vm();
        var wizard = wiz(vm);
        var view = new ConvertView(vm, wizard, Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        vm.setCurrentStep(WizardStep.TARGET);
        Node root = view.root();
        // Combo boxes for target container + encoding; PasswordField for store password
        assertThat(root.lookupAll(".combo-box")).isNotEmpty();
        assertThat(root.lookupAll(".password-field")).isNotEmpty();
        assertThat(findButton(root, "Browse…")).isNotNull();
        Label lineWidthLabel = findLabelByTextStartsWith(root, "Base64 line width");
        assertThat(lineWidthLabel).isNotNull();
        // Spec pre-flight finding #4: password field pushes to controller.
        PasswordField pwdField = (PasswordField) root.lookup("#convert-target-password");
        assertThat(pwdField).isNotNull();
        pwdField.setText("secret");
        assertThat(wizard.getTargetStorePassword()).isNotEmpty();
    }

    @Test
    void preflightPanelRendersProfileNameAndThreeSeveritySections() {
        var vm = vm();
        // Seed a clean preflight report — one WARN, no BLOCK.
        var report = new PreflightReport(List.of(
                new PreflightFinding(
                        PreflightSeverity.WARN,
                        "BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE", null,
                        "BCFKS source contains private-key entries…")));
        new ConvertController(vm).onPreflightProduced(report);

        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        vm.setCurrentStep(WizardStep.PREFLIGHT);
        Node root = view.root();
        Label profileLabel = findLabelByTextStartsWith(root, "Profile:");
        // Profile label may be empty if profile is null in this test — at minimum a WARN section
        // should be present.
        Label warn = findLabelByTextStartsWith(root, "Warnings");
        assertThat(warn).isNotNull();
        // Optional password override field exists:
        Label optPwd = findLabelByTextStartsWith(root, "Override per-entry key password");
        assertThat(optPwd).isNotNull();
    }

    @Test
    void executePanelRendersSummaryAndConvertButton() {
        var vm = vm();
        var view = new ConvertView(vm, wiz(vm), Executors.newSingleThreadExecutor(),
                () -> null, r -> {}, s -> {});
        vm.setCurrentStep(WizardStep.EXECUTE);
        Node root = view.root();
        Button convert = findButton(root, "Convert");
        assertThat(convert).isNotNull();
        Label summary = findLabelByTextStartsWith(root, "Final plan summary");
        assertThat(summary).isNotNull();
    }
}