package io.github.certtool.domain.assessment;

import java.util.List;
import java.util.Objects;

/**
 * The atomic result of a single FIPS compatibility rule evaluation (per spec §6).
 *
 * <p>Every finding carries the rule id, a title, a status, a severity, a human summary, the
 * evidence that produced the status, a remediation hint, and references. The report never reduces
 * a finding to a single icon — all eight fields are presented.
 *
 * <p>{@code summary}, {@code evidence}, and {@code remediation} MUST NOT contain passwords, key
 * bytes, or full keystore blobs (spec §2).
 */
public record AssessmentFinding(
        String ruleId,
        String title,
        AssessmentStatus status,
        Severity severity,
        String summary,
        String evidence,
        String remediation,
        List<String> references) {

    public AssessmentFinding {
        Objects.requireNonNull(ruleId, "ruleId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(remediation, "remediation");
        references = references == null ? List.of() : List.copyOf(references);
    }
}