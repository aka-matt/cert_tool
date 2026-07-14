package io.github.certtool.reporting.core;

import io.github.certtool.reporting.ReportEnvelope;

/** SPI every renderer implements. */
public interface ReportRenderer {

    /**
     * Renders {@code envelope} to bytes. Implementations MUST run
     * {@link io.github.certtool.reporting.SanitizationGuard#enforce} on their output before
     * returning; the guard's {@code source} label identifies the envelope for diagnostics.
     */
    byte[] render(ReportEnvelope envelope);
}