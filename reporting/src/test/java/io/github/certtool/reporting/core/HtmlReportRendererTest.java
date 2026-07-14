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
import io.github.certtool.reporting.ReportSchema;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HtmlReportRenderer")
class HtmlReportRendererTest {

    private final HtmlReportRenderer renderer = new HtmlReportRenderer();

    private static ReportEnvelope sampleAssessment() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding f1 = new AssessmentFinding("X-1", "Title 1",
                AssessmentStatus.PASS, Severity.INFO,
                "summary 1", "evidence 1", "remediation 1", List.of());
        AssessmentFinding f2 = new AssessmentFinding("X-2", "Title 2",
                AssessmentStatus.FAIL, Severity.CRITICAL,
                "summary 2", "evidence 2", "remediation 2", List.of("ref-1"));
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(f1, f2));
        return new ReportEnvelope.AssessmentReportEnvelope("Test", "source.jks", report);
    }

    @Test
    @DisplayName("output is a self-contained HTML document with embedded CSS (offline-safe)")
    void selfContainedHtml() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).startsWith("<!DOCTYPE html>");
        assertThat(html).contains("<style>");
        assertThat(html).contains("</style>");
        assertThat(html).contains("</html>");
        // No external stylesheets / scripts.
        assertThat(html).doesNotContain("rel=\"stylesheet\"");
        assertThat(html).doesNotContain("<script");
        // No external URLs (http/https anywhere).
        assertThat(html).doesNotContain("http://");
        assertThat(html).doesNotContain("https://");
    }

    @Test
    @DisplayName("embeds the schema version in a <meta> tag")
    void schemaVersionInMeta() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).contains("<meta name=\"report-schema\" content=\""
                + ReportSchema.REPORT_SCHEMA_VERSION + "\">");
    }

    @Test
    @DisplayName("includes the title, source label, tool name, generated timestamp")
    void includesMetadata() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).contains("<title>Test</title>");
        assertThat(html).contains("source.jks");
        assertThat(html).contains("Cert Tool");
        assertThat(html).contains("0.1.0");
        assertThat(html).contains("2026-07-14T00:00:00Z");
    }

    @Test
    @DisplayName("includes the disclaimer verbatim")
    void includesDisclaimer() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).contains("NOT a formal certification conclusion");
    }

    @Test
    @DisplayName("renders findings as a table with all eight fields")
    void rendersFindingsTable() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).contains("<table");
        assertThat(html).contains("Rule");
        assertThat(html).contains("Status");
        assertThat(html).contains("Severity");
        assertThat(html).contains("Summary");
        assertThat(html).contains("Evidence");
        assertThat(html).contains("Remediation");
        assertThat(html).contains("X-1");
        assertThat(html).contains("X-2");
        assertThat(html).contains("Title 1");
        assertThat(html).contains("ref-1");
    }

    @Test
    @DisplayName("emits the per-status summary cards")
    void emitsSummaryCards() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(html).contains("PASS");
        assertThat(html).contains("FAIL");
        assertThat(html).contains("WARNING");
        assertThat(html).contains("NOT_ASSESSABLE");
    }

    @Test
    @DisplayName("escapes HTML metacharacters in user-controlled strings")
    void escapesUserControlledStrings() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X-EVIL", "<script>alert(1)</script>",
                AssessmentStatus.WARNING, Severity.LOW,
                "sum & < > \" ' ", "ev & < > \" ' ", "rem & < > \" ' ", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s", report);

        String html = new String(renderer.render(env), StandardCharsets.UTF_8);
        // The raw script tag MUST NOT survive.
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        // Escaped form MUST appear.
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
        // Ampersand must be escaped.
        assertThat(html).contains("sum &amp; &lt; &gt; &quot; &#39;");
    }

    @Test
    @DisplayName("never leaks a password-shaped substring into HTML output")
    void doesNotLeakPasswords() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X", "T",
                AssessmentStatus.WARNING, Severity.LOW,
                "summary", "evidence with password=hunter2", "r", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s", report);

        assertThatThrownBy(() -> renderer.render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("never emits forbidden certification-claim phrases")
    void doesNotEmitCertificationClaims() throws Exception {
        String html = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        for (String forbidden : List.of(
                "FIPS Certification",
                "Official FIPS Validation",
                "NIST Certified",
                "正式认证结论")) {
            assertThat(html).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("output is deterministic for identical inputs")
    void deterministic() throws Exception {
        byte[] a = renderer.render(sampleAssessment());
        byte[] b = renderer.render(sampleAssessment());
        assertThat(a).isEqualTo(b);
    }
}