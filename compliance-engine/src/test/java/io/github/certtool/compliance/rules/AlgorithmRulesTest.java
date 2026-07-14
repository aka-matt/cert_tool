package io.github.certtool.compliance.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.certanalysis.core.CertificateAnalyzer;
import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.compliance.rules.algorithm.AlgorithmRules;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuleContext;
import io.github.certtool.domain.context.RuntimeEnvironment;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.ExpirationPolicy;
import io.github.certtool.domain.profile.JksPrivateKeyPolicy;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.ProfileId;
import io.github.certtool.domain.profile.Sha1Policy;
import io.github.certtool.domain.profile.Standard;
import io.github.certtool.domain.profile.UnknownAlgorithmPolicy;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AlgorithmRules")
class AlgorithmRulesTest {

    private static Profile profile(Sha1Policy sha1Policy, UnknownAlgorithmPolicy unknownPolicy) {
        return new Profile(
                ProfileId.FIPS_140_3_ASSESSMENT,
                "FIPS 140-3 Compatibility Assessment",
                Standard.FIPS_140_3,
                "2026-01-01",
                List.of("RSA", "EC"),
                List.of("MD2", "MD5"),
                Map.of("RSA", 3072),
                List.of("P-256", "P-384"),
                sha1Policy,
                ExpirationPolicy.FAIL,
                unknownPolicy,
                JksPrivateKeyPolicy.FAIL,
                "BCFIPS",
                true,
                List.of());
    }

    private static RuntimeEnvironment runtime() {
        return new RuntimeEnvironment(
                "Eclipse Adoptium",
                "17.0.11",
                "Linux",
                "amd64",
                List.of(new ProviderInfo("SUN", "17", "SUN", false, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
    }

    private static RuleContext contextFor(X509Certificate cert) {
        CertificateAnalysis a = CertificateAnalyzer.analyze(cert);
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.BCFKS,
                ContentEncoding.BINARY,
                "/tmp/x.bcfks",
                1024L,
                true,
                List.of("a"));
        EntryAnalysis entry = new EntryAnalysis("a", EntryType.TRUSTED_CERTIFICATE, a, null, null);
        return new RuleContext(ks, List.of(entry), runtime());
    }

    private static AssessmentFinding find(AssessmentReport report, String ruleId) {
        return report.findings().stream()
                .filter(f -> f.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding for " + ruleId));
    }

    private static X509Certificate sha256Rsa(int bits) {
        KeyPair kp = CertificateGenerator.rsaKeyPair(bits);
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
    }

    private static X509Certificate sha1Rsa() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA1withRSA", Duration.ofDays(30));
    }

    private static X509Certificate md5Rsa() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "MD5withRSA", Duration.ofDays(30));
    }

    @Test
    @DisplayName("WeakRsaKeyRule: FAIL on RSA-1024 (below 3072 minimum)")
    void weakRsaFails() {
        X509Certificate cert = sha256Rsa(1024);
        RuleContext ctx = contextFor(cert);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(Sha1Policy.DISALLOW, UnknownAlgorithmPolicy.NOT_ASSESSABLE), RuleRegistry.of(AlgorithmRules.all()));
        assertThat(find(report, "ALGORITHM.RSA_KEY_SIZE").status()).isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("WeakRsaKeyRule: PASS on RSA-3072 (meets minimum)")
    void rsa3072Passes() {
        X509Certificate cert = sha256Rsa(3072);
        RuleContext ctx = contextFor(cert);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(Sha1Policy.DISALLOW, UnknownAlgorithmPolicy.NOT_ASSESSABLE), RuleRegistry.of(AlgorithmRules.all()));
        assertThat(find(report, "ALGORITHM.RSA_KEY_SIZE").status()).isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("Sha1SignatureRule: FAIL when profile disallows SHA-1")
    void sha1Fails() {
        X509Certificate cert = sha1Rsa();
        RuleContext ctx = contextFor(cert);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(Sha1Policy.DISALLOW, UnknownAlgorithmPolicy.NOT_ASSESSABLE), RuleRegistry.of(AlgorithmRules.all()));
        assertThat(find(report, "ALGORITHM.SHA1_SIGNATURE").status()).isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("Sha1SignatureRule: WARNING when profile permits legacy SHA-1 verification")
    void sha1Warns() {
        X509Certificate cert = sha1Rsa();
        RuleContext ctx = contextFor(cert);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(Sha1Policy.WARN, UnknownAlgorithmPolicy.NOT_ASSESSABLE), RuleRegistry.of(AlgorithmRules.all()));
        assertThat(find(report, "ALGORITHM.SHA1_SIGNATURE").status()).isEqualTo(AssessmentStatus.WARNING);
    }

    @Test
    @DisplayName("Md5SignatureRule: FAIL on any MD5 (always disallowed)")
    void md5Fails() {
        X509Certificate cert = md5Rsa();
        RuleContext ctx = contextFor(cert);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(Sha1Policy.DISALLOW, UnknownAlgorithmPolicy.NOT_ASSESSABLE), RuleRegistry.of(AlgorithmRules.all()));
        assertThat(find(report, "ALGORITHM.MD5_SIGNATURE").status()).isEqualTo(AssessmentStatus.FAIL);
    }
}