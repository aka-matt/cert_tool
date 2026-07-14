package io.github.certtool.domain.certificate;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Immutable analysis of a single X.509 certificate — every fact the spec §5 requires.
 *
 * <p>This is the canonical model the UI renders and the rule engine consumes. It is descriptive
 * only: it never assigns PASS/WARNING/FAIL — that's the rule engine's job in Phase 4.
 *
 * <p>{@code subject} / {@code issuer} are RFC 2253-encoded DN strings (no ASN.1 binary blob).
 * {@code serialNumberHex} and {@code serialNumberDecimal} both render the same value.
 */
public record CertificateAnalysis(
        String subject,
        String issuer,
        BigInteger serialNumber,
        String serialNumberHex,
        String serialNumberDecimal,
        int x509Version,
        ValidityWindow validity,
        ValidityState currentValidity,
        String signatureAlgorithm,
        String signatureAlgorithmOid,
        PublicKeyInfo publicKeyInfo,
        ExtensionAnalysis extensions,
        FingerprintBundle fingerprints,
        SelfSignedStatus selfSigned,
        String pem) {

    public CertificateAnalysis {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(serialNumber, "serialNumber");
        Objects.requireNonNull(serialNumberHex, "serialNumberHex");
        Objects.requireNonNull(serialNumberDecimal, "serialNumberDecimal");
        Objects.requireNonNull(validity, "validity");
        Objects.requireNonNull(currentValidity, "currentValidity");
        Objects.requireNonNull(signatureAlgorithm, "signatureAlgorithm");
        Objects.requireNonNull(signatureAlgorithmOid, "signatureAlgorithmOid");
        Objects.requireNonNull(publicKeyInfo, "publicKeyInfo");
        Objects.requireNonNull(extensions, "extensions");
        Objects.requireNonNull(fingerprints, "fingerprints");
        Objects.requireNonNull(selfSigned, "selfSigned");
        Objects.requireNonNull(pem, "pem");
    }
}