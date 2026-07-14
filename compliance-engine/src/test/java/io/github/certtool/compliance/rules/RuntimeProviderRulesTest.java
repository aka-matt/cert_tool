package io.github.certtool.compliance.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.compliance.rules.runtime.RuntimeProviderRules;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuntimeProviderRules")
class RuntimeProviderRulesTest {

    private static Profile profile(String requiredProvider, boolean approvedOnlyRequired) {
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
                ExpirationPolicy.FAIL,
                UnknownAlgorithmPolicy.NOT_ASSESSABLE,
                JksPrivateKeyPolicy.FAIL,
                requiredProvider,
                approvedOnlyRequired,
                List.of());
    }

    private static RuleContext context(RuntimeEnvironment runtime) {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY, "/tmp/x.bcfks", 0L, true, List.of());
        return new RuleContext(ks, List.<EntryAnalysis>of(), runtime);
    }

    private static AssessmentFinding find(AssessmentReport report, String ruleId) {
        return report.findings().stream()
                .filter(f -> f.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding for " + ruleId));
    }

    @Test
    @DisplayName("RequiredProviderRule: PASS when BCFIPS present and profile requires BCFIPS")
    void requiredProviderPresent() {
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium", "17.0.11", "Linux", "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "BouncyCastle FIPS Provider", false, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
        AssessmentReport report = new AssessmentEngine()
                .assess(context(runtime), profile("BCFIPS", false), RuleRegistry.of(RuntimeProviderRules.all()));
        assertThat(find(report, "RUNTIME.REQUIRED_PROVIDER").status()).isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("RequiredProviderRule: FAIL when BCFIPS absent and profile requires BCFIPS")
    void requiredProviderAbsent() {
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium", "17.0.11", "Linux", "amd64",
                List.of(new ProviderInfo("SUN", "17", "SUN JCE", false, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
        AssessmentReport report = new AssessmentEngine()
                .assess(context(runtime), profile("BCFIPS", false), RuleRegistry.of(RuntimeProviderRules.all()));
        assertThat(find(report, "RUNTIME.REQUIRED_PROVIDER").status()).isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("ApprovedOnlyModeRule: PASS when provider confirms Approved-Only Mode")
    void approvedOnlyConfirmed() {
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium", "17.0.11", "Linux", "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "BouncyCastle FIPS Provider", true, true)),
                Instant.parse("2026-07-14T00:00:00Z"));
        AssessmentReport report = new AssessmentEngine()
                .assess(context(runtime), profile("BCFIPS", true), RuleRegistry.of(RuntimeProviderRules.all()));
        assertThat(find(report, "RUNTIME.APPROVED_ONLY_MODE").status()).isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("ApprovedOnlyModeRule: NOT_ASSESSABLE when Approved-Only detection failed")
    void approvedOnlyUnknown() {
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium", "17.0.11", "Linux", "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "BouncyCastle FIPS Provider", true, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
        AssessmentReport report = new AssessmentEngine()
                .assess(context(runtime), profile("BCFIPS", true), RuleRegistry.of(RuntimeProviderRules.all()));
        assertThat(find(report, "RUNTIME.APPROVED_ONLY_MODE").status())
                .isEqualTo(AssessmentStatus.NOT_ASSESSABLE);
    }

    @Test
    @DisplayName("NonFipsBouncyCastleRule: WARNING when non-FIPS Bouncy Castle is also installed")
    void nonFipsBcPresent() {
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium", "17.0.11", "Linux", "amd64",
                List.of(
                        new ProviderInfo("BCFIPS", "2.0.0", "BouncyCastle FIPS Provider", true, true),
                        new ProviderInfo("BC", "1.78", "BouncyCastle Security Provider", false, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
        AssessmentReport report = new AssessmentEngine()
                .assess(context(runtime), profile("BCFIPS", true), RuleRegistry.of(RuntimeProviderRules.all()));
        assertThat(find(report, "RUNTIME.NON_FIPS_BOUNCY_CASTLE").status()).isEqualTo(AssessmentStatus.WARNING);
    }
}