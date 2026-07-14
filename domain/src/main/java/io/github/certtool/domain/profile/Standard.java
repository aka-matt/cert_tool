package io.github.certtool.domain.profile;

/**
 * The FIPS standard a {@link Profile} targets.
 *
 * <p>FIPS 140-2 was withdrawn by NIST on 2024-02-29; FIPS 140-3 is the current U.S. federal
 * standard for cryptographic modules. This enum only labels the target standard for the static
 * assessment — it does not imply any formal certification conclusion.
 */
public enum Standard {
    FIPS_140_2,
    FIPS_140_3
}