package io.github.certtool.domain.assessment;

/**
 * Severity of an {@link AssessmentFinding}. Independent of {@link AssessmentStatus}: a finding can
 * be {@code PASS} with severity {@code INFO}, or {@code FAIL} with severity {@code HIGH}.
 */
public enum Severity {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}