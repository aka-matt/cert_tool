package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CertificateAnalyzer")
class CertificateAnalyzerTest {

    @Test
    @DisplayName("analyzes an RSA-2048 SHA-256 self-signed cert end-to-end")
    void rsaSelfSigned() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=example.com"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        CertificateAnalysis a = CertificateAnalyzer.analyze(cert);

        assertThat(a.subject()).contains("CN=example.com");
        assertThat(a.issuer()).contains("CN=example.com");
        assertThat(a.x509Version()).isEqualTo(3);
        assertThat(a.currentValidity()).isEqualTo(ValidityState.VALID);
        assertThat(a.signatureAlgorithm()).isEqualToIgnoringCase("SHA256withRSA");
        assertThat(a.publicKeyInfo().algorithm()).isEqualTo(KeyAlgorithm.RSA);
        assertThat(a.publicKeyInfo().rsaKeySize()).isEqualTo(2048);
        assertThat(a.selfSigned().isFullySelfSigned()).isTrue();
        assertThat(a.fingerprints().sha256()).matches("[0-9a-f]{64}");
        assertThat(a.pem()).contains("BEGIN CERTIFICATE").contains("END CERTIFICATE");
        assertThat(a.serialNumberHex()).matches("[0-9a-f]+");
        assertThat(a.serialNumberDecimal()).matches("[0-9]+");
    }

    @Test
    @DisplayName("captures a SHA-1 signature algorithm as a fact (rule engine will rate it)")
    void sha1Signature() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=sha1"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA1withRSA",
                java.time.Duration.ofDays(30));

        CertificateAnalysis a = CertificateAnalyzer.analyze(cert);

        assertThat(a.signatureAlgorithm()).isEqualToIgnoringCase("SHA1withRSA");
        assertThat(a.signatureAlgorithmOid()).isNotBlank();
    }

    @Test
    @DisplayName("marks an expired cert with ValidityState.EXPIRED")
    void expiredCert() {
        // A real expired cert is one whose notBefore is in the past and notAfter has passed.
        // Use a long-ago notBefore with short validity that ended yesterday.
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=old"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(-365),
                java.time.Duration.ofDays(30));

        CertificateAnalysis a = CertificateAnalyzer.analyze(cert);

        assertThat(a.currentValidity()).isEqualTo(ValidityState.EXPIRED);
    }
}