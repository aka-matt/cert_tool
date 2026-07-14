package io.github.certtool.keystorecore.password;

import java.util.Objects;

/**
 * Request to a {@link PasswordProvider} for the keystore's store password.
 *
 * <p>The {@code sourceDescription} is rendered in the UI dialog (e.g., {@code "JKS file:
 * foo.jks"}). The {@code attemptNumber} and {@code maxAttempts} allow the provider to display
 * retry progress.
 */
public record StorePasswordRequest(String sourceDescription, int attemptNumber, int maxAttempts) {

    public StorePasswordRequest {
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }
}