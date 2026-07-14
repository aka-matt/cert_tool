package io.github.certtool.keystorecore.password;

import io.github.certtool.domain.keystore.EntryType;
import java.util.Objects;

/**
 * Request to a {@link PasswordProvider} for a single entry's key password.
 *
 * <p>{@code entryType} tells the provider whether this is a private key, secret key, etc., so the
 * dialog can label itself appropriately.
 */
public record EntryPasswordRequest(
        String sourceDescription, String alias, EntryType entryType, int attemptNumber, int maxAttempts) {

    public EntryPasswordRequest {
        Objects.requireNonNull(sourceDescription, "sourceDescription");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(entryType, "entryType");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
    }
}