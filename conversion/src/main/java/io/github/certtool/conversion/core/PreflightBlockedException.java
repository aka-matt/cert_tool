package io.github.certtool.conversion.core;

import io.github.certtool.conversion.domain.preflight.PreflightReport;
import java.util.Objects;

/**
 * Raised by the orchestrator when the preflight stage refuses the conversion because of a BLOCK
 * finding. The wizard catches this exception, displays its report prominently, and offers the user
 * a way to revise the plan (spec §9 wizard step 4).
 *
 * <p>The message is intentionally generic — never includes password material or full keystore bytes.
 */
public final class PreflightBlockedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final PreflightReport report;

    public PreflightBlockedException(PreflightReport report) {
        super("Conversion blocked by preflight: " + Objects.requireNonNull(report, "report").blockerCount()
                + " blocker(s), " + report.warningCount() + " warning(s).");
        this.report = report;
    }

    public PreflightReport report() {
        return report;
    }
}