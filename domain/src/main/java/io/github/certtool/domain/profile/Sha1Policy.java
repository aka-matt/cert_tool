package io.github.certtool.domain.profile;

/**
 * How a {@link Profile} treats SHA-1 in signature algorithms.
 *
 * <p>SHA-1 has been deprecated for digital signatures since 2011 and is disallowed by NIST for
 * FIPS-approved signatures. FIPS 140-3 effectively disallows SHA-1; FIPS 140-2 allows it only
 * for legacy verification (not new signatures).
 */
public enum Sha1Policy {
    /** SHA-1 must not be used anywhere (new signatures or verification). */
    DISALLOW,
    /** SHA-1 is permitted for legacy verification only (no new SHA-1 signatures). */
    WARN,
    /** SHA-1 is permitted without remark (insecure posture; for special compat use only). */
    ALLOW
}