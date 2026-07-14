package io.github.certtool.reporting.core;

import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.reporting.ReportEnvelope;
import io.github.certtool.reporting.ReportSchema;
import io.github.certtool.reporting.SanitizationGuard;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a {@link ReportEnvelope} to a self-contained HTML document.
 *
 * <p>Offline by construction: all CSS lives in an inline {@code <style>} block; no
 * {@code <link href="http...">} or {@code <script src="...">} elements. The output is suitable
 * for direct disk write, email attachment, or browser preview without network access.
 *
 * <p>Every user-controlled string is escaped via {@link HtmlEscaper} before insertion. After
 * assembly the renderer runs {@link SanitizationGuard#enforce} on the result.
 */
public final class HtmlReportRenderer implements ReportRenderer {

    @Override
    public byte[] render(ReportEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");

        StringBuilder sb = new StringBuilder(4096);
        appendHeader(sb, envelope);
        appendSummary(sb, envelope);
        appendFindings(sb, envelope);
        if (envelope instanceof ReportEnvelope.ConversionReportEnvelope c) {
            appendConversionSection(sb, c);
        }
        appendDisclaimer(sb, envelope);
        appendFooter(sb);

        String html = sb.toString();
        SanitizationGuard.enforce(html, envelope.title());
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private static void appendHeader(StringBuilder sb, ReportEnvelope env) {
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\"><head>\n");
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
        sb.append("<meta name=\"report-schema\" content=\"")
                .append(HtmlEscaper.escape(env.schemaVersion())).append("\">\n");
        sb.append("<meta name=\"generator\" content=\"")
                .append(HtmlEscaper.escape(env.toolName())).append(' ')
                .append(HtmlEscaper.escape(env.toolVersion())).append("\">\n");
        sb.append("<title>").append(HtmlEscaper.escape(env.title())).append("</title>\n");
        sb.append("<style>\n").append(EMBEDDED_CSS).append("\n</style>\n");
        sb.append("</head><body>\n");
        sb.append("<header class=\"report-header\">\n");
        sb.append("<h1>").append(HtmlEscaper.escape(env.title())).append("</h1>\n");
        sb.append("<dl class=\"report-meta\">\n");
        sb.append("<dt>Source</dt><dd>").append(HtmlEscaper.escape(env.sourceLabel())).append("</dd>\n");
        sb.append("<dt>Generated</dt><dd>")
                .append(HtmlEscaper.escape(env.generatedAt().toString())).append("</dd>\n");
        sb.append("<dt>Tool</dt><dd>")
                .append(HtmlEscaper.escape(env.toolName())).append(' ')
                .append(HtmlEscaper.escape(env.toolVersion())).append("</dd>\n");
        sb.append("<dt>Schema</dt><dd>")
                .append(HtmlEscaper.escape(env.schemaVersion())).append("</dd>\n");
        sb.append("</dl>\n</header>\n");
    }

    private static void appendSummary(StringBuilder sb, ReportEnvelope env) {
        sb.append("<section class=\"summary\"><h2>Summary</h2>\n");
        sb.append("<ul class=\"counts\">\n");
        Map<AssessmentStatus, Long> counts;
        if (env instanceof ReportEnvelope.AssessmentReportEnvelope a) {
            counts = a.counts();
        } else {
            counts = new java.util.EnumMap<>(AssessmentStatus.class);
            for (AssessmentStatus s : AssessmentStatus.values()) {
                counts.put(s, 0L);
            }
        }
        for (AssessmentStatus s : AssessmentStatus.values()) {
            long n = counts.getOrDefault(s, 0L);
            sb.append("<li class=\"count count-").append(s.name().toLowerCase(Locale.ROOT))
                    .append("\"><span class=\"label\">").append(s.name()).append("</span>")
                    .append("<span class=\"value\">").append(n).append("</span></li>\n");
        }
        sb.append("</ul>\n</section>\n");
    }

    private static void appendFindings(StringBuilder sb, ReportEnvelope env) {
        sb.append("<section class=\"findings\"><h2>Findings</h2>\n");
        if (env.findings().isEmpty()) {
            sb.append("<p class=\"empty\">No findings recorded.</p>\n");
        } else {
            sb.append("<table class=\"findings-table\">\n<thead><tr>");
            for (String col : new String[]{"Rule", "Title", "Status", "Severity",
                    "Summary", "Evidence", "Remediation", "References"}) {
                sb.append("<th>").append(HtmlEscaper.escape(col)).append("</th>");
            }
            sb.append("</tr></thead>\n<tbody>\n");
            for (AssessmentFinding f : env.findings()) {
                sb.append("<tr>");
                sb.append("<td>").append(HtmlEscaper.escape(f.ruleId())).append("</td>");
                sb.append("<td>").append(HtmlEscaper.escape(f.title())).append("</td>");
                sb.append("<td class=\"status-").append(f.status().name().toLowerCase(Locale.ROOT))
                        .append("\">").append(f.status().name()).append("</td>");
                sb.append("<td class=\"sev-").append(f.severity().name().toLowerCase(Locale.ROOT))
                        .append("\">").append(f.severity().name()).append("</td>");
                sb.append("<td>").append(HtmlEscaper.escape(f.summary())).append("</td>");
                sb.append("<td>").append(HtmlEscaper.escape(f.evidence())).append("</td>");
                sb.append("<td>").append(HtmlEscaper.escape(f.remediation())).append("</td>");
                sb.append("<td>").append(HtmlEscaper.escape(String.join(", ", f.references())))
                        .append("</td>");
                sb.append("</tr>\n");
            }
            sb.append("</tbody>\n</table>\n");
        }
        sb.append("</section>\n");
    }

    private static void appendConversionSection(StringBuilder sb, ReportEnvelope.ConversionReportEnvelope c) {
        sb.append("<section class=\"conversion\"><h2>Conversion Result</h2>\n");
        sb.append("<dl class=\"conversion-meta\">\n");
        sb.append("<dt>Target</dt><dd>").append(HtmlEscaper.escape(c.targetPath())).append("</dd>\n");
        sb.append("<dt>Written bytes</dt><dd>").append(c.writtenBytes()).append("</dd>\n");
        sb.append("<dt>Reload verified</dt><dd>").append(c.reloadVerified() ? "yes" : "no").append("</dd>\n");
        sb.append("<dt>Preflight codes</dt><dd>")
                .append(HtmlEscaper.escape(String.join(", ", c.preflightCodes()))).append("</dd>\n");
        sb.append("</dl>\n</section>\n");
    }

    private static void appendDisclaimer(StringBuilder sb, ReportEnvelope env) {
        sb.append("<section class=\"disclaimer\"><h2>Disclaimer</h2>\n");
        sb.append("<p>").append(HtmlEscaper.escape(env.disclaimer())).append("</p>\n");
        sb.append("<p class=\"schema-note\">Schema version: ")
                .append(HtmlEscaper.escape(ReportSchema.REPORT_SCHEMA_VERSION))
                .append(" — generated by ").append(HtmlEscaper.escape(ReportSchema.TOOL_NAME))
                .append(' ').append(HtmlEscaper.escape(ReportSchema.TOOL_VERSION)).append(".</p>\n");
        sb.append("</section>\n");
    }

    private static void appendFooter(StringBuilder sb) {
        sb.append("</body></html>\n");
    }

    /** Hand-written CSS — small, offline, no external resources. */
    private static final String EMBEDDED_CSS = """
            body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                   margin: 2rem; color: #1a1a1a; background: #fafafa; }
            h1 { margin-bottom: 0.25rem; }
            h2 { border-bottom: 1px solid #ccc; padding-bottom: 0.25rem; margin-top: 2rem; }
            .report-meta { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem;
                           font-size: 0.9rem; color: #444; }
            .counts { list-style: none; padding: 0; display: flex; gap: 0.75rem; flex-wrap: wrap; }
            .count { padding: 0.5rem 0.75rem; border: 1px solid #ddd; border-radius: 4px;
                     background: #fff; min-width: 7rem; }
            .count .label { display: block; font-size: 0.75rem; color: #666; text-transform: uppercase; }
            .count .value { font-weight: 600; font-size: 1.5rem; }
            .count-pass { border-left: 4px solid #2e7d32; }
            .count-warning { border-left: 4px solid #f9a825; }
            .count-fail { border-left: 4px solid #c62828; }
            .count-not_assessable { border-left: 4px solid #757575; }
            .count-not_applicable { border-left: 4px solid #90a4ae; }
            .findings-table { border-collapse: collapse; width: 100%; background: #fff; }
            .findings-table th, .findings-table td { border: 1px solid #ddd; padding: 0.5rem;
                                                    text-align: left; vertical-align: top; font-size: 0.9rem; }
            .findings-table th { background: #f0f0f0; }
            .status-pass { color: #2e7d32; font-weight: 600; }
            .status-fail { color: #c62828; font-weight: 600; }
            .status-warning { color: #f9a825; font-weight: 600; }
            .sev-critical { color: #c62828; font-weight: 600; }
            .sev-high { color: #d84315; font-weight: 600; }
            .sev-medium { color: #f9a825; }
            .sev-low { color: #1976d2; }
            .sev-info { color: #555; }
            .disclaimer { margin-top: 2rem; padding: 1rem; background: #fff8e1;
                          border-left: 4px solid #f9a825; }
            .schema-note { font-size: 0.85rem; color: #555; margin-top: 0.5rem; }
            .empty { color: #888; font-style: italic; }
            .conversion-meta { display: grid; grid-template-columns: max-content 1fr; gap: 0.25rem 1rem; }
            """;
}