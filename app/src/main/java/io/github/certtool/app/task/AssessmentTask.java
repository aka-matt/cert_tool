package io.github.certtool.app.task;

import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;
import javafx.concurrent.Task;

/**
 * JavaFX {@link Task} that runs the rule engine for the supplied {@link RuleContext} and
 * {@link Profile} on a background thread.
 */
public final class AssessmentTask extends Task<AssessmentReport> {

    private final AssessmentEngine engine;
    private final RuleContext context;
    private final Profile profile;
    private final RuleRegistry registry;

    public AssessmentTask(
            AssessmentEngine engine, RuleContext context, Profile profile, RuleRegistry registry) {
        this.engine = engine;
        this.context = context;
        this.profile = profile;
        this.registry = registry;
    }

    @Override
    protected AssessmentReport call() {
        tryMessage("Running FIPS compatibility assessment…");
        AssessmentReport report = engine.assess(context, profile, registry);
        tryMessage("Assessment complete: " + report.findings().size() + " finding(s).");
        return report;
    }

    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // toolkit not initialised — fine for headless callers
        }
    }
}