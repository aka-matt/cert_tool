package io.github.certtool.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ReportSchema")
class ReportSchemaTest {

    @Test
    @DisplayName("exposes a non-blank semver REPORT_SCHEMA_VERSION")
    void exposesSchemaVersion() {
        assertThat(ReportSchema.REPORT_SCHEMA_VERSION).isNotBlank();
        assertThat(ReportSchema.REPORT_SCHEMA_VERSION).matches("\\d+\\.\\d+\\.\\d+");
    }

    @Test
    @DisplayName("exposes a non-blank tool name")
    void exposesToolName() {
        assertThat(ReportSchema.TOOL_NAME).isNotBlank();
        assertThat(ReportSchema.TOOL_NAME).isEqualTo("Cert Tool");
    }

    @Test
    @DisplayName("exposes a non-blank tool version")
    void exposesToolVersion() {
        assertThat(ReportSchema.TOOL_VERSION).isNotBlank();
    }
}