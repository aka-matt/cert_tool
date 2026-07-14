package io.github.certtool.domain.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.domain.profile.JksPrivateKeyPolicy;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.ProfileId;
import io.github.certtool.domain.profile.Sha1Policy;
import io.github.certtool.domain.profile.Standard;
import io.github.certtool.domain.profile.ExpirationPolicy;
import io.github.certtool.domain.profile.UnknownAlgorithmPolicy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AssessmentReport")
class AssessmentReportTest {

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

    private static AssessmentFinding finding(AssessmentStatus status, Severity severity) {
        return new AssessmentFinding(
                "R-TEST",
                "Title",
                status,
                severity,
                "summary",
                "evidence",
                "remediation",
                List.of());
    }

    @Test
    @DisplayName("constructs with profile, generatedAt, findings")
    void constructs() {
        AssessmentReport r = new AssessmentReport(profile(), Instant.parse("2026-07-14T00:00:00Z"), List.of());
        assertThat(r.profile().id()).isEqualTo(ProfileId.FIPS_140_3_ASSESSMENT);
        assertThat(r.findings()).isEmpty();
    }

    @Test
    @DisplayName("summary counts findings per status")
    void summaryCounts() {
        AssessmentReport r = new AssessmentReport(
                profile(),
                Instant.parse("2026-07-14T00:00:00Z"),
                List.of(
                        finding(AssessmentStatus.PASS, Severity.INFO),
                        finding(AssessmentStatus.PASS, Severity.INFO),
                        finding(AssessmentStatus.FAIL, Severity.HIGH),
                        finding(AssessmentStatus.WARNING, Severity.MEDIUM),
                        finding(AssessmentStatus.NOT_ASSESSABLE, Severity.INFO)));
        Map<AssessmentStatus, Long> s = r.summary();
        assertThat(s.get(AssessmentStatus.PASS)).isEqualTo(2);
        assertThat(s.get(AssessmentStatus.FAIL)).isEqualTo(1);
        assertThat(s.get(AssessmentStatus.WARNING)).isEqualTo(1);
        assertThat(s.get(AssessmentStatus.NOT_ASSESSABLE)).isEqualTo(1);
        assertThat(s.get(AssessmentStatus.NOT_APPLICABLE)).isEqualTo(0);
    }

    @Test
    @DisplayName("rejects null profile")
    void rejectsNullProfile() {
        assertThatThrownBy(() -> new AssessmentReport(
                null, Instant.parse("2026-07-14T00:00:00Z"), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("profile");
    }
}