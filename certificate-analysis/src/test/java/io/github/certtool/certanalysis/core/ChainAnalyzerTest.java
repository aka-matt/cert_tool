package io.github.certtool.certanalysis.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.ChainAnalysis;
import io.github.certtool.domain.certificate.ChainIssueCode;
import io.github.certtool.testfixtures.CertificateChains;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChainAnalyzer")
class ChainAnalyzerTest {

    @Test
    @DisplayName("clean 3-cert chain passes all checks")
    void cleanChain() {
        CertificateChains.Chain3 c = CertificateChains.rootIntermediateLeaf();
        ChainAnalysis a = ChainAnalyzer.analyze(c.ordered());

        assertThat(a.isClean()).isTrue();
        assertThat(a.issues()).isEmpty();
        assertThat(a.certificates()).hasSize(3);
    }

    @Test
    @DisplayName("detects signature invalid when intermediate is tampered")
    void brokenSignature() {
        CertificateChains.Chain3 c = CertificateChains.rootIntermediateLeaf();
        // Re-sign the leaf's TBS with a wrong key, but keep its subject/issuer.
        KeyPair wrong = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate brokenLeaf = CertificateGenerator.withTamperedSignature(c.leaf(), wrong.getPrivate());

        ChainAnalysis a = ChainAnalyzer.analyze(java.util.List.of(brokenLeaf, c.intermediate(), c.root()));

        assertThat(a.issues())
                .extracting(i -> i.code())
                .contains(ChainIssueCode.SIGNATURE_INVALID);
    }

    @Test
    @DisplayName("detects expired intermediate")
    void expiredIntermediate() {
        KeyPair rootKey = CertificateGenerator.rsaKeyPair(2048);
        KeyPair intKey = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate root = CertificateGenerator.selfSigned(
                new X500Principal("CN=Root"), rootKey, "SHA256withRSA", Duration.ofDays(365));
        // notBefore 400 days ago, notAfter 30 days ago → expired.
        X509Certificate expiredInt = CertificateGenerator.selfSigned(
                new X500Principal("CN=Intermediate"),
                intKey,
                "SHA256withRSA",
                Duration.ofDays(-400),
                Duration.ofDays(370));
        X509Certificate leaf = CertificateGenerator.issuedBy(
                new X500Principal("CN=leaf"),
                intKey,
                expiredInt,
                intKey.getPrivate(),
                "SHA256withRSA",
                Duration.ofDays(90));

        ChainAnalysis a = ChainAnalyzer.analyze(java.util.List.of(leaf, expiredInt, root));

        assertThat(a.issues())
                .extracting(i -> i.code())
                .contains(ChainIssueCode.EXPIRED);
    }

    @Test
    @DisplayName("detects ORDER_ANOMALY when the last cert is not self-signed")
    void abnormalOrder() {
        CertificateChains.Chain3 c = CertificateChains.rootIntermediateLeaf();
        // Reverse the chain so the leaf is last. Now last cert != root.
        ChainAnalysis a = ChainAnalyzer.analyze(c.rootFirst());

        assertThat(a.issues())
                .extracting(i -> i.code())
                .contains(ChainIssueCode.ORDER_ANOMALY);
    }

    @Test
    @DisplayName("detects ISSUER_MISMATCH when chain is broken between two siblings")
    void issuerMismatch() {
        CertificateChains.Chain3 c = CertificateChains.rootIntermediateLeaf();
        // Inject a stray unrelated cert between leaf and intermediate.
        X509Certificate stranger = CertificateGenerator.selfSigned(
                new X500Principal("CN=stranger"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        ChainAnalysis a = ChainAnalyzer.analyze(java.util.List.of(c.leaf(), stranger, c.intermediate(), c.root()));

        assertThat(a.issues())
                .extracting(i -> i.code())
                .contains(ChainIssueCode.ISSUER_MISMATCH);
    }
}