package io.github.certtool.keystorecore.load;

import java.util.Objects;

/**
 * Options that control a single keystore load.
 *
 * <p>Per spec §4, the loader can be instructed to skip private-key entries that the user cannot
 * (or will not) decrypt, while still returning the entry as {@code UNKNOWN} with a warning.
 */
public record LoadOptions(int maxPasswordAttempts, boolean skipUndecryptableEntries) {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    public LoadOptions {
        if (maxPasswordAttempts < 1) {
            throw new IllegalArgumentException("maxPasswordAttempts must be >= 1");
        }
        Objects.requireNonNull(skipUndecryptableEntries, "skipUndecryptableEntries");
    }

    public static LoadOptions defaults() {
        return new LoadOptions(DEFAULT_MAX_ATTEMPTS, false);
    }

    public static LoadOptions skipUndecryptable() {
        return new LoadOptions(DEFAULT_MAX_ATTEMPTS, true);
    }
}