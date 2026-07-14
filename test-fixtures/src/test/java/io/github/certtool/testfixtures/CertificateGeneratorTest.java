package io.github.certtool.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CertificateGenerator}.
 *
 * <p>Per spec §13, the test fixtures MUST be generated in-test or stored in dedicated test
 * resources. No external network and no real user keystores.
 */
@DisplayName("CertificateGenerator")
class CertificateGeneratorTest {

    private static final X500Principal DN = new X500Principal("CN=test");

    @Test
    @DisplayName("generates an RSA-2048 self-signed certificate")
    void rsa2048SelfSigned() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withRSA", Duration.ofDays(30));

        assertThat(cert.getPublicKey().getAlgorithm()).isEqualTo("RSA");
        assertThat(((java.security.interfaces.RSAPublicKey) cert.getPublicKey()).getModulus().bitLength())
                .isEqualTo(2048);
        assertThat(cert.getSubjectX500Principal()).isEqualTo(DN);
        assertThat(cert.getIssuerX500Principal()).isEqualTo(DN);
        // JDK 17 returns the algorithm name upper-cased via X509Certificate#getSigAlgName.
        assertThat(cert.getSigAlgName()).isEqualToIgnoringCase("SHA256withRSA");
    }

    @Test
    @DisplayName("generates an RSA-3072 self-signed certificate")
    void rsa3072SelfSigned() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(3072);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withRSA", Duration.ofDays(30));

        assertThat(((java.security.interfaces.RSAPublicKey) cert.getPublicKey()).getModulus().bitLength())
                .isEqualTo(3072);
    }

    @Test
    @DisplayName("generates a weak RSA-1024 self-signed certificate for negative tests")
    void weakRsa1024() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(1024);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withRSA", Duration.ofDays(30));

        assertThat(((java.security.interfaces.RSAPublicKey) cert.getPublicKey()).getModulus().bitLength())
                .isEqualTo(1024);
    }

    @Test
    @DisplayName("generates an EC P-256 self-signed certificate")
    void ecP256() throws Exception {
        KeyPair kp = CertificateGenerator.ecKeyPair("P-256");
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withECDSA", Duration.ofDays(30));

        assertThat(cert.getPublicKey().getAlgorithm()).isEqualTo("EC");
    }

    @Test
    @DisplayName("generates a SHA-1 signed certificate (negative test material)")
    void sha1Signed() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA1withRSA", Duration.ofDays(30));

        assertThat(cert.getSigAlgName()).isEqualToIgnoringCase("SHA1withRSA");
    }

    @Test
    @DisplayName("generates an expired certificate (negative test material)")
    void expiredCert() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(
                        DN, kp, "SHA256withRSA", Duration.ofDays(-30), Duration.ofDays(-1));

        assertThat(cert.getNotAfter()).isBefore(new Date());
    }

    @Test
    @DisplayName("generates a not-yet-valid certificate (negative test material)")
    void notYetValidCert() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(
                        DN, kp, "SHA256withRSA", Duration.ofDays(1), Duration.ofDays(30));

        assertThat(cert.getNotBefore()).isAfter(new Date());
    }

    @Test
    @DisplayName("generates an issuer-signed certificate with a different issuer DN")
    void issuerSigned() throws Exception {
        KeyPair rootKp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate root =
                CertificateGenerator.selfSigned(DN, rootKp, "SHA256withRSA", Duration.ofDays(365));

        X500Principal subDn = new X500Principal("CN=leaf");
        KeyPair leafKp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate leaf =
                CertificateGenerator.issuedBy(
                        subDn,
                        leafKp,
                        root,
                        rootKp.getPrivate(),
                        "SHA256withRSA",
                        Duration.ofDays(30));

        assertThat(leaf.getSubjectX500Principal()).isEqualTo(subDn);
        assertThat(leaf.getIssuerX500Principal()).isEqualTo(DN);
    }

    @Test
    @DisplayName("serial numbers are positive")
    void serialNumberIsPositive() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withRSA", Duration.ofDays(30));

        assertThat(cert.getSerialNumber()).isGreaterThan(BigInteger.ZERO);
    }

    @Test
    @DisplayName("validity window is computed from now")
    void validityWindow() throws Exception {
        Instant before = Instant.now();
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert =
                CertificateGenerator.selfSigned(DN, kp, "SHA256withRSA", Duration.ofDays(10));
        Instant after = Instant.now().plus(Duration.ofDays(10));

        assertThat(cert.getNotBefore().toInstant()).isAfterOrEqualTo(before.minusSeconds(2));
        assertThat(cert.getNotAfter().toInstant()).isBeforeOrEqualTo(after.plusSeconds(2));
    }
}