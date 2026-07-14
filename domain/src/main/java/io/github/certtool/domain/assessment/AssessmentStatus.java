package io.github.certtool.domain.assessment;

/**
 * The five possible outcomes of a single FIPS compatibility assessment rule (per spec §6).
 *
 * <p>The tool MUST NOT reduce a finding to a green/red icon — every status carries evidence and
 * remediation. {@code NOT_ASSESSABLE} is the honest answer when the tool cannot determine the
 * state (e.g. approved-only mode detection failed); we never guess.
 */
public enum AssessmentStatus {
    PASS,
    WARNING,
    FAIL,
    NOT_ASSESSABLE,
    NOT_APPLICABLE
}