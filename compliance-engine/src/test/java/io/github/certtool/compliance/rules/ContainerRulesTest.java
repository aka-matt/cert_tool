package io.github.certtool.compliance.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.core.AssessmentEngine;
import io.github.certtool.compliance.core.RuleRegistry;
import io.github.certtool.compliance.rules.container.ContainerRules;
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

@DisplayName("ContainerRules")
class ContainerRulesTest {

    private static Profile profile(JksPrivateKeyPolicy policy) {
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
                policy,
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

    private static RuleContext jksContext(boolean hasPrivateKey, boolean integrityOk) {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS,
                ContentEncoding.BINARY,
                "/tmp/x.jks",
                1024L,
                integrityOk,
                List.of("a"));
        EntryAnalysis entry =
                new EntryAnalysis("a", hasPrivateKey ? EntryType.PRIVATE_KEY : EntryType.TRUSTED_CERTIFICATE, null, null, null);
        return new RuleContext(ks, List.of(entry), runtime());
    }

    private static RuleContext bcfksContext(boolean integrityOk) {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.BCFKS,
                ContentEncoding.BINARY,
                "/tmp/x.bcfks",
                1024L,
                integrityOk,
                List.of("a"));
        EntryAnalysis entry = new EntryAnalysis("a", EntryType.TRUSTED_CERTIFICATE, null, null, null);
        return new RuleContext(ks, List.of(entry), runtime());
    }

    @Test
    @DisplayName("JksPrivateKeyRule: PASS when no private keys in JKS")
    void jksNoPrivateKey() {
        RuleContext ctx = jksContext(false, true);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.FAIL), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_PRIVATE_KEY").status())
                .isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("JksPrivateKeyRule: FAIL when JKS holds private keys and policy=FAIL")
    void jksPrivateKeyFails() {
        RuleContext ctx = jksContext(true, true);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.FAIL), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_PRIVATE_KEY").status())
                .isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("JksPrivateKeyRule: WARNING when JKS holds private keys and policy=WARNING")
    void jksPrivateKeyWarns() {
        RuleContext ctx = jksContext(true, true);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.WARNING), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_PRIVATE_KEY").status())
                .isEqualTo(AssessmentStatus.WARNING);
    }

    @Test
    @DisplayName("JksIntegrityRule: FAIL when integrity check did not pass")
    void jksIntegrityFail() {
        RuleContext ctx = jksContext(false, false);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.FAIL), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_INTEGRITY").status())
                .isEqualTo(AssessmentStatus.FAIL);
    }

    @Test
    @DisplayName("JksIntegrityRule: PASS when integrity check passed")
    void jksIntegrityPass() {
        RuleContext ctx = jksContext(false, true);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.FAIL), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_INTEGRITY").status())
                .isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("JksIntegrityRule: NOT_APPLICABLE on BCFKS")
    void jksIntegrityNotApplicableOnBcfks() {
        RuleContext ctx = bcfksContext(false);
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(ctx, profile(JksPrivateKeyPolicy.FAIL), RuleRegistry.of(ContainerRules.all()));
        assertThat(findByRuleId(report, "CONTAINER.JKS_INTEGRITY").status())
                .isEqualTo(AssessmentStatus.NOT_APPLICABLE);
    }

    private static io.github.certtool.domain.assessment.AssessmentFinding findByRuleId(
            AssessmentReport report, String ruleId) {
        return report.findings().stream()
                .filter(f -> f.ruleId().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No finding for " + ruleId));
    }
}