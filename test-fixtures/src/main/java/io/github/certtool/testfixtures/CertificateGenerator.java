package io.github.certtool.testfixtures;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Utility for building X.509 certificates in tests.
 *
 * <p>Per spec §13, test certificates are generated in-test or stored in dedicated test resources
 * — never loaded from external networks or real user keystores.
 */
public final class CertificateGenerator {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private CertificateGenerator() {}

    /** Generates an RSA key pair of the given modulus size using BC. */
    public static KeyPair rsaKeyPair(int bits) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            g.initialize(bits, new SecureRandom());
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate RSA key pair", e);
        }
    }

    /** Generates an EC key pair on the named curve (e.g. "P-256", "P-384", "P-521"). */
    public static KeyPair ecKeyPair(String curveName) {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            g.initialize(new org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec(curveName), new SecureRandom());
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate EC key pair on " + curveName, e);
        }
    }

    /**
     * Generates a self-signed certificate valid for {@code validity} starting now.
     *
     * @param subjectDn   subject DN (also the issuer DN)
     * @param keyPair     the key pair (public key certifies, private key signs)
     * @param signatureAlg e.g. "SHA256withRSA", "SHA1withRSA", "SHA256withECDSA"
     * @param validity    how long the cert is valid for, starting now
     */
    public static X509Certificate selfSigned(
            X500Principal subjectDn, KeyPair keyPair, String signatureAlg, Duration validity) {
        return selfSigned(subjectDn, keyPair, signatureAlg, Duration.ZERO, validity);
    }

    /**
     * Generates a self-signed certificate whose notBefore is offset by {@code notBeforeOffset}
     * from now.
     */
    public static X509Certificate selfSigned(
            X500Principal subjectDn,
            KeyPair keyPair,
            String signatureAlg,
            Duration notBeforeOffset,
            Duration validity) {
        try {
            X500Name name = X500Name.getInstance(subjectDn.getEncoded());
            SubjectPublicKeyInfo spki =
                    SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
            BigInteger serial = new BigInteger(64, new SecureRandom());
            Instant notBefore = Instant.now().plus(notBeforeOffset);
            Instant notAfter = notBefore.plus(validity);

            X509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            name,
                            serial,
                            Date.from(notBefore),
                            Date.from(notAfter),
                            name,
                            keyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder(signatureAlg)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(keyPair.getPrivate());
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build self-signed certificate", e);
        }
    }

    /** Generates a certificate signed by the given issuer. */
    public static X509Certificate issuedBy(
            X500Principal subjectDn,
            KeyPair subjectKeyPair,
            X509Certificate issuer,
            PrivateKey issuerPrivateKey,
            String signatureAlg,
            Duration validity) {
        try {
            X500Name subjectName = X500Name.getInstance(subjectDn.getEncoded());
            // Use the issuer cert's SUBJECT as the leaf's issuer DN.
            X500Name issuerName = X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded());
            SubjectPublicKeyInfo spki =
                    SubjectPublicKeyInfo.getInstance(subjectKeyPair.getPublic().getEncoded());
            BigInteger serial = new BigInteger(64, new SecureRandom());
            Instant notBefore = Instant.now();
            Instant notAfter = notBefore.plus(validity);

            X509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            issuerName,
                            serial,
                            Date.from(notBefore),
                            Date.from(notAfter),
                            subjectName,
                            subjectKeyPair.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder(signatureAlg)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(issuerPrivateKey);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build issuer-signed certificate", e);
        }
    }

    /**
     * Builds a fresh certificate that preserves the original's subject, issuer, public key, and
     * validity window, but signs the TBS with the supplied (deliberately wrong) private key. The
     * resulting cert is structurally self-signed (subject==issuer) but its signature will not
     * verify with its embedded public key — perfect for negative chain-validation tests.
     */
    public static X509Certificate withTamperedSignature(X509Certificate original, PrivateKey wrongKey) {
        try {
            X500Name subjectName = X500Name.getInstance(original.getSubjectX500Principal().getEncoded());
            X500Name issuerName = X500Name.getInstance(original.getIssuerX500Principal().getEncoded());
            SubjectPublicKeyInfo spki =
                    SubjectPublicKeyInfo.getInstance(original.getPublicKey().getEncoded());
            BigInteger serial = original.getSerialNumber();
            Date notBefore = original.getNotBefore();
            Date notAfter = original.getNotAfter();

            X509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            issuerName,
                            serial,
                            notBefore,
                            notAfter,
                            subjectName,
                            original.getPublicKey());
            // Use SHA256withRSA by default — caller can override via another overload if needed.
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(wrongKey);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build tampered certificate", e);
        }
    }

    /**
     * Decorator hook: lets tests add arbitrary extensions to a freshly built self-signed
     * certificate. The {@code signingKey} must match {@code original.getPublicKey()}.
     */
    public static X509Certificate withExtensions(
            X509Certificate original,
            PrivateKey signingKey,
            java.util.function.Consumer<X509v3CertificateBuilder> decorator) {
        try {
            X500Name subjectName = X500Name.getInstance(original.getSubjectX500Principal().getEncoded());
            X500Name issuerName = X500Name.getInstance(original.getIssuerX500Principal().getEncoded());
            Date notBefore = original.getNotBefore();
            Date notAfter = original.getNotAfter();
            BigInteger serial = original.getSerialNumber();

            X509v3CertificateBuilder builder =
                    new JcaX509v3CertificateBuilder(
                            issuerName,
                            serial,
                            notBefore,
                            notAfter,
                            subjectName,
                            original.getPublicKey());
            decorator.accept(builder);
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(signingKey);
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build certificate with extensions", e);
        }
    }

    /**
     * Adds an unrecognized critical extension with the given OID. Tests use this to assert that
     * the parser records the OID without crashing.
     */
    public static X509Certificate withUnrecognizedCriticalExtension(
            X509Certificate original, PrivateKey signingKey, String oid) {
        return withExtensions(original, signingKey, b -> {
            try {
                org.bouncycastle.asn1.x509.Extension unrecognized =
                        new org.bouncycastle.asn1.x509.Extension(
                                new org.bouncycastle.asn1.ASN1ObjectIdentifier(oid),
                                /* critical */ true,
                                new byte[] {0x01, 0x02, 0x03});
                b.addExtension(unrecognized);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to add unrecognized extension", e);
            }
        });
    }
}