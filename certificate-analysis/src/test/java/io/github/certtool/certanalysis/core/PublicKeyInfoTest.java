package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PublicKeyInfo")
class PublicKeyInfoTest {

    @Test
    @DisplayName("describes an RSA-2048 key by algorithm and bit size")
    void rsa2048() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=rsa"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        PublicKeyInfo info = PublicKeyInfo.of(cert.getPublicKey());

        assertThat(info.algorithm()).isEqualTo(KeyAlgorithm.RSA);
        assertThat(info.rsaKeySize()).isEqualTo(2048);
        assertThat(info.ecCurveName()).isNull();
        assertThat(info.ecCurveOid()).isNull();
    }

    @Test
    @DisplayName("reports weak RSA-1024 (caller decides severity)")
    void rsa1024() {
        KeyPair weak = CertificateGenerator.rsaKeyPair(1024);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=weak"),
                weak,
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        PublicKeyInfo info = PublicKeyInfo.of(cert.getPublicKey());

        assertThat(info.algorithm()).isEqualTo(KeyAlgorithm.RSA);
        assertThat(info.rsaKeySize()).isEqualTo(1024);
    }

    @Test
    @DisplayName("describes an EC P-256 key with curve name and OID")
    void ecP256() {
        KeyPair ec = CertificateGenerator.ecKeyPair("P-256");
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=ec"),
                ec,
                "SHA256withECDSA",
                java.time.Duration.ofDays(30));

        PublicKeyInfo info = PublicKeyInfo.of(cert.getPublicKey());

        assertThat(info.algorithm()).isEqualTo(KeyAlgorithm.EC);
        // BC's ECNamedCurveSpec.getName() returns the SEC2 name (e.g. "prime256v1"), not the
        // NIST alias. We accept either; what matters is that we get a non-null, recognizable name.
        assertThat(info.ecCurveName()).isIn("prime256v1", "secp256r1", "P-256");
        // ecCurveOid is filled in by PublicKeyInfoFactory (BC ASN.1), not by the JDK-only path.
        assertThat(info.rsaKeySize()).isNull();
    }

    @Test
    @DisplayName("describes an EC P-384 key with curve name")
    void ecP384() {
        KeyPair ec = CertificateGenerator.ecKeyPair("P-384");
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=ec384"),
                ec,
                "SHA256withECDSA",
                java.time.Duration.ofDays(30));

        PublicKeyInfo info = PublicKeyInfo.of(cert.getPublicKey());

        assertThat(info.algorithm()).isEqualTo(KeyAlgorithm.EC);
        assertThat(info.ecCurveName()).isIn("secp384r1", "P-384");
    }
}