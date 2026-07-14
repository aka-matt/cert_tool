package io.github.certtool.certanalysis.core;

import io.github.certtool.domain.certificate.SelfSignedStatus;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * Distinguishes "looks self-signed" (subject==issuer) from "actually self-signed"
 * (signature verifies with own public key).
 *
 * <p>Per spec §5, the parser exposes both a "is self-signed" flag and a "self-signed verification
 * result". A cert that is structurally self-signed but fails signature verification is suspicious
 * — possibly forged or corrupted.
 */
public final class SelfSignedVerifier {

    private SelfSignedVerifier() {}

    public static SelfSignedStatus check(X509Certificate cert) {
        Objects.requireNonNull(cert, "cert");
        boolean structural = structuralSelfSigned(cert);
        boolean verifies;
        if (!structural) {
            verifies = false;
        } else {
            verifies = signatureVerifies(cert);
        }
        return new SelfSignedStatus(structural, verifies);
    }

    private static boolean structuralSelfSigned(X509Certificate cert) {
        var subject = cert.getSubjectX500Principal();
        var issuer = cert.getIssuerX500Principal();
        return subject != null && subject.equals(issuer);
    }

    private static boolean signatureVerifies(X509Certificate cert) {
        try {
            cert.verify(cert.getPublicKey());
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}