package io.github.certtool.compliance.rules;

/**
 * The six rule categories called out in spec §7:
 * <ol>
 *   <li>CONTAINER — keystore container (JKS vs BCFKS, encoding, integrity).</li>
 *   <li>ENTRY — entry kinds (private key, secret key, trusted cert).</li>
 *   <li>CERTIFICATE — single-cert and chain checks (validity, Basic Constraints, signatures).</li>
 *   <li>ALGORITHM — algorithm and key-size checks (RSA, EC, DSA, SHA, MD, etc.).</li>
 *   <li>RUNTIME_PROVIDER — JVM, OS, JCA providers, BCFIPS Approved-Only Mode.</li>
 *   <li>CONVERSION — conversion plan checks (used by Phase 5 wizard preflight).</li>
 * </ol>
 *
 * <p>Used by the UI to group Findings by category and by the engine to filter rule sets.
 */
public enum RuleCategory {
    CONTAINER,
    ENTRY,
    CERTIFICATE,
    ALGORITHM,
    RUNTIME_PROVIDER,
    CONVERSION
}