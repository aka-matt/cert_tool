package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentReport;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.domain.assessment.Severity;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.reporting.ReportEnvelope;
import io.github.certtool.reporting.ReportSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ExportReportTask")
class ExportReportTaskTest {

    private static ReportEnvelope sample() throws Exception {
        Profile p = DefaultProfiles.loadFips1403();
        AssessmentFinding f = new AssessmentFinding("X-1", "Title",
                AssessmentStatus.PASS, Severity.INFO,
                "summary", "evidence", "remediation", List.of());
        AssessmentReport r = new AssessmentReport(p, Instant.parse("2026-07-14T00:00:00Z"),
                List.of(f));
        return new ReportEnvelope.AssessmentReportEnvelope("Export Test", "src.jks", r);
    }

    @Test
    @DisplayName("call() writes a JSON file atomically with the schema version embedded")
    void writesJson(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.json");
        Path result = new ExportReportTask(sample(), target, ExportReportTask.Format.JSON).call();
        assertThat(result).isEqualTo(target);
        String text = Files.readString(target);
        assertThat(text).contains("\"schemaVersion\" : \"" + ReportSchema.REPORT_SCHEMA_VERSION + "\"");
        assertThat(text).contains("\"toolName\" : \"" + ReportSchema.TOOL_NAME + "\"");
    }

    @Test
    @DisplayName("call() writes an HTML file with embedded CSS")
    void writesHtml(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.html");
        new ExportReportTask(sample(), target, ExportReportTask.Format.HTML).call();
        String text = Files.readString(target);
        assertThat(text).contains("<!DOCTYPE html>");
        assertThat(text).contains("<style>");
    }

    @Test
    @DisplayName("call() writes a Markdown file with schema comment")
    void writesMarkdown(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.md");
        new ExportReportTask(sample(), target, ExportReportTask.Format.MARKDOWN).call();
        String text = Files.readString(target);
        assertThat(text).startsWith("<!-- schema: " + ReportSchema.REPORT_SCHEMA_VERSION + " -->");
        assertThat(text).contains("# Export Test");
    }

    @Test
    @DisplayName("defaultFileName builds a schema-versioned filename")
    void defaultFileName() {
        assertThat(ExportReportTask.defaultFileName("Test", ExportReportTask.Format.JSON))
                .isEqualTo("test.report." + ReportSchema.REPORT_SCHEMA_VERSION + ".json");
        assertThat(ExportReportTask.defaultFileName("My Title!", ExportReportTask.Format.HTML))
                .isEqualTo("my_title_.report." + ReportSchema.REPORT_SCHEMA_VERSION + ".html");
        assertThat(ExportReportTask.defaultFileName(null, ExportReportTask.Format.MARKDOWN))
                .isEqualTo("report.report." + ReportSchema.REPORT_SCHEMA_VERSION + ".md");
    }

    @Test
    @DisplayName("call() overwrites an existing target via atomic replace")
    void overwritesExisting(@TempDir Path tmp) throws Exception {
        Path target = tmp.resolve("out.json");
        Files.writeString(target, "old contents");
        new ExportReportTask(sample(), target, ExportReportTask.Format.JSON).call();
        String text = Files.readString(target);
        assertThat(text).doesNotContain("old contents");
        assertThat(text).contains("\"schemaVersion\"");
    }
}