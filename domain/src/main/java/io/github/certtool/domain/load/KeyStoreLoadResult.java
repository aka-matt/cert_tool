package io.github.certtool.domain.load;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a keystore load operation. Either {@link #isSuccess()} is {@code true} and
 * {@link #container()} + {@link #entries()} are populated, or {@link #failure()} is populated.
 */
public record KeyStoreLoadResult(
        boolean success,
        KeyStoreContainerType container,
        String providerName,
        String providerVersion,
        List<LoadedEntry> entries,
        LoadFailure failure) {

    public KeyStoreLoadResult {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
        if (success) {
            Objects.requireNonNull(container, "container");
        } else {
            Objects.requireNonNull(failure, "failure");
        }
    }

    public static KeyStoreLoadResult success(
            KeyStoreContainerType container,
            String providerName,
            String providerVersion,
            List<LoadedEntry> entries) {
        return new KeyStoreLoadResult(true, container, providerName, providerVersion, entries, null);
    }

    public static KeyStoreLoadResult failure(KeyFailure f) {
        return new KeyStoreLoadResult(false, null, null, null, List.of(), f.toDomainFailure());
    }

    public Optional<LoadFailure> failureOptional() {
        return Optional.ofNullable(failure);
    }

    public boolean isSuccess() {
        return success;
    }
}