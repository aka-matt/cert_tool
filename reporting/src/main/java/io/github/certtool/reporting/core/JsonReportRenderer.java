package io.github.certtool.reporting.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.certtool.domain.assessment.AssessmentFinding;
import io.github.certtool.domain.assessment.AssessmentStatus;
import io.github.certtool.reporting.ReportEnvelope;
import io.github.certtool.reporting.SanitizationGuard;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a {@link ReportEnvelope} to JSON.
 *
 * <p>The output is authoritative for the report schema (spec §9). Properties are emitted
 * alphabetically for deterministic, diff-friendly output. {@code schemaVersion},
 * {@code toolName}, {@code toolVersion}, {@code generatedAt}, {@code title}, {@code sourceLabel},
 * {@code disclaimer}, {@code counts}, and {@code findings} are always present.
 *
 * <p>The renderer runs {@link SanitizationGuard#enforce} on its output before returning; if any
 * forbidden pattern is detected, the renderer throws {@link io.github.certtool.reporting.RenderException}.
 */
public final class JsonReportRenderer implements ReportRenderer {

    private final ObjectMapper mapper;

    public JsonReportRenderer() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public byte[] render(ReportEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");

        // Sort the per-status counts map so consumers get deterministic bytes.
        Map<String, Long> counts = new java.util.TreeMap<>();
        if (envelope instanceof ReportEnvelope.AssessmentReportEnvelope a) {
            for (Map.Entry<AssessmentStatus, Long> e : a.counts().entrySet()) {
                counts.put(e.getKey().name(), e.getValue());
            }
        } else {
            for (AssessmentStatus s : AssessmentStatus.values()) {
                counts.put(s.name(), 0L);
            }
        }

        List<FindingView> findingsList = envelope.findings().stream()
                .map(FindingView::of)
                .toList();

        ReportView view;
        if (envelope instanceof ReportEnvelope.AssessmentReportEnvelope a) {
            view = new ReportView(
                    counts,
                    envelope.disclaimer(),
                    envelope.findings().stream().map(FindingView::of).toList(),
                    envelope.generatedAt().toString(),
                    envelope.schemaVersion(),
                    envelope.sourceLabel(),
                    envelope.title(),
                    envelope.toolName(),
                    envelope.toolVersion(),
                    null);
        } else if (envelope instanceof ReportEnvelope.ConversionReportEnvelope c) {
            view = new ReportView(
                    counts,
                    envelope.disclaimer(),
                    List.of(),
                    envelope.generatedAt().toString(),
                    envelope.schemaVersion(),
                    envelope.sourceLabel(),
                    envelope.title(),
                    envelope.toolName(),
                    envelope.toolVersion(),
                    new ConversionView(
                            c.preflightCodes(),
                            c.reloadVerified(),
                            c.targetPath(),
                            c.writtenBytes()));
        } else {
            throw new IllegalArgumentException("Unsupported envelope: " + envelope.getClass());
        }

        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(view);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize report envelope to JSON", e);
        }

        String json = new String(bytes, StandardCharsets.UTF_8);
        // Defense in depth — sanitization must hold for every renderer.
        SanitizationGuard.enforce(json, envelope.title());
        return bytes;
    }

    /** Top-level view — property order here is alphabetical. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({
            "conversion", "counts", "disclaimer", "findings", "generatedAt",
            "schemaVersion", "sourceLabel", "title", "toolName", "toolVersion"
    })
    public record ReportView(
            Map<String, Long> counts,
            String disclaimer,
            List<FindingView> findings,
            String generatedAt,
            String schemaVersion,
            String sourceLabel,
            String title,
            String toolName,
            String toolVersion,
            ConversionView conversion) {}

    @JsonPropertyOrder({"preflightCodes", "reloadVerified", "targetPath", "writtenBytes"})
    public record ConversionView(
            List<String> preflightCodes,
            boolean reloadVerified,
            String targetPath,
            long writtenBytes) {}

    @JsonPropertyOrder({
            "evidence", "references", "remediation", "ruleId",
            "severity", "status", "summary", "title"
    })
    public record FindingView(
            String ruleId,
            String title,
            String status,
            String severity,
            String summary,
            String evidence,
            String remediation,
            List<String> references) {

        static FindingView of(AssessmentFinding f) {
            return new FindingView(
                    f.ruleId(), f.title(),
                    f.status().name(), f.severity().name(),
                    f.summary(), f.evidence(), f.remediation(),
                    f.references());
        }
    }

    // Surfaces a helper used by tests for stable per-status init.
    @SuppressWarnings("unused")
    private static EnumMap<AssessmentStatus, Long> emptyCounts() {
        EnumMap<AssessmentStatus, Long> m = new EnumMap<>(AssessmentStatus.class);
        for (AssessmentStatus s : AssessmentStatus.values()) {
            m.put(s, 0L);
        }
        return m;
    }
}