package io.github.certtool.reporting.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.conversion.domain.result.ReloadVerification;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.reporting.ReportEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConversionReportRenderer (envelope coverage)")
class ConversionReportRendererTest {

    private static ReportEnvelope.ConversionReportEnvelope sampleConversion() {
        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "source.jks", "target.bcfks",
                "src-pwd".toCharArray(), "tgt-pwd".toCharArray(),
                AliasConflictPolicy.SKIP, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of());
        PreflightReport preflight = new PreflightReport(List.of(
                new PreflightFinding(PreflightSeverity.INFO, "ALIAS_OVERWRITE_INTERACTION",
                        null, "info message")));
        ReloadVerification verif = new ReloadVerification(
                true, 1, 1, 1, 1, true);
        ConversionResult result = new ConversionResult(plan, preflight, verif,
                "target.bcfks", 2048L);
        return new ReportEnvelope.ConversionReportEnvelope("Convert Test", "source.jks", result);
    }

    @Test
    @DisplayName("JSON renderer includes conversion metadata (target, written, verified, codes)")
    void jsonIncludesConversionMetadata() throws Exception {
        String json = new String(
                new JsonReportRenderer().render(sampleConversion()),
                StandardCharsets.UTF_8);
        assertThat(json).contains("\"conversion\"");
        assertThat(json).contains("\"targetPath\" : \"target.bcfks\"");
        assertThat(json).contains("\"writtenBytes\" : 2048");
        assertThat(json).contains("\"reloadVerified\" : true");
        assertThat(json).contains("\"ALIAS_OVERWRITE_INTERACTION\"");
    }

    @Test
    @DisplayName("HTML renderer includes conversion section with target and reload status")
    void htmlIncludesConversionSection() throws Exception {
        String html = new String(
                new HtmlReportRenderer().render(sampleConversion()),
                StandardCharsets.UTF_8);
        assertThat(html).contains("Conversion Result");
        assertThat(html).contains("target.bcfks");
        assertThat(html).contains("yes"); // reloadVerified
    }

    @Test
    @DisplayName("Markdown renderer includes conversion section")
    void markdownIncludesConversionSection() throws Exception {
        String md = new String(
                new MarkdownReportRenderer().render(sampleConversion()),
                StandardCharsets.UTF_8);
        assertThat(md).contains("## Conversion Result");
        assertThat(md).contains("target.bcfks");
        assertThat(md).contains("yes");
        assertThat(md).contains("ALIAS_OVERWRITE_INTERACTION");
    }

    @Test
    @DisplayName("conversion envelope never leaks the source/target passwords")
    void neverLeaksPasswords() throws Exception {
        ReportEnvelope env = sampleConversion();
        for (ReportRenderer r : List.of(
                new JsonReportRenderer(),
                new HtmlReportRenderer(),
                new MarkdownReportRenderer())) {
            byte[] bytes = r.render(env);
            String text = new String(bytes, StandardCharsets.UTF_8);
            assertThat(text)
                    .as("renderer %s leaked source password", r.getClass().getSimpleName())
                    .doesNotContain("src-pwd");
            assertThat(text)
                    .as("renderer %s leaked target password", r.getClass().getSimpleName())
                    .doesNotContain("tgt-pwd");
        }
    }
}