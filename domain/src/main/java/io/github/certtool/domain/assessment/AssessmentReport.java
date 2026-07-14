package io.github.certtool.domain.assessment;

import io.github.certtool.domain.profile.Profile;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Top-level result of running the rule engine against a {@code RuleContext} for a {@link Profile}.
 *
 * <p>Every report carries the {@link FipsDisclaimer} verbatim — the tool never produces a report
 * that does not disclaim formal certification (spec §6).
 *
 * <p>Findings are immutable; {@link #summary()} computes per-status counts at construction time
 * so callers can render them without re-scanning.
 */
public record AssessmentReport(
        Profile profile,
        Instant generatedAt,
        String disclaimer,
        List<AssessmentFinding> findings) {

    public AssessmentReport {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(disclaimer, "disclaimer");
        Objects.requireNonNull(findings, "findings");
        findings = List.copyOf(findings);
    }

    /** Convenience constructor that uses the canonical disclaimer text. */
    public AssessmentReport(Profile profile, Instant generatedAt, List<AssessmentFinding> findings) {
        this(profile, generatedAt, FipsDisclaimer.text(), findings);
    }

    /** Returns an immutable map of finding counts per {@link AssessmentStatus}. */
    public Map<AssessmentStatus, Long> summary() {
        Map<AssessmentStatus, Long> counts = new EnumMap<>(AssessmentStatus.class);
        for (AssessmentStatus s : AssessmentStatus.values()) {
            counts.put(s, 0L);
        }
        for (AssessmentFinding f : findings) {
            counts.merge(f.status(), 1L, Long::sum);
        }
        return Map.copyOf(counts);
    }
}