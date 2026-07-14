package io.github.certtool.conversion.domain.preflight;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregate result of a {@link io.github.certtool.conversion.core.Preflight} run. */
public record PreflightReport(List<PreflightFinding> findings) {

    public PreflightReport {
        Objects.requireNonNull(findings, "findings");
        findings = List.copyOf(findings);
    }

    /** True iff at least one finding has severity {@link PreflightSeverity#BLOCK}. */
    public boolean hasBlockers() {
        return findings.stream().anyMatch(f -> f.severity() == PreflightSeverity.BLOCK);
    }

    /** Number of findings with severity {@link PreflightSeverity#BLOCK}. */
    public long blockerCount() {
        return findings.stream().filter(f -> f.severity() == PreflightSeverity.BLOCK).count();
    }

    /** Number of findings with severity {@link PreflightSeverity#WARN}. */
    public long warningCount() {
        return findings.stream().filter(f -> f.severity() == PreflightSeverity.WARN).count();
    }

    /** Counts grouped by severity, suitable for the wizard preflight UI. */
    public Map<PreflightSeverity, Long> counts() {
        Map<PreflightSeverity, Long> m = new EnumMap<>(PreflightSeverity.class);
        for (PreflightSeverity s : PreflightSeverity.values()) {
            m.put(s, 0L);
        }
        for (PreflightFinding f : findings) {
            m.merge(f.severity(), 1L, Long::sum);
        }
        return Map.copyOf(m);
    }
}