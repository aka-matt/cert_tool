package io.github.certtool.domain.inspect;

import java.util.List;
import java.util.Objects;

/** Aggregated view of a fully analyzed keystore. */
public record InspectedKeyStore(KeyStoreSummary summary, List<InspectedEntry> entries) {

    public InspectedKeyStore {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }
}
