package io.github.certtool.app.viewmodel;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * View-model for the Convert wizard. Adds wizard navigation state on top of {@link
 * ConvertViewModel}'s source/target-state observables.
 *
 * <p>Per-step validators and preflight orchestration live in {@link
 * io.github.certtool.app.controller.ConvertWizardController} — the VM is plain state plus a
 * handful of derived bindings.
 */
public final class ConvertWizardViewModel extends ConvertViewModel {

    /** 5-step wizard enumeration, ordered. */
    public enum WizardStep {
        SOURCE("Source"),
        CONTENTS("Contents"),
        TARGET("Target"),
        PREFLIGHT("Preflight"),
        EXECUTE("Execute and verify");

        private final String title;

        WizardStep(String title) { this.title = title; }

        public String title() { return title; }
    }

    /** Target-side Base64 formatting options. */
    public record Base64Options(int lineWidth, boolean wrapHeaders) {
        public static final Base64Options DEFAULT = new Base64Options(64, false);

        public Base64Options {
            if (lineWidth <= 0) {
                throw new IllegalArgumentException("lineWidth must be > 0");
            }
        }
    }

    private final ObjectProperty<WizardStep> currentStep =
            new SimpleObjectProperty<>(WizardStep.SOURCE);
    private final BooleanProperty runningPreflight = new SimpleBooleanProperty(false);
    private final BooleanProperty runningConvert = new SimpleBooleanProperty(false);
    private final ReadOnlyBooleanWrapper backEnabled = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper stepTitle = new ReadOnlyStringWrapper();
    private final ObjectProperty<Base64Options> targetBase64Options =
            new SimpleObjectProperty<>(Base64Options.DEFAULT);
    private final ObjectProperty<KeyStoreContainerType> targetContainerType =
            new SimpleObjectProperty<>(KeyStoreContainerType.BCFKS);
    private final ObjectProperty<ContentEncoding> targetEncoding =
            new SimpleObjectProperty<>(ContentEncoding.BINARY);

    // nextEnabled is wired by the controller. It is exposed as a property so the view can bind
    // Next.disableProperty() to !nextEnabled.
    private final BooleanProperty nextEnabled = new SimpleBooleanProperty(false);

    {
        backEnabled.bind(
                Bindings.createBooleanBinding(
                        () -> currentStep.get() != WizardStep.SOURCE, currentStep));
        stepTitle.bind(
                Bindings.createStringBinding(
                        () -> "Step "
                                + (currentStep.get().ordinal() + 1)
                                + " of 5 — "
                                + currentStep.get().title(),
                        currentStep));
    }

    public ObjectProperty<WizardStep> currentStepProperty() { return currentStep; }

    public WizardStep getCurrentStep() { return currentStep.get(); }

    public void setCurrentStep(WizardStep step) {
        currentStep.set(Objects.requireNonNull(step, "step"));
    }

    public ReadOnlyBooleanProperty nextEnabledProperty() { return nextEnabled; }

    public boolean isNextEnabled() { return nextEnabled.get(); }

    public void setNextEnabled(boolean v) { nextEnabled.set(v); }

    public ReadOnlyBooleanProperty backEnabledProperty() {
        return backEnabled.getReadOnlyProperty();
    }

    public BooleanProperty runningPreflightProperty() { return runningPreflight; }

    public boolean isRunningPreflight() { return runningPreflight.get(); }

    public void setRunningPreflight(boolean v) { runningPreflight.set(v); }

    public BooleanProperty runningConvertProperty() { return runningConvert; }

    public boolean isRunningConvert() { return runningConvert.get(); }

    public void setRunningConvert(boolean v) { runningConvert.set(v); }

    public ReadOnlyStringProperty stepTitleProperty() {
        return stepTitle.getReadOnlyProperty();
    }

    public ObjectProperty<Base64Options> targetBase64OptionsProperty() {
        return targetBase64Options;
    }

    public Base64Options getTargetBase64Options() { return targetBase64Options.get(); }

    public void setTargetBase64Options(Base64Options o) {
        targetBase64Options.set(Objects.requireNonNull(o, "options"));
    }

    public ObjectProperty<KeyStoreContainerType> targetContainerTypeProperty() {
        return targetContainerType;
    }

    public KeyStoreContainerType getTargetContainerType() {
        return targetContainerType.get();
    }

    public void setTargetContainerType(KeyStoreContainerType v) {
        targetContainerType.set(Objects.requireNonNull(v, "v"));
    }

    public ObjectProperty<ContentEncoding> targetEncodingProperty() {
        return targetEncoding;
    }

    public ContentEncoding getTargetEncoding() {
        return targetEncoding.get();
    }

    public void setTargetEncoding(ContentEncoding v) {
        targetEncoding.set(Objects.requireNonNull(v, "v"));
    }
}