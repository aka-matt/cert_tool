package io.github.certtool.domain.certificate;

/**
 * Typed codes for the issues a chain validator can detect. Per spec §5, the chain check
 * must surface: issuer/subject linkage, signature verification, validity windows, Basic
 * Constraints presence on intermediates, CA Key Usage permitting signing, root self-signed,
 * and chain-order anomalies.
 *
 * <p>This enum is descriptive only — the rule engine (Phase 4) maps these codes into
 * {@code AssessmentStatus}.
 */
public enum ChainIssueCode {
    /** Cert[i].issuer does not equal Cert[i+1].subject. */
    ISSUER_MISMATCH,
    /** Cert[i]'s signature does not verify under Cert[i+1]'s public key. */
    SIGNATURE_INVALID,
    /** Some cert in the chain has a notAfter in the past. */
    EXPIRED,
    /** Some cert in the chain has a notBefore in the future. */
    NOT_YET_VALID,
    /** An intermediate (non-root) cert lacks the Basic Constraints extension. */
    MISSING_BASIC_CONSTRAINTS,
    /** A CA cert lacks the keyCertSign bit in Key Usage. */
    KEY_USAGE_MISSING_SIGNING,
    /** Last cert in the chain is not a self-signed root. */
    ROOT_NOT_SELF_SIGNED,
    /** Last cert is not a root, or chain shape violates end-entity → root ordering. */
    ORDER_ANOMALY
}