package io.github.certtool.domain.profile;

/**
 * How a {@link Profile} treats certificates whose {@code notAfter} lies in the past.
 */
public enum ExpirationPolicy {
    /** Expired certificates produce a FAIL finding. */
    FAIL,
    /** Expired certificates produce a WARNING finding. */
    WARN,
    /** Expired certificates produce no finding. */
    ALLOW
}