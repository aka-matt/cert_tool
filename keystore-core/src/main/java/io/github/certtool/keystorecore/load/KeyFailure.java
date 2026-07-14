package io.github.certtool.keystorecore.load;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import java.util.Optional;

/**
 * Internal loader-level failure record. Wraps a {@link LoadFailureReason}, an augmented message
 * (suitable for both the UI and technical diagnostics), and an optional cause.
 *
 * <p>The augmented message becomes the {@code technicalReason} of the resulting {@link LoadFailure}
 * so that reports and logs carry the indeterminate / cause hint without leaking key material.
 */
record KeyFailure(LoadFailureReason reason, String message, Throwable cause) {

    LoadFailure toDomainFailure() {
        String tech = message;
        if (cause != null) {
            tech = tech + " (" + cause.getClass().getSimpleName() + ")";
        }
        return new LoadFailure(
                reason, message, tech, /*retryable*/ false, /*needsPassword*/ reason == LoadFailureReason.WRONG_STORE_PASSWORD || reason == LoadFailureReason.WRONG_ENTRY_PASSWORD, Optional.ofNullable(cause));
    }

    static KeyFailure of(LoadFailureReason reason, String userMessage) {
        return new KeyFailure(reason, userMessage, null);
    }

    static KeyFailure of(LoadFailureReason reason, String userMessage, Throwable cause) {
        return new KeyFailure(reason, userMessage, Optional.ofNullable(cause).orElse(null));
    }
}