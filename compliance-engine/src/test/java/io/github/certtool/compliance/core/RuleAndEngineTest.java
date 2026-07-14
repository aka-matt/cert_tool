package io.github.certtool.compliance.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.rules.RuleCategory;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
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

@DisplayName("Rule and AssessmentEngine")
class RuleAndEngineTest {

    private static Profile profile() {
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
                "BCFIPS",
                true,
                List.of("https://csrc.nist.gov/projects/fips-140-3"));
    }

    private static RuleContext emptyContext() {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY, "/tmp/x.bcfks", 0L, true, List.of());
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium",
                "17.0.11",
                "Linux",
                "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "FIPS", true, true)),
                Instant.parse("2026-07-14T00:00:00Z"));
        return new RuleContext(ks, List.of(), runtime);
    }

    private static RuleContext jksWithPrivateKeyContext() {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY, "/tmp/x.jks", 1024L, true, List.of("a"));
        RuntimeEnvironment runtime = new RuntimeEnvironment(
                "Eclipse Adoptium",
                "17.0.11",
                "Linux",
                "amd64",
                List.of(new ProviderInfo("SUN", "17", "SUN", false, false)),
                Instant.parse("2026-07-14T00:00:00Z"));
        EntryAnalysis entry = new EntryAnalysis("a", EntryType.PRIVATE_KEY, null, null, null);
        return new RuleContext(ks, List.of(entry), runtime);
    }

    @Test
    @DisplayName("AssessmentEngine emits a finding for each registered rule")
    void engineEmitsAllFindings() {
        Rule passRule = new Rule() {
            @Override
            public String id() {
                return "R-PASS";
            }

            @Override
            public String title() {
                return "Always passes";
            }

            @Override
            public RuleCategory category() {
                return RuleCategory.CONTAINER;
            }

            @Override
            public AssessmentFinding evaluate(RuleContext ctx, Profile p) {
                return new AssessmentFinding(
                        id(),
                        title(),
                        AssessmentStatus.PASS,
                        Severity.INFO,
                        "All good",
                        "no evidence",
                        "no remediation",
                        List.of());
            }
        };

        RuleRegistry registry = RuleRegistry.of(List.of(passRule));
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(emptyContext(), profile(), registry);

        assertThat(report.findings()).hasSize(1);
        assertThat(report.findings().get(0).ruleId()).isEqualTo("R-PASS");
        assertThat(report.findings().get(0).status()).isEqualTo(AssessmentStatus.PASS);
    }

    @Test
    @DisplayName("every AssessmentReport carries the canonical disclaimer")
    void reportCarriesDisclaimer() {
        RuleRegistry registry = RuleRegistry.of(List.of());
        AssessmentEngine engine = new AssessmentEngine();
        AssessmentReport report = engine.assess(jksWithPrivateKeyContext(), profile(), registry);

        assertThat(report.disclaimer())
                .contains("static compatibility assessment")
                .contains("NOT a formal certification")
                .contains("NIST");
    }

    @Test
    @DisplayName("RuleRegistry filters by category")
    void registryFilters() {
        Rule a = makeRule("R-A", RuleCategory.CONTAINER);
        Rule b = makeRule("R-B", RuleCategory.ALGORITHM);
        Rule c = makeRule("R-C", RuleCategory.ALGORITHM);

        RuleRegistry reg = RuleRegistry.of(List.of(a, b, c));
        assertThat(reg.rules()).hasSize(3);
        assertThat(reg.inCategory(RuleCategory.ALGORITHM)).extracting(Rule::id).containsExactly("R-B", "R-C");
        assertThat(reg.inCategory(RuleCategory.CERTIFICATE)).isEmpty();
    }

    private static Rule makeRule(String id, RuleCategory category) {
        return new Rule() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String title() {
                return id;
            }

            @Override
            public RuleCategory category() {
                return category;
            }

            @Override
            public AssessmentFinding evaluate(RuleContext ctx, Profile p) {
                return new AssessmentFinding(
                        id, title(), AssessmentStatus.PASS, Severity.INFO, "", "", "", List.of());
            }
        };
    }
}