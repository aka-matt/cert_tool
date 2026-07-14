package io.github.certtool.domain.profile;

/**
 * Identifies a built-in or custom rule profile.
 *
 * <p>Per spec §6 and §7, this tool exposes at least the following profiles:
 * <ul>
 *   <li>{@link #FIPS_140_2_LEGACY_ASSESSMENT} — compatibility with FIPS 140-2 (legacy posture).</li>
 *   <li>{@link #FIPS_140_3_ASSESSMENT} — compatibility with FIPS 140-3 (current posture).</li>
 *   <li>{@link #CUSTOM} — user-supplied JSON rule set.</li>
 * </ul>
 *
 * <p>This is a static compatibility assessment identifier. It is NOT a certification identifier
 * and must never be used in any report, UI string, or API as evidence of formal NIST/CMVP
 * validation.
 */
public enum ProfileId {
    FIPS_140_2_LEGACY_ASSESSMENT,
    FIPS_140_3_ASSESSMENT,
    CUSTOM
}