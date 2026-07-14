package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Fingerprints")
class FingerprintsTest {

    @Test
    @DisplayName("computes deterministic SHA-256 and SHA-1 fingerprints")
    void deterministic() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=f"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        FingerprintBundle a = Fingerprints.of(cert);
        FingerprintBundle b = Fingerprints.of(cert);

        assertThat(a).isEqualTo(b);
        assertThat(a.sha256()).matches("[0-9a-f]{64}");
        assertThat(a.sha1()).matches("[0-9a-f]{40}");
    }

    @Test
    @DisplayName("two distinct certificates produce distinct fingerprints")
    void distinct() {
        X509Certificate c1 = CertificateGenerator.selfSigned(
                new X500Principal("CN=a"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        X509Certificate c2 = CertificateGenerator.selfSigned(
                new X500Principal("CN=b"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        assertThat(Fingerprints.of(c1).sha256())
                .isNotEqualTo(Fingerprints.of(c2).sha256());
    }

    @Test
    @DisplayName("uppercase hex with colons format is also available")
    void formatted() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=f"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        FingerprintBundle fp = Fingerprints.of(cert);

        assertThat(fp.sha256Formatted()).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}");
        assertThat(fp.sha1Formatted()).matches("([0-9A-F]{2}:){19}[0-9A-F]{2}");
    }
}