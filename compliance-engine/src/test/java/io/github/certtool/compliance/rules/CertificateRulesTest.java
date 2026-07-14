package io.github.certtool.compliance.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.certanalysis.core.CertificateAnalyzer;
import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.compliance.rules.certificate.CertificateRules;
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

@DisplayName("CertificateRules")
class CertificateRulesTest {

    private static Profile profile(ExpirationPolicy policy) {
        return new Profile(
                ProfileId.FIPS_140_3_ASSESSMENT,
                "FIPS 140-3 Compatibility Assessment",
                Standard.FIPS_140_3,
                "2026-01-01",
                List.of("RSA", "EC"),
                List.of("MD2", "MD5"),
                Map.of("RSA", 3072),
                List.of("P-256", "P-384"),
                Sha1Policy.DISALLOW,
                policy,
                UnknownAlgorithmPolicy.NOT_ASSESSABLE,
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

    private static X509Certificate validRsaCert() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
    }

    private static X509Certificate expiredCert() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        // notBefore 400 days ago, notAfter 30 days ago → expired.
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(-400), Duration.ofDays(370));
    }

    private static X509Certificate notYetValidCert() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        // notBefore 30 days in the future, valid for 30 days.
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30), Duration.ofDays(30));
    }

    @Test
    @DisplayName("ExpiredCertificateRule: PASS on a fresh cert")
    void freshCert() {
        RuleContext ctx = contextFor(validRsaCert());
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(ExpirationPolicy.FAIL), RuleRegistry.of(CertificateRules.all()));
        assertThat(find(report, "CERT.EXPIRED").status()).isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("ExpiredCertificateRule: FAIL when policy=FAIL and cert is expired")
    void expiredFails() {
        RuleContext ctx = contextFor(expiredCert());
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(ExpirationPolicy.FAIL), RuleRegistry.of(CertificateRules.all()));
        assertThat(find(report, "CERT.EXPIRED").status()).isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("ExpiredCertificateRule: WARNING when policy=WARN and cert is expired")
    void expiredWarns() {
        RuleContext ctx = contextFor(expiredCert());
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(ExpirationPolicy.WARN), RuleRegistry.of(CertificateRules.all()));
        assertThat(find(report, "CERT.EXPIRED").status()).isEqualTo(AssessmentStatus.WARNING);
    }

    @Test
    @DisplayName("NotYetValidCertificateRule: WARNING on a not-yet-valid cert")
    void notYetValid() {
        RuleContext ctx = contextFor(notYetValidCert());
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(ExpirationPolicy.FAIL), RuleRegistry.of(CertificateRules.all()));
        assertThat(find(report, "CERT.NOT_YET_VALID").status()).isEqualTo(AssessmentStatus.WARNING);
    }

    @Test
    @DisplayName("UnknownCriticalExtensionRule: FAIL when cert has unrecognized critical OID")
    void unrecognizedCriticalExt() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate owned = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
        X509Certificate decorated = CertificateGenerator.withUnrecognizedCriticalExtension(
                owned, kp.getPrivate(), "1.2.3.4.5.6.7.8.9");
        RuleContext ctx = contextFor(decorated);
        AssessmentReport report = new AssessmentEngine()
                .assess(ctx, profile(ExpirationPolicy.FAIL), RuleRegistry.of(CertificateRules.all()));
        assertThat(find(report, "CERT.UNRECOGNIZED_CRITICAL_EXTENSION").status())
                .isEqualTo(AssessmentStatus.FAIL);
    }
}