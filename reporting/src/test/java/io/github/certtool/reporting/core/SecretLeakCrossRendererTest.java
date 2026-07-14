package io.github.certtool.reporting.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.reporting.ReportEnvelope;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cross-renderer guard: feeds each renderer a payload containing known-bad substrings and asserts
 * that no renderer ever returns bytes containing those substrings. Backstop for the per-renderer
 * sanitization tests.
 */
@DisplayName("SecretLeakCrossRenderer")
class SecretLeakCrossRendererTest {

    private static final String SECRET_PASSWORD = "hunter2-supersecret-pw-9f8a";
    private static final String SECRET_KEY_PEM = "-----BEGIN PRIVATE KEY-----\nMIIE...";

    private static ReportEnvelope poisonedEnvelope(String secret) throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding f1 = new AssessmentFinding("X-1", "OK title",
                AssessmentStatus.PASS, Severity.INFO,
                "summary " + secret,
                "evidence " + secret,
                "remediation " + secret,
                List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(f1));
        return new ReportEnvelope.AssessmentReportEnvelope("Leak test", "src.jks", report);
    }

    @Test
    @DisplayName("JSON renderer refuses a poisoned payload (password substring)")
    void jsonRefusesPasswordLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope("password=" + SECRET_PASSWORD);
        assertThatThrownBy(() -> new JsonReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("HTML renderer refuses a poisoned payload (password substring)")
    void htmlRefusesPasswordLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope("password=" + SECRET_PASSWORD);
        assertThatThrownBy(() -> new HtmlReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("Markdown renderer refuses a poisoned payload (password substring)")
    void markdownRefusesPasswordLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope("password=" + SECRET_PASSWORD);
        assertThatThrownBy(() -> new MarkdownReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("JSON renderer refuses a poisoned payload (PEM private-key marker)")
    void jsonRefusesPemMarkerLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope(SECRET_KEY_PEM);
        assertThatThrownBy(() -> new JsonReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("HTML renderer refuses a poisoned payload (PEM private-key marker)")
    void htmlRefusesPemMarkerLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope(SECRET_KEY_PEM);
        assertThatThrownBy(() -> new HtmlReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("Markdown renderer refuses a poisoned payload (PEM private-key marker)")
    void markdownRefusesPemMarkerLeak() throws Exception {
        ReportEnvelope env = poisonedEnvelope(SECRET_KEY_PEM);
        assertThatThrownBy(() -> new MarkdownReportRenderer().render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("clean envelopes render successfully across all three renderers")
    void cleanEnvelopesRenderAcrossAllFormats() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding f = new AssessmentFinding("X-1", "T",
                AssessmentStatus.PASS, Severity.INFO,
                "summary", "evidence", "remediation", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(f));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("Clean", "s", report);

        for (ReportRenderer r : List.of(
                new JsonReportRenderer(),
                new HtmlReportRenderer(),
                new MarkdownReportRenderer())) {
            byte[] bytes = r.render(env);
            assertThat(bytes).isNotEmpty();
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertThat(text).doesNotContain("FIPS Certification");
            assertThat(text).doesNotContain("Official FIPS Validation");
            assertThat(text).doesNotContain("NIST Certified");
            assertThat(text).doesNotContain("正式认证结论");
        }
    }
}