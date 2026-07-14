package io.github.certtool.testfixtures;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;

/**
 * Helpers for building realistic certificate chains: root → intermediate → leaf.
 *
 * <p>Used by Phase 3 (chain analysis) and Phase 4 (rule engine) tests.
 */
public final class CertificateChains {

    private CertificateChains() {}

    /** A 3-cert chain (root → intermediate → leaf), end-entity-first. */
    public static Chain3 rootIntermediateLeaf() {
        return rootIntermediateLeaf(Duration.ofDays(365), Duration.ofDays(180), Duration.ofDays(90));
    }

    /** A 3-cert chain with explicit validity windows. */
    public static Chain3 rootIntermediateLeaf(Duration rootValid, Duration intValid, Duration leafValid) {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        KeyPair intKey = CertificateGenerator.rsaKeyPair(2048);
        KeyPair leafKey = CertificateGenerator.rsaKeyPair(2048);

        X500Principal rootDn = new X500Principal("CN=Root CA");
        X500Principal intDn = new X500Principal("CN=Intermediate CA");
        X500Principal leafDn = new X500Principal("CN=leaf.example");

        // Root CA — BasicConstraints(CA=true), KeyUsage(keyCertSign + cRLSign). Self-signed.
        X509Certificate root = buildCa(rootDn, rootKey, rootKey.getPrivate(), rootValid);

        // Intermediate CA — BasicConstraints + KeyUsage, signed by root.
        X509Certificate intermediate = buildIntermediate(intDn, intKey, root, rootKey.getPrivate(), intValid);

        // Leaf — EKU(serverAuth, clientAuth), signed by intermediate.
        X509Certificate leaf = buildLeaf(leafDn, leafKey, intermediate, intKey.getPrivate(), leafValid);

        return new Chain3(leaf, intermediate, root, leafKey, intKey, rootKey);
    }

    private static X509Certificate buildCa(X500Principal dn, KeyPair key, java.security.PrivateKey signingKey,
                                            Duration validity) {
        X509Certificate base = CertificateGenerator.selfSigned(
                dn, key, "SHA256withRSA", validity);
        return CertificateGenerator.withExtensions(base, signingKey, b -> {
            try {
                b.addExtension(new Extension(
                        Extension.basicConstraints,
                        /* critical */ true,
                        new BasicConstraints(/* ca */ true).getEncoded()));
                b.addExtension(new Extension(
                        Extension.keyUsage,
                        /* critical */ true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign).getEncoded()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static X509Certificate buildIntermediate(X500Principal dn, KeyPair subjectKey,
                                                       X509Certificate issuerCert, java.security.PrivateKey issuerKey,
                                                       Duration validity) {
        // Build a base intermediate signed by issuer, then re-decorate with extensions using the
        // issuer's key to preserve the signature chain.
        X509Certificate base = CertificateGenerator.issuedBy(
                dn, subjectKey, issuerCert, issuerKey, "SHA256withRSA", validity);
        return CertificateGenerator.withExtensions(base, issuerKey, b -> {
            try {
                b.addExtension(new Extension(
                        Extension.basicConstraints,
                        /* critical */ true,
                        new BasicConstraints(/* ca */ true).getEncoded()));
                b.addExtension(new Extension(
                        Extension.keyUsage,
                        /* critical */ true,
                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign).getEncoded()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static X509Certificate buildLeaf(X500Principal dn, KeyPair leafKey,
                                              X509Certificate issuerCert, java.security.PrivateKey issuerKey,
                                              Duration validity) {
        X509Certificate base = CertificateGenerator.issuedBy(
                dn, leafKey, issuerCert, issuerKey, "SHA256withRSA", validity);
        return CertificateGenerator.withExtensions(base, issuerKey, b -> {
            try {
                b.addExtension(new Extension(
                        Extension.extendedKeyUsage,
                        /* critical */ false,
                        new ExtendedKeyUsage(new KeyPurposeId[] {
                                KeyPurposeId.id_kp_serverAuth,
                                KeyPurposeId.id_kp_clientAuth
                        }).getEncoded()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /**
     * Holds the three certs (end-entity-first, root-last) and the matching key pairs so tests can
     * re-sign, replace, or otherwise mutate them.
     */
    public record Chain3(
            X509Certificate leaf,
            X509Certificate intermediate,
            X509Certificate root,
            KeyPair leafKey,
            KeyPair intermediateKey,
            KeyPair rootKey) {

        /** Returns the chain ordered end-entity first, root last. */
        public java.util.List<X509Certificate> ordered() {
            return java.util.List.of(leaf, intermediate, root);
        }

        /** Returns the chain in reverse: root first, end-entity last. */
        public java.util.List<X509Certificate> rootFirst() {
            return java.util.List.of(root, intermediate, leaf);
        }
    }
}