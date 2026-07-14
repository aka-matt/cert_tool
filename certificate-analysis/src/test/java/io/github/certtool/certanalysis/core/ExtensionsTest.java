package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Extensions")
class ExtensionsTest {

    @Test
    @DisplayName("reads Basic Constraints (CA=true, pathLen)")
    void basicConstraints() {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate base = CertificateGenerator.selfSigned(
                new X500Principal("CN=ca"),
                rootKey,
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        X509Certificate ca = CertificateGenerator.withExtensions(
                base, rootKey.getPrivate(),
                b -> {
                    try {
                        org.bouncycastle.asn1.x509.BasicConstraints bc =
                                new org.bouncycastle.asn1.x509.BasicConstraints(/* ca */ true);
                        b.addExtension(
                                new Extension(
                                        Extension.basicConstraints,
                                        /* critical */ true,
                                        bc.getEncoded()));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });

        ExtensionAnalysis ext = Extensions.inspect(ca);

        assertThat(ext.basicConstraints().isCa()).isTrue();
        // No path length set on this synthetic cert.
        assertThat(ext.basicConstraints().pathLength()).isNull();
    }

    @Test
    @DisplayName("reads Key Usage bits including keyCertSign")
    void keyUsage() {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate base = CertificateGenerator.selfSigned(
                new X500Principal("CN=ku"),
                rootKey,
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        X509Certificate ca = CertificateGenerator.withExtensions(
                base, rootKey.getPrivate(),
                b -> {
                    try {
                        b.addExtension(
                                new Extension(
                                        Extension.keyUsage,
                                        /* critical */ true,
                                        new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign).getEncoded()));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });

        ExtensionAnalysis ext = Extensions.inspect(ca);

        assertThat(ext.keyUsage().keyCertSign()).isTrue();
        assertThat(ext.keyUsage().cRLSign()).isTrue();
        assertThat(ext.keyUsage().digitalSignature()).isFalse();
    }

    @Test
    @DisplayName("reads Extended Key Usage OIDs")
    void extendedKeyUsage() {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate base = CertificateGenerator.selfSigned(
                new X500Principal("CN=eku"),
                rootKey,
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        X509Certificate ee = CertificateGenerator.withExtensions(
                base, rootKey.getPrivate(),
                b -> {
                    try {
                        b.addExtension(
                                new Extension(
                                        Extension.extendedKeyUsage,
                                        /* critical */ false,
                                        new ExtendedKeyUsage(
                                                new KeyPurposeId[] {
                                                        KeyPurposeId.id_kp_serverAuth,
                                                        KeyPurposeId.id_kp_clientAuth
                                                }).getEncoded()));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });

        ExtensionAnalysis ext = Extensions.inspect(ee);

        assertThat(ext.extendedKeyUsageOids())
                .contains(KeyPurposeId.id_kp_serverAuth.getId(),
                        KeyPurposeId.id_kp_clientAuth.getId());
    }

    @Test
    @DisplayName("captures unrecognized critical extensions without throwing")
    void unrecognizedCriticalExtension() {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate base = CertificateGenerator.selfSigned(
                new X500Principal("CN=unknown"),
                rootKey,
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        String exoticOid = "1.3.6.1.4.1.99999.42.7";
        X509Certificate weird = CertificateGenerator.withUnrecognizedCriticalExtension(
                base, rootKey.getPrivate(), exoticOid);

        ExtensionAnalysis ext = Extensions.inspect(weird);

        assertThat(ext.unrecognizedCriticalOids()).contains(exoticOid);
        assertThat(ext.criticalOids()).contains(exoticOid);
    }

    @Test
    @DisplayName("returns empty analysis for a cert with no extensions")
    void noExtensions() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=none"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        ExtensionAnalysis ext = Extensions.inspect(cert);

        assertThat(ext.subjectAlternativeNames()).isEmpty();
        assertThat(ext.issuerAlternativeNames()).isEmpty();
        assertThat(ext.extendedKeyUsageOids()).isEmpty();
        assertThat(ext.certificatePolicyOids()).isEmpty();
        assertThat(ext.crlDistributionPointUris()).isEmpty();
        assertThat(ext.aiaOcspUris()).isEmpty();
        assertThat(ext.aiaCaIssuerUris()).isEmpty();
        assertThat(ext.unrecognizedCriticalOids()).isEmpty();
    }
}