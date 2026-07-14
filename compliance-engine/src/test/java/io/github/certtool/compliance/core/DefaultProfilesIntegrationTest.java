package io.github.certtool.compliance.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.certanalysis.core.CertificateAnalyzer;
import io.github.certtool.compliance.loader.DefaultProfiles;
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
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.io.IOException;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Default profiles integration")
class DefaultProfilesIntegrationTest {

    private static RuntimeEnvironment bcFipsRuntime() {
        return new RuntimeEnvironment(
                "Eclipse Adoptium",
                "17.0.11",
                "Linux",
                "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "BouncyCastle FIPS Provider", true, true)),
                Instant.parse("2026-07-14T00:00:00Z"));
    }

    private static RuleContext contextFor(X509Certificate cert) {
        CertificateAnalysis a = CertificateAnalyzer.analyze(cert);
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY, "/tmp/x.bcfks", 1024L, true, List.of("a"));
        EntryAnalysis entry = new EntryAnalysis("a", EntryType.TRUSTED_CERTIFICATE, a, null, null);
        return new RuleContext(ks, List.of(entry), bcFipsRuntime());
    }

    private static AssessmentFinding findByRuleId(AssessmentReport report, String ruleId) {
        return report.findings().stream()
                .filter(f -> f.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding for " + ruleId));
    }

    @Test
    @DisplayName("FIPS 140-3 profile FAILs SHA-1, FIPS 140-2 legacy WARNS")
    void profileSwitchingSha1() throws IOException {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA1withRSA", Duration.ofDays(30));
        RuleContext ctx = contextFor(cert);
        AssessmentEngine engine = new AssessmentEngine();
        RuleRegistry registry = DefaultRules.registry();

        Profile fips1403 = DefaultProfiles.loadFips1403();
        Profile fips1402 = DefaultProfiles.loadFips1402Legacy();

        AssessmentReport r1403 = engine.assess(ctx, fips1403, registry);
        AssessmentReport r1402 = engine.assess(ctx, fips1402, registry);

        assertThat(findByRuleId(r1403, "ALGORITHM.SHA1_SIGNATURE").status())
                .isEqualTo(AssessmentStatus.FAIL);
        assertThat(findByRuleId(r1402, "ALGORITHM.SHA1_SIGNATURE").status())
                .isEqualTo(AssessmentStatus.WARNING);
    }

    @Test
    @DisplayName("every report carries the canonical disclaimer")
    void disclaimerAlwaysPresent() throws IOException {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
        RuleContext ctx = contextFor(cert);
        AssessmentEngine engine = new AssessmentEngine();
        RuleRegistry registry = DefaultRules.registry();

        for (Profile p : List.of(DefaultProfiles.loadFips1403(), DefaultProfiles.loadFips1402Legacy())) {
            AssessmentReport r = engine.assess(ctx, p, registry);
            assertThat(r.disclaimer())
                    .contains("static compatibility assessment")
                    .contains("NOT a formal certification");
        }
    }

    @Test
    @DisplayName("summary counts match findings")
    void summaryCountsMatch() throws IOException {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
        RuleContext ctx = contextFor(cert);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport r = engine.assess(ctx, DefaultProfiles.loadFips1403(), DefaultRules.registry());

        long fromFindings = r.findings().stream()
                .filter(f -> f.status() == AssessmentStatus.PASS)
                .count();
        long fromSummary = r.summary().get(AssessmentStatus.PASS);
        assertThat(fromSummary).isEqualTo(fromFindings);
    }
}