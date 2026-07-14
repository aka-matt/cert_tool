package io.github.certtool.domain.error;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured load-failure result (per spec §12).
 *
 * <p>{@code technicalReason} is for diagnostics; {@code userMessage} is rendered to the UI. The
 * original exception is intentionally wrapped to discourage accidental exposure of Provider
 * exception text, which often contains key material hints.
 */
public record LoadFailure(
        LoadFailureReason reason,
        String userMessage,
        String technicalReason,
        boolean retryable,
        boolean needsPassword,
        Optional<Throwable> cause) {

    public LoadFailure {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(technicalReason, "technicalReason");
        Objects.requireNonNull(cause, "cause");
        technicalReason = technicalReason.isBlank() ? reason.name() : technicalReason;
    }

    public static LoadFailure of(LoadFailureReason reason, String userMessage) {
        return new LoadFailure(reason, userMessage, reason.name(), false, false, Optional.empty());
    }

    public static LoadFailure of(
            LoadFailureReason reason, String userMessage, Throwable cause) {
        return new LoadFailure(
                reason,
                userMessage,
                reason.name() + ": " + cause.getClass().getSimpleName(),
                false,
                false,
                Optional.ofNullable(cause));
    }
}