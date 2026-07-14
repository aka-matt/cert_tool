package io.github.certtool.conversion.domain.result;

import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import java.util.Objects;

/**
 * Result of a successful conversion run. Holds the {@link PreflightReport} that authorised the run,
 * the {@link ReloadVerification} that confirmed the write succeeded, the actual on-disk path
 * written, and the number of bytes flushed to disk. {@code preflightReport} is included so the
 * UI can show WARN findings alongside the success message.
 */
public record ConversionResult(
        ConversionPlan plan,
        PreflightReport preflightReport,
        ReloadVerification verification,
        String targetPath,
        long writtenBytes) {

    public ConversionResult {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(preflightReport, "preflightReport");
        Objects.requireNonNull(verification, "verification");
        Objects.requireNonNull(targetPath, "targetPath");
    }
}