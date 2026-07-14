package io.github.certtool.reporting;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Raised by {@link SanitizationGuard#enforce} when a renderer's output contains forbidden patterns
 * (password strings, PEM private-key markers, full Base64 keystores, false FIPS-certification
 * claims — see spec §2 and §6).
 *
 * <p>This is the last line of defence: even if a future bug in a renderer accidentally leaks key
 * material, the guard refuses to hand the bytes back to the caller.
 */
public final class RenderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<SanitizationViolation> violations;

    public RenderException(List<SanitizationViolation> violations) {
        super(buildMessage(violations));
        this.violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
    }

    public List<SanitizationViolation> violations() {
        return violations;
    }

    private static String buildMessage(List<SanitizationViolation> violations) {
        return "Sanitization guard rejected output for source='"
                + (violations.isEmpty() ? "<unknown>" : violations.get(0).source())
                + "' — " + violations.size() + " violation(s): "
                + violations.stream()
                        .map(v -> v.pattern() + " at offset " + v.offset())
                        .collect(Collectors.joining("; "));
    }
}