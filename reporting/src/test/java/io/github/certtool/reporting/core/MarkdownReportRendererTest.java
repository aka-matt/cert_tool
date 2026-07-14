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

@DisplayName("MarkdownReportRenderer")
class MarkdownReportRendererTest {

    private final MarkdownReportRenderer renderer = new MarkdownReportRenderer();

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
    @DisplayName("starts with an HTML comment carrying the schema version")
    void schemaVersionInComment() throws Exception {
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(md).startsWith("<!-- schema: " + ReportSchema.REPORT_SCHEMA_VERSION + " -->");
    }

    @Test
    @DisplayName("renders the title as an H1 heading and the source/tool metadata")
    void rendersMetadata() throws Exception {
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(md).contains("# Test");
        assertThat(md).contains("- **Source:** `source.jks`");
        assertThat(md).contains("- **Generated:** `2026-07-14T00:00:00Z`");
        assertThat(md).contains("- **Tool:** `Cert Tool 0.1.0`");
        assertThat(md).contains("- **Schema:** `1.0.0`");
    }

    @Test
    @DisplayName("renders the disclaimer verbatim")
    void rendersDisclaimer() throws Exception {
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(md).contains("## Disclaimer");
        assertThat(md).contains("NOT a formal certification conclusion");
    }

    @Test
    @DisplayName("renders summary as a per-status list")
    void rendersSummary() throws Exception {
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(md).contains("## Summary");
        assertThat(md).contains("- PASS: 1");
        assertThat(md).contains("- FAIL: 1");
    }

    @Test
    @DisplayName("renders findings as a Markdown table")
    void rendersFindingsTable() throws Exception {
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(md).contains("## Findings");
        assertThat(md).contains("| Rule | Title | Status | Severity | Summary | Evidence | Remediation | References |");
        assertThat(md).contains("| --- ");
        assertThat(md).contains("| X-1 | Title 1 | PASS | INFO | summary 1 | evidence 1 | remediation 1 |  |");
        assertThat(md).contains("| X-2 | Title 2 | FAIL | CRITICAL | summary 2 | evidence 2 | remediation 2 | ref-1 |");
    }

    @Test
    @DisplayName("escapes pipe characters in table cells so layout does not break")
    void escapesPipes() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X", "Title|with|pipes",
                AssessmentStatus.WARNING, Severity.LOW,
                "summary | with | pipes", "evidence", "remediation", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s", report);

        String md = new String(renderer.render(env), StandardCharsets.UTF_8);
        // The pipes inside cell text are escaped so they do not appear as raw pipes.
        // SanitizationGuard will reject unescaped "password=" etc.; escaped pipes are fine.
        assertThat(md).contains("Title\\|with\\|pipes");
        assertThat(md).contains("summary \\| with \\| pipes");
    }

    @Test
    @DisplayName("collapses newlines in table cells to spaces")
    void collapsesNewlines() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X", "Title",
                AssessmentStatus.WARNING, Severity.LOW,
                "line1\nline2", "evidence", "remediation", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s", report);

        String md = new String(renderer.render(env), StandardCharsets.UTF_8);
        // Newlines inside cell become spaces — but cell-level newlines still come from our
        // row terminator. Verify the escaped summary collapses.
        assertThat(md).contains("line1 line2");
    }

    @Test
    @DisplayName("never leaks a password-shaped substring into Markdown output")
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
        String md = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        for (String forbidden : List.of(
                "FIPS Certification",
                "Official FIPS Validation",
                "NIST Certified",
                "正式认证结论")) {
            assertThat(md).doesNotContain(forbidden);
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