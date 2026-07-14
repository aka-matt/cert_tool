package io.github.certtool.compliance.core;

import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.profile.Profile;

/**
 * A single FIPS compatibility assessment rule (spec §7).
 *
 * <p>Each rule is pure: it reads facts from a {@link RuleContext} and posture from a {@link Profile},
 * and produces exactly one {@link AssessmentFinding}. Rules must not throw — they must catch their
 * own exceptions and emit a {@code NOT_ASSESSABLE} finding with the exception class name as
 * evidence (without leaking stack traces or sensitive material).
 *
 * <p>Rules must NEVER:
 * <ul>
 *   <li>Log passwords, private keys, full PEM, or full Base64 keystores.</li>
 *   <li>Make network calls.</li>
 *   <li>Perform I/O on the filesystem.</li>
 *   <li>Mutate the {@code RuleContext} or {@code Profile}.</li>
 *   <li>Claim "FIPS Certified" or use any equivalent certification language.</li>
 * </ul>
 */
public interface Rule {

    /** Stable, unique rule identifier (e.g. {@code "FIPS1403.ALG.SHA1"}). */
    String id();

    /** Short human-readable rule title shown in the Findings table. */
    String title();

    /** The category under which the rule is grouped in the UI and in the report. */
    RuleCategory category();

    /**
     * Evaluates the rule and returns exactly one finding. Implementations must not throw.
     *
     * @param context the facts derived from the loaded keystore and runtime
     * @param profile the posture being applied to the assessment
     */
    AssessmentFinding evaluate(RuleContext context, Profile profile);
}