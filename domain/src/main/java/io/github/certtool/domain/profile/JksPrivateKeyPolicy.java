package io.github.certtool.domain.profile;

/**
 * How a {@link Profile} treats a JKS keystore that contains private-key entries.
 *
 * <p>JKS uses a weak proprietary integrity check (a per-byte XOR "checksum"), is vulnerable to
 * several known attacks, and does not encrypt private-key material with strong cryptography. FIPS
 * profiles should reject private-key material in JKS.
 */
public enum JksPrivateKeyPolicy {
    /** Private-key entries in JKS produce a FAIL finding. */
    FAIL,
    /** Private-key entries in JKS produce a WARNING finding. */
    WARNING,
    /** We cannot tell whether private keys are stored securely in JKS → NOT_ASSESSABLE. */
    NOT_ASSESSABLE
}