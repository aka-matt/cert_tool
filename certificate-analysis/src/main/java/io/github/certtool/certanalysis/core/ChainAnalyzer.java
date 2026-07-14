package io.github.certtool.certanalysis.core;

import io.github.certtool.domain.certificate.ChainAnalysis;
import io.github.certtool.domain.certificate.ChainAnalysis.X509CertificateRef;
import io.github.certtool.domain.certificate.ChainIssue;
import io.github.certtool.domain.certificate.ChainIssueCode;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Performs the static chain checks called out in spec §5 + §17.3.
 *
 * <p>The input is an ordered list of certificates as returned by the JDK's {@code KeyStore}:
 * end-entity first, root last. We check each pair (parent, child) for issuer linkage and
 * cryptographic signature; each cert individually for validity window; intermediates for
 * Basic Constraints presence and {@code keyCertSign} usage; the final cert for self-signature.
 *
 * <p>Per spec §5, this is NOT a full PKIX online check — we do not consult CRLs, OCSP, or any
 * network source. We also do not produce {@code AssessmentStatus} values (Phase 4's job).
 */
public final class ChainAnalyzer {

    private ChainAnalyzer() {}

    public static ChainAnalysis analyze(List<X509Certificate> chain) {
        Objects.requireNonNull(chain, "chain");
        List<ChainIssue> issues = new ArrayList<>();
        List<X509CertificateRef> refs = new ArrayList<>(chain.size());
        Instant now = Instant.now();

        for (int i = 0; i < chain.size(); i++) {
            X509Certificate cert = chain.get(i);
            refs.add(new X509CertificateRef(
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    HexFormat.of().formatHex(cert.getSerialNumber().toByteArray())));
            // Per-cert validity check.
            switch (new io.github.certtool.domain.certificate.ValidityWindow(
                    cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant()).validAt(now)) {
                case EXPIRED -> issues.add(new ChainIssue(
                        ChainIssueCode.EXPIRED, i, "notAfter=" + cert.getNotAfter().toInstant()));
                case NOT_YET_VALID -> issues.add(new ChainIssue(
                        ChainIssueCode.NOT_YET_VALID, i, "notBefore=" + cert.getNotBefore().toInstant()));
                default -> { /* VALID */ }
            }
        }

        if (chain.isEmpty()) {
            return new ChainAnalysis(refs, issues);
        }

        // Order anomaly: end-entity first, root last is the convention. If the LAST cert
        // isn't a self-signed root, flag ORDER_ANOMALY. We compute self-signed via structural
        // + cryptographic check.
        X509Certificate last = chain.get(chain.size() - 1);
        if (!SelfSignedVerifier.check(last).isFullySelfSigned()) {
            issues.add(new ChainIssue(
                    ChainIssueCode.ORDER_ANOMALY,
                    chain.size() - 1,
                    "Last certificate is not a self-signed root"));
            // Also surface ROOT_NOT_SELF_SIGNED if applicable.
            if (!last.getSubjectX500Principal().equals(last.getIssuerX500Principal())) {
                issues.add(new ChainIssue(
                        ChainIssueCode.ROOT_NOT_SELF_SIGNED,
                        chain.size() - 1,
                        "Last cert subject != issuer"));
            }
        }

        // Parent/child issuer linkage + signature verification.
        for (int i = 0; i < chain.size() - 1; i++) {
            X509Certificate child = chain.get(i);
            X509Certificate parent = chain.get(i + 1);
            // 1. issuer linkage
            if (!child.getIssuerX500Principal().equals(parent.getSubjectX500Principal())) {
                issues.add(new ChainIssue(
                        ChainIssueCode.ISSUER_MISMATCH, i,
                        "child issuer=" + child.getIssuerX500Principal().getName()
                                + " parent subject=" + parent.getSubjectX500Principal().getName()));
                continue; // no point in signature verification if subjects don't link
            }
            // 2. signature verifies under parent's public key
            try {
                child.verify(parent.getPublicKey());
            } catch (GeneralSecurityException e) {
                issues.add(new ChainIssue(
                        ChainIssueCode.SIGNATURE_INVALID, i,
                        "Signature did not verify under parent's public key"));
            }
        }

        // Intermediate (non-root, non-leaf) Basic Constraints + keyCertSign checks.
        for (int i = 1; i < chain.size() - 1; i++) {
            X509Certificate ca = chain.get(i);
            ExtensionAnalysis ext = Extensions.inspect(ca);
            if (!ext.basicConstraints().isCa()) {
                issues.add(new ChainIssue(
                        ChainIssueCode.MISSING_BASIC_CONSTRAINTS, i,
                        "CA flag not set in Basic Constraints"));
            }
            if (!ext.keyUsage().keyCertSign()) {
                issues.add(new ChainIssue(
                        ChainIssueCode.KEY_USAGE_MISSING_SIGNING, i,
                        "Key Usage does not include keyCertSign"));
            }
        }

        return new ChainAnalysis(refs, issues);
    }
}