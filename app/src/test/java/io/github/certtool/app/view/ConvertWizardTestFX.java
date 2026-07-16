package io.github.certtool.app.view;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.AppComposition;
import io.github.certtool.app.controller.MainShellController;
import io.github.certtool.app.viewmodel.ConvertWizardViewModel.WizardStep;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * TestFX smoke test for the Convert wizard TARGET step.
 * Validates that the Next button is disabled while target path is empty,
 * and re-enabled once the path is filled.
 *
 * <p>Gated behind {@code -Dcert.tool.testfx=true} so CI can opt in or out;
 * headless environments without a display won't run these tests.
 */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "cert.tool.testfx", matches = "true")
class ConvertWizardTestFX extends ApplicationTest {

    private MainShellController controller;
    private AppComposition composition;

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // JavaFX may already be initialised in some environments.
        }
    }

    @Override
    public void start(Stage stage) {
        composition = AppComposition.defaultComposition();
        controller = new MainShellController(composition, stage);
        controller.show();

        // Seed a source keystore so SOURCE → CONTENTS step can advance.
        // Uses the 6-arg LoadedKeyStoreInfo record (containerType, encoding, sourcePath,
        // sizeBytes, integrityCheckPassed, aliases).
        composition.convertController().onSourceSelected(
                new LoadedKeyStoreInfo(
                        KeyStoreContainerType.JKS,
                        ContentEncoding.BINARY,
                        "/tmp/source.jks",
                        1024L,
                        true,
                        List.of("a", "b")));

        // Reset wizard to SOURCE so the test starts fresh.
        composition.convertWizardController().resetOnSourceChange();

        // Seed a password for the target store. The BCFKS validator requires a non-empty
        // password for the TARGET step to be valid. In the real UI the user sets this
        // on the TARGET step; for the test we seed it upfront.
        composition.convertWizardController().setTargetStorePassword("changeit".toCharArray());
    }

    /**
     * Advances the wizard one step by posting next() to the FX application thread
     * and waiting a fixed time for the event to be processed.
     */
    private void advanceToNextStep() {
        Platform.runLater(() -> composition.convertWizardController().next());
        sleep(150);
    }

    @Test
    void targetStepNextDisabledUntilTargetPathFilled() {
        var vm = composition.convertWizardVm();

        // Verify we start at SOURCE.
        assertThat(vm.getCurrentStep()).isEqualTo(WizardStep.SOURCE);

        // Click the Convert tab to make it active.
        clickOn(".tab:has-text(\"Convert\")");
        sleep(100);

        // --- SOURCE → CONTENTS ---
        advanceToNextStep();
        assertThat(vm.getCurrentStep())
                .as("Wizard should be on CONTENTS step after first Next")
                .isEqualTo(WizardStep.CONTENTS);

        // --- CONTENTS → TARGET ---
        advanceToNextStep();
        assertThat(vm.getCurrentStep())
                .as("Wizard should be on TARGET step after second Next")
                .isEqualTo(WizardStep.TARGET);

        // --- TARGET step: Next must be disabled because no target path is set ---
        assertThat(vm.isNextEnabled())
                .as("Next must be disabled when target path is empty")
                .isFalse();

        // Also verify via the UI button in the ConvertView footer.
        Button nextButton = lookup("Next →").queryButton();
        assertThat(nextButton.isDisabled())
                .as("Next button must be disabled when target path is empty")
                .isTrue();

        // --- Find the target path TextField ---
        TextField pathField = findTargetPathField();
        assertThat(pathField)
                .as("Target path TextField must be present in the TARGET step")
                .isNotNull();

        // --- Fill the target path via the VM directly ---
        // This triggers the targetPathProperty listener in ConvertWizardController
        // which calls recomputeNextEnabled(). We also call recomputeNextEnabled()
        // directly to ensure the button binding updates.
        Platform.runLater(() -> {
            vm.setTargetPath("/tmp/target.bcfks");
            composition.convertWizardController().recomputeNextEnabled();
        });
        sleep(150);

        // --- TARGET step: Next must now be enabled ---
        assertThat(vm.isNextEnabled())
                .as("Next must be enabled after target path is filled")
                .isTrue();

        Button nextButtonAfter = lookup("Next →").queryButton();
        assertThat(nextButtonAfter.isDisabled())
                .as("Next button must be enabled after target path is filled")
                .isFalse();
    }

    /**
     * Locates the target-path TextField by traversing the TARGET step's GridPane children.
     * The grid row 2 contains (Label "Target path:", TextField, Button "Browse…").
     */
    private TextField findTargetPathField() {
        // First try: lookup by ID + CSS descendant combinator.
        TextField tf = lookup("#convert-step-target .text-field").query();
        if (tf != null) {
            return tf;
        }

        // Second try: traverse the TARGET panel node hierarchy.
        Node targetPane = lookup("#convert-step-target").query();
        if (targetPane == null) {
            return null;
        }
        for (Node n : ((javafx.scene.Parent) targetPane).getChildrenUnmodifiable()) {
            if (n instanceof javafx.scene.layout.GridPane grid) {
                for (Node cell : grid.getChildren()) {
                    if (cell instanceof TextField textField) {
                        return textField;
                    }
                }
            }
        }
        return null;
    }
}
