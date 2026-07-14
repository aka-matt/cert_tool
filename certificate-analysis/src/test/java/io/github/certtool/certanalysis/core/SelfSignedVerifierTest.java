package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.CertificateChains;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SelfSignedVerifier")
class SelfSignedVerifierTest {

    @Test
    @DisplayName("structural + cryptographic: a fresh self-signed cert verifies both")
    void selfSignedVerifies() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=root"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));

        SelfSignedStatus status = SelfSignedVerifier.check(cert);

        assertThat(status.structuralSelfSigned()).isTrue();
        assertThat(status.signatureVerifies()).isTrue();
    }

    @Test
    @DisplayName("structural + cryptographic: a chain leaf is neither structural nor signed by itself")
    void leafIsNotSelfSigned() {
        CertificateChains.Chain3 chain = CertificateChains.rootIntermediateLeaf();
        X509Certificate leaf = chain.leaf();

        SelfSignedStatus status = SelfSignedVerifier.check(leaf);

        assertThat(status.structuralSelfSigned()).isFalse();
        assertThat(status.signatureVerifies()).isFalse();
    }

    @Test
    @DisplayName("structural=true but signatureVerifies=false when the cert was tampered after signing")
    void tamperedSignature() {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=tampered"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        // Re-sign TBS with a DIFFERENT key — keeps subject==issuer (structural) but breaks sig.
        KeyPair wrong = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate broken = CertificateGenerator.withTamperedSignature(cert, wrong.getPrivate());

        SelfSignedStatus status = SelfSignedVerifier.check(broken);

        assertThat(status.structuralSelfSigned()).isTrue();
        assertThat(status.signatureVerifies()).isFalse();
    }
}