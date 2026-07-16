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
        if (root instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) {
                Label r = findLabelByTextStartsWith(c, prefix);
                if (r != null) return r;
            }
        }
        return null;
    }
}