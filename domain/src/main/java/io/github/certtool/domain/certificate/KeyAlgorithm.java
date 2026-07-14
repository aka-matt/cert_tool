package io.github.certtool.domain.certificate;

/** Algorithm family for the public key of a parsed certificate. */
public enum KeyAlgorithm {
    RSA,
    EC,
    DSA,
    /** Anything we don't recognise — Phase 4's rule engine decides what to do with it. */
    UNKNOWN
}