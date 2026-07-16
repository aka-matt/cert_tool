package io.github.certtool.app.task;

import io.github.certtool.conversion.core.Preflight;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.profile.Profile;
import java.util.Map;
import java.util.Objects;
import javafx.concurrent.Task;

/**
 * Runs {@link Preflight#check} on a background thread and exposes the resulting
 * {@link PreflightReport} to JavaFX bindings.
 *
 * <p>Mirrors the existing {@code ConvertTask} pattern: a thin wrapper that makes the synchronous
 * preflight check usable as a {@link javafx.concurrent.Task}, so the wizard can disable Next
 * while preflight runs and surface terminal-state exceptions via {@code setOnFailed}.
 */
public class ConvertPreflightTask extends Task<PreflightReport> {

    private final ConversionPlan plan;
    private final Map<String, EntryType> sourceEntries;
    private final Profile profile;

    public ConvertPreflightTask(
            ConversionPlan plan,
            Map<String, EntryType> sourceEntries,
            Profile profile) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.sourceEntries = Map.copyOf(Objects.requireNonNull(sourceEntries, "sourceEntries"));
        this.profile = profile; // nullable — null skips FIPS-like classification in Preflight
    }

    @Override
    protected PreflightReport call() throws Exception {
        tryMessage("Preflighting…");
        PreflightReport report = Preflight.check(plan, sourceEntries, profile);
        tryMessage("Preflight complete: "
                + report.blockerCount() + " blockers, "
                + report.warningCount() + " warnings.");
        return report;
    }

    /** Updates the task message, swallowing Toolkit-not-initialised for headless callers. */
    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // headless callers; no JavaFX Toolkit — ignore
        }
    }
}