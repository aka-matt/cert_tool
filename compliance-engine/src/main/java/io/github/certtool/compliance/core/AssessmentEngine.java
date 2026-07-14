package io.github.certtool.compliance.core;

import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.FipsDisclaimer;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Runs a {@link RuleRegistry} against a {@link RuleContext} for a given {@link Profile} and
 * produces an {@link AssessmentReport}.
 *
 * <p>The engine is intentionally tiny:
 * <ul>
 *   <li>It never decides what a rule should emit — the rule does that.</li>
 *   <li>It always attaches the canonical {@link FipsDisclaimer} to the report (spec §6).</li>
 *   <li>It sorts findings by severity (most severe first), then by ruleId for determinism.</li>
 *   <li>It catches every exception from a rule and converts it into a {@code NOT_ASSESSABLE}
 *       finding so a single broken rule cannot take down the whole assessment.</li>
 * </ul>
 */
public final class AssessmentEngine {

    private final Clock clock;

    public AssessmentEngine() {
        this(Clock.systemUTC());
    }

    /** Constructor used by tests to inject a fixed clock. */
    public AssessmentEngine(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Runs every rule and returns the assembled report.
     *
     * @throws NullPointerException if any argument is null
     */
    public AssessmentReport assess(RuleContext context, Profile profile, RuleRegistry registry) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(registry, "registry");

        List<AssessmentFinding> findings = new ArrayList<>(registry.rules().size());
        for (Rule rule : registry.rules()) {
            findings.add(safeEvaluate(rule, context, profile));
        }
        findings.sort(Comparator.<AssessmentFinding, Severity>comparing(
                        AssessmentFinding::severity, Comparator.comparingInt(Severity::ordinal))
                .thenComparing(AssessmentFinding::ruleId));

        return new AssessmentReport(
                profile,
                Instant.now(clock),
                FipsDisclaimer.text(),
                findings);
    }

    /**
     * Invokes a rule and converts any thrown exception into a {@code NOT_ASSESSABLE} finding so a
     * single faulty rule does not abort the assessment.
     */
    private static AssessmentFinding safeEvaluate(Rule rule, RuleContext context, Profile profile) {
        try {
            return rule.evaluate(context, profile);
        } catch (RuntimeException e) {
            return new AssessmentFinding(
                    rule.id(),
                    rule.title(),
                    AssessmentStatus.NOT_ASSESSABLE,
                    Severity.INFO,
                    "Rule evaluation failed",
                    "Exception class: " + e.getClass().getName(),
                    "Inspect the rule implementation; rule must not throw.",
                    List.of());
        }
    }
}