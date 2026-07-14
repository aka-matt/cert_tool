package io.github.certtool.conversion.domain.preflight;

import java.util.Objects;

/**
 * One finding produced by the {@link io.github.certtool.conversion.core.Preflight} stage.
 *
 * <p>{@code alias} is nullable (some findings are plan-level, not entry-level). {@code message}
 * MUST NOT contain passwords, key bytes, or the full Base64 keystore (spec §2).
 */
public record PreflightFinding(
        PreflightSeverity severity,
        String code,
        String alias,
        String message) {

    public PreflightFinding {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }
}