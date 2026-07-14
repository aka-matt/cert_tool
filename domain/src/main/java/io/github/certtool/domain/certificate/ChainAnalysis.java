package io.github.certtool.domain.certificate;

import java.util.List;
import java.util.Objects;

/**
 * Result of analyzing an ordered certificate chain.
 *
 * <p>{@code certificates} is the input chain as supplied by the caller (typically
 * end-entity first, root last — the order returned by {@code KeyStore.getCertificateChain}).
 * {@code issues} is the deduplicated list of detected problems; an empty list means the chain
 * passed all static checks.
 *
 * <p>This is descriptive only. The rule engine (Phase 4) maps issues into
 * {@code AssessmentFinding} records with the proper {@code AssessmentStatus}. We never claim
 * "full PKIX online validation" — see spec §5.
 */
public record ChainAnalysis(List<X509CertificateRef> certificates, List<ChainIssue> issues) {

    public ChainAnalysis {
        certificates = List.copyOf(certificates);
        issues = List.copyOf(issues);
        Objects.requireNonNull(certificates, "certificates");
    }

    /**
     * Lightweight certificate reference inside a chain — we don't want to leak the whole
     * X509Certificate into the chain domain type, but we DO need the subject for the UI.
     */
    public record X509CertificateRef(String subject, String issuer, String serialNumberHex) {

        public X509CertificateRef {
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(serialNumberHex, "serialNumberHex");
        }
    }

    public boolean isClean() {
        return issues.isEmpty();
    }
}