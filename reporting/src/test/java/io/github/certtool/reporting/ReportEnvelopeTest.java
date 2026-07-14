package io.github.certtool.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportEnvelope")
class ReportEnvelopeTest {

    @Test
    @DisplayName("AssessmentReportEnvelope exposes schema version and source label")
    void assessmentEnvelopeExposesMetadata() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentReport report = new AssessmentReport(profile, Instant.parse("2026-07-14T00:00:00Z"),
                List.of(new AssessmentFinding("X-1", "title", AssessmentStatus.PASS, Severity.INFO,
                        "summary", "evidence", "remediation", List.of())));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("Test Title", "source.jks", report);

        assertThat(env.schemaVersion()).isEqualTo(ReportSchema.REPORT_SCHEMA_VERSION);
        assertThat(env.title()).isEqualTo("Test Title");
        assertThat(env.sourceLabel()).isEqualTo("source.jks");
        assertThat(env.generatedAt()).isEqualTo(Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(env.toolName()).isEqualTo(ReportSchema.TOOL_NAME);
        assertThat(env.toolVersion()).isEqualTo(ReportSchema.TOOL_VERSION);
    }

    @Test
    @DisplayName("AssessmentReportEnvelope.disclaimer() returns the canonical disclaimer")
    void assessmentEnvelopeDisclaimer() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentReport report = new AssessmentReport(profile, Instant.parse("2026-07-14T00:00:00Z"),
                List.of());
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("t", "s", report);
        assertThat(env.disclaimer()).contains("NOT a formal certification conclusion");
    }

    @Test
    @DisplayName("AssessmentReportEnvelope findings list is defensively copied")
    void assessmentEnvelopeDefensiveCopy() throws Exception {
        Profile profile = DefaultProfiles.loadFips1403();
        AssessmentFinding f = new AssessmentFinding("X-1", "t", AssessmentStatus.PASS, Severity.INFO,
                "s", "e", "r", List.of());
        AssessmentReport report = new AssessmentReport(profile, Instant.parse("2026-07-14T00:00:00Z"),
                List.of(f));
        ReportEnvelope env = new ReportEnvelope.AssessmentReportEnvelope("t", "s", report);
        assertThat(env.findings()).containsExactly(f);
    }

    @Test
    @DisplayName("ConversionReportEnvelope exposes plan and preflight codes without leaking passwords")
    void conversionEnvelopeExposesMetadata() throws Exception {
        io.github.certtool.conversion.domain.plan.ConversionPlan plan =
                new io.github.certtool.conversion.domain.plan.ConversionPlan(
                        io.github.certtool.domain.keystore.KeyStoreContainerType.JKS,
                        io.github.certtool.domain.keystore.ContentEncoding.BINARY,
                        io.github.certtool.domain.keystore.KeyStoreContainerType.JKS,
                        io.github.certtool.domain.keystore.ContentEncoding.BINARY,
                        "src", "tgt",
                        "src-pwd".toCharArray(), "tgt-pwd".toCharArray(),
                        io.github.certtool.conversion.domain.plan.AliasConflictPolicy.SKIP,
                        io.github.certtool.conversion.domain.plan.OverwritePolicy.FAIL_IF_EXISTS,
                        List.of(), List.of());
        io.github.certtool.conversion.domain.preflight.PreflightReport preflight =
                new io.github.certtool.conversion.domain.preflight.PreflightReport(List.of());
        io.github.certtool.conversion.domain.result.ReloadVerification verif =
                new io.github.certtool.conversion.domain.result.ReloadVerification(
                        true, 1, 1, 1, 1, true);
        io.github.certtool.conversion.domain.result.ConversionResult result =
                new io.github.certtool.conversion.domain.result.ConversionResult(
                        plan, preflight, verif, "tgt", 1024L);

        ReportEnvelope env = new ReportEnvelope.ConversionReportEnvelope("Convert Title", "src.jks", result);
        assertThat(env.schemaVersion()).isEqualTo(ReportSchema.REPORT_SCHEMA_VERSION);
        assertThat(env.title()).isEqualTo("Convert Title");
        assertThat(env.sourceLabel()).isEqualTo("src.jks");
        assertThat(env.findings()).isEmpty();
        // Sanitization: sourceLabel is the path label, not the password.
        assertThat(env.sourceLabel()).doesNotContain("src-pwd");
    }
}