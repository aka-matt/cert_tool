package io.github.certtool.keystorecore.input;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;

/**
 * Thrown when Base64 input cannot be decoded. Wrapped at the loader boundary into a {@link
 * LoadFailure} with reason {@link LoadFailureReason#INVALID_BASE64} or {@link
 * LoadFailureReason#EMPTY_INPUT}.
 *
 * <p>The exception message intentionally does NOT include the offending input — it would often be
 * the full keystore payload.
 */
public final class InvalidBase64Exception extends RuntimeException {

    public InvalidBase64Exception(String message) {
        super(message);
    }

    public InvalidBase64Exception(String message, Throwable cause) {
        super(message, cause);
    }
}