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
import io.github.certtool.reporting.SanitizationViolation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JsonReportRenderer")
class JsonReportRendererTest {

    private final JsonReportRenderer renderer = new JsonReportRenderer();

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
    @DisplayName("renders a JSON document with the schema version at the top level")
    void schemaVersionAtTopLevel() throws Exception {
        byte[] bytes = renderer.render(sampleAssessment());
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).contains("\"schemaVersion\" : \"" + ReportSchema.REPORT_SCHEMA_VERSION + "\"");
    }

    @Test
    @DisplayName("includes the tool name, tool version, title, source label, generatedAt")
    void includesToolMetadata() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(json).contains("\"toolName\" : \"" + ReportSchema.TOOL_NAME + "\"");
        assertThat(json).contains("\"toolVersion\" : \"" + ReportSchema.TOOL_VERSION + "\"");
        assertThat(json).contains("\"title\" : \"Test\"");
        assertThat(json).contains("\"sourceLabel\" : \"source.jks\"");
        assertThat(json).contains("\"generatedAt\" : \"2026-07-14T00:00:00Z\"");
    }

    @Test
    @DisplayName("includes the disclaimer text from the FipsDisclaimer")
    void includesDisclaimer() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(json).contains("disclaimer");
        assertThat(json).contains("NOT a formal certification conclusion");
    }

    @Test
    @DisplayName("emits findings with all eight AssessmentFinding fields")
    void emitsFindingsWithAllFields() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(json).contains("\"ruleId\" : \"X-1\"");
        assertThat(json).contains("\"title\" : \"Title 1\"");
        assertThat(json).contains("\"status\" : \"PASS\"");
        assertThat(json).contains("\"severity\" : \"INFO\"");
        assertThat(json).contains("\"summary\" : \"summary 1\"");
        assertThat(json).contains("\"evidence\" : \"evidence 1\"");
        assertThat(json).contains("\"remediation\" : \"remediation 1\"");
        assertThat(json).contains("\"references\" : [ ]");
        assertThat(json).contains("\"ruleId\" : \"X-2\"");
        assertThat(json).contains("\"status\" : \"FAIL\"");
        assertThat(json).contains("\"severity\" : \"CRITICAL\"");
        assertThat(json).contains("\"ref-1\"");
    }

    @Test
    @DisplayName("emits the per-status counts summary")
    void emitsCountsSummary() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        assertThat(json).contains("\"counts\"");
        assertThat(json).contains("\"PASS\" : 1");
        assertThat(json).contains("\"FAIL\" : 1");
    }

    @Test
    @DisplayName("output is deterministic — two renders produce byte-identical bytes")
    void deterministicOutput() throws Exception {
        byte[] a = renderer.render(sampleAssessment());
        byte[] b = renderer.render(sampleAssessment());
        assertThat(a).isEqualTo(b);
    }

    @Test
    @DisplayName("never leaks a password-shaped substring into JSON output")
    void doesNotLeakPasswords() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X-PWN", "Title",
                AssessmentStatus.WARNING, Severity.LOW,
                "summary containing password=hunter2",
                "evidence: Password: hunter2",
                "remediation", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s.jks", report);

        assertThatThrownBy(() -> renderer.render(env))
                .isInstanceOf(io.github.certtool.reporting.RenderException.class);
    }

    @Test
    @DisplayName("never emits forbidden certification-claim phrases")
    void doesNotEmitCertificationClaims() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        for (String forbidden : List.of(
                "FIPS Certification",
                "Official FIPS Validation",
                "NIST Certified",
                "正式认证结论")) {
            assertThat(json).doesNotContain(forbidden);
        }
    }

    @Test
    @DisplayName("violation list is exposed on RenderException for diagnostics")
    void renderExceptionExposesViolations() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding poisoned = new AssessmentFinding("X", "T",
                AssessmentStatus.WARNING, Severity.LOW,
                "secret", "secret password=foo", "r", List.of());
        AssessmentReport report = new AssessmentReport(profile,
                Instant.parse("2026-07-14T00:00:00Z"), List.of(poisoned));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("T", "s", report);

        try {
            renderer.render(env);
            org.junit.jupiter.api.Assertions.fail("expected RenderException");
        } catch (io.github.certtool.reporting.RenderException e) {
            List<SanitizationViolation> v = e.violations();
            assertThat(v).isNotEmpty();
            assertThat(v.get(0).match()).contains("password=foo");
        }
    }

    @Test
    @DisplayName("alphabetical property ordering is enabled — keys are sorted")
    void alphabeticalPropertyOrdering() throws Exception {
        String json = new String(renderer.render(sampleAssessment()), StandardCharsets.UTF_8);
        // Disambiguate by value — root-level keys carry the metadata values (Test, source.jks,
        // 2026-07-14T00:00:00Z, 1.0.0) so substring searches find the root instance, not the
        // identical key inside findings[].
        int counts = json.indexOf("\"counts\"");
        int disclaimer = json.indexOf("\"disclaimer\"");
        int findings = json.indexOf("\"findings\"");
        int generatedAt = json.indexOf("\"generatedAt\" : \"2026-07-14");
        int schema = json.indexOf("\"schemaVersion\" : \"1.0.0\"");
        int sourceLabel = json.indexOf("\"sourceLabel\" : \"source.jks\"");
        int title = json.indexOf("\"title\" : \"Test\"");
        int toolName = json.indexOf("\"toolName\" : \"Cert Tool\"");
        int toolVersion = json.indexOf("\"toolVersion\" : \"0.1.0\"");
        // alphabetical: counts < disclaimer < findings < generatedAt < schemaVersion < sourceLabel < title < toolName < toolVersion
        assertThat(counts).isPositive();
        assertThat(counts).isLessThan(disclaimer);
        assertThat(disclaimer).isLessThan(findings);
        assertThat(findings).isLessThan(generatedAt);
        assertThat(generatedAt).isLessThan(schema);
        assertThat(schema).isLessThan(sourceLabel);
        assertThat(sourceLabel).isLessThan(title);
        assertThat(title).isLessThan(toolName);
        assertThat(toolName).isLessThan(toolVersion);
    }
}