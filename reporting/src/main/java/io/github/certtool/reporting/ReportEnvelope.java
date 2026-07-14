package io.github.certtool.reporting;

import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.FipsDisclaimer;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Sealed-style envelope passed to every {@code ReportRenderer}. Wraps the underlying domain object
 * (assessment report or conversion result) with the metadata every renderer needs: a human
 * title, a logical source label, the schema version, the tool name+version, and the disclaimer.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link AssessmentReportEnvelope} — wraps an {@link AssessmentReport} (primary use case).</li>
 *   <li>{@link ConversionReportEnvelope} — wraps a {@link ConversionResult} (wizard step 5).</li>
 * </ul>
 *
 * <p>Renderers MUST NOT access passwords or key material through these envelopes. The
 * {@link #findings()} method returns an empty list for conversion reports because the conversion
 * pipeline owns its own preflight shape; the {@link ConversionReportEnvelope} exposes preflight
 * codes via a dedicated accessor.
 */
public sealed interface ReportEnvelope
        permits ReportEnvelope.AssessmentReportEnvelope, ReportEnvelope.ConversionReportEnvelope {

    String schemaVersion();

    String title();

    String sourceLabel();

    Instant generatedAt();

    String toolName();

    String toolVersion();

    String disclaimer();

    /** Findings for the assessment use case; empty for conversion use case. */
    List<AssessmentFinding> findings();

    /** Assessment report envelope — wraps {@link AssessmentReport}. */
    record AssessmentReportEnvelope(
            String title,
            String sourceLabel,
            AssessmentReport report) implements ReportEnvelope {

        public AssessmentReportEnvelope {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(report, "report");
        }

        @Override
        public String schemaVersion() {
            return ReportSchema.REPORT_SCHEMA_VERSION;
        }

        @Override
        public Instant generatedAt() {
            return report.generatedAt();
        }

        @Override
        public String toolName() {
            return ReportSchema.TOOL_NAME;
        }

        @Override
        public String toolVersion() {
            return ReportSchema.TOOL_VERSION;
        }

        @Override
        public String disclaimer() {
            return report.disclaimer();
        }

        @Override
        public List<AssessmentFinding> findings() {
            return report.findings();
        }

        /** Counts of findings per status, exposed for summary cards. */
        public java.util.Map<io.github.certtool.domain.assessment.AssessmentStatus, Long> counts() {
            return report.summary();
        }
    }

    /**
     * Conversion report envelope — wraps {@link ConversionResult}. Carries the wizard's
     * preflight codes + reload verification outcome as the reportable shape; conversion reports do
     * not produce AssessmentFindings.
     */
    record ConversionReportEnvelope(
            String title,
            String sourceLabel,
            ConversionResult result) implements ReportEnvelope {

        public ConversionReportEnvelope {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(sourceLabel, "sourceLabel");
            Objects.requireNonNull(result, "result");
        }

        @Override
        public String schemaVersion() {
            return ReportSchema.REPORT_SCHEMA_VERSION;
        }

        @Override
        public Instant generatedAt() {
            // ConversionResult does not carry its own timestamp — derive from "now" at envelope
            // construction time so each render reflects the call moment. Acceptable per spec
            // because the wizard calls this on user-driven export, never in a hot loop.
            return Instant.now();
        }

        @Override
        public String toolName() {
            return ReportSchema.TOOL_NAME;
        }

        @Override
        public String toolVersion() {
            return ReportSchema.TOOL_VERSION;
        }

        @Override
        public String disclaimer() {
            return FipsDisclaimer.text();
        }

        @Override
        public List<AssessmentFinding> findings() {
            return List.of();
        }

        /** Preflight codes (strings) so renderers can show them without depending on the module. */
        public List<String> preflightCodes() {
            return result.preflightReport().findings().stream()
                    .map(f -> f.code())
                    .toList();
        }

        public boolean reloadVerified() {
            return result.verification().matchesSource();
        }

        public String targetPath() {
            return result.targetPath();
        }

        public long writtenBytes() {
            return result.writtenBytes();
        }
    }
}