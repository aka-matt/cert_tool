package io.github.certtool.reporting;

import java.util.Objects;

/**
 * One occurrence of a forbidden pattern in a rendered report.
 *
 * <p>Carries the regex pattern that matched, the offending substring, its offset in the source
 * string, and a logical source label (e.g. a report id) so operators can correlate the leak with
 * the call site.
 */
public record SanitizationViolation(
        String source,
        String pattern,
        String match,
        int offset) {

    public SanitizationViolation {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(pattern, "pattern");
        Objects.requireNonNull(match, "match");
    }
}