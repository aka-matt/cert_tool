package io.github.certtool.domain.profile;

/**
 * How a {@link Profile} treats algorithms it cannot recognize.
 *
 * <p>Per spec §6 + §7, an unrecognized algorithm is not a green light: rules should default to
 * {@link #NOT_ASSESSABLE} so the report explicitly says "we don't know" rather than silently
 * passing.
 */
public enum UnknownAlgorithmPolicy {
    /** Unrecognized algorithm → FAIL. */
    FAIL,
    /** Unrecognized algorithm → WARNING. */
    WARNING,
    /** Unrecognized algorithm → NOT_ASSESSABLE (we cannot judge). */
    NOT_ASSESSABLE,
    /** Unrecognized algorithm is silently accepted. */
    ALLOW
}