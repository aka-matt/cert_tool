package io.github.certtool.app.task;

import io.github.certtool.conversion.core.KeystoreConversion;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.password.PasswordProvider;
import javafx.concurrent.Task;

/**
 * JavaFX {@link Task} that runs a {@link ConversionPlan} through {@link KeystoreConversion}.
 *
 * <p>The actual conversion is synchronous and exception-driven (see
 * {@link KeystoreConversion#execute}) so that the wizard can display preflight findings on step 4
 * before invoking this task on step 5. The task lives here so the UI can bind {@code stateProperty},
 * {@code messageProperty}, and {@code exceptionProperty} to progress UI.
 */
public final class ConvertTask extends Task<ConversionResult> {

    private final ConversionPlan plan;
    private final Profile profile;
    private final PasswordProvider passwordProvider;

    public ConvertTask(ConversionPlan plan, Profile profile, PasswordProvider passwordProvider) {
        this.plan = plan;
        this.profile = profile;
        this.passwordProvider = passwordProvider;
    }

    @Override
    protected ConversionResult call() throws Exception {
        tryMessage("Preflighting…");
        ConversionResult r = KeystoreConversion.execute(plan, profile, passwordProvider);
        tryMessage("Conversion succeeded: " + r.writtenBytes() + " bytes written.");
        return r;
    }

    /**
     * Updates the task message, swallowing "Toolkit not initialized" exceptions so headless tests
     * can still drive {@link #call()}.
     */
    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // toolkit not initialised — headless callers don't need progress UI
        }
    }
}
