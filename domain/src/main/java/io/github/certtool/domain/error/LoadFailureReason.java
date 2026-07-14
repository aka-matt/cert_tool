package io.github.certtool.domain.error;

/**
 * The exhaustive set of reasons a keystore load can fail (per spec §12).
 *
 * <p>Domain-level classification only — user-readable messages and UI mappings live in the
 * application layer.
 */
public enum LoadFailureReason {
    FILE_NOT_FOUND,
    FILE_TOO_LARGE,
    FILE_NOT_READABLE,
    EMPTY_INPUT,
    INVALID_BASE64,
    /** Both supported container probes loaded successfully, so user selection is required. */
    AMBIGUOUS_CONTAINER,
    UNSUPPORTED_FORMAT,
    WRONG_STORE_PASSWORD,
    CORRUPTED_KEYSTORE,
    PROVIDER_UNAVAILABLE,
    ENTRY_PASSWORD_REQUIRED,
    WRONG_ENTRY_PASSWORD,
    UNSUPPORTED_ENTRY,
    CANCELLED,
    UNKNOWN
}
