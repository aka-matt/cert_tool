package io.github.certtool.domain.certificate;

/**
 * Result of asking a {@link ValidityWindow} whether a given instant falls inside it.
 *
 * <p>Per spec §5, every parsed X.509 certificate exposes a current validity state. This is
 * descriptive only — the rule engine (Phase 4) maps this fact into an {@code AssessmentStatus}.
 */
public enum ValidityState {
    /** {@code instant} is within {@code [notBefore, notAfter]}. */
    VALID,
    /** {@code instant} is before {@code notBefore}. */
    NOT_YET_VALID,
    /** {@code instant} is after {@code notAfter}. */
    EXPIRED
}