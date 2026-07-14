package io.github.certtool.domain.inspect;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Counts and metadata that summarise a successfully loaded keystore. */
public record KeyStoreSummary(
        KeyStoreContainerType containerType,
        ContentEncoding encoding,
        String providerName,
        String providerVersion,
        Map<EntryType, Integer> entryCountsByType,
        int totalEntries,
        int totalCertificates) {

    public KeyStoreSummary {
        Objects.requireNonNull(containerType, "containerType");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(providerVersion, "providerVersion");
        Objects.requireNonNull(entryCountsByType, "entryCountsByType");
        entryCountsByType = Map.copyOf(entryCountsByType);
        if (totalEntries < 0) {
            throw new IllegalArgumentException("totalEntries must be >= 0");
        }
        if (totalCertificates < 0) {
            throw new IllegalArgumentException("totalCertificates must be >= 0");
        }
    }

    /** Derives a summary from a successful load result. */
    public static KeyStoreSummary from(KeyStoreLoadResult result, ContentEncoding encoding) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(result.container(), "result.container");
        Map<EntryType, Integer> counts = new EnumMap<>(EntryType.class);
        counts.put(EntryType.PRIVATE_KEY, 0);
        counts.put(EntryType.TRUSTED_CERTIFICATE, 0);
        counts.put(EntryType.SECRET_KEY, 0);
        counts.put(EntryType.UNKNOWN, 0);

        int total = 0;
        int certs = 0;
        for (var e : result.entries()) {
            counts.merge(e.entryType(), 1, Integer::sum);
            certs += e.certificateChain().size();
            total++;
        }
        return new KeyStoreSummary(
                result.container(),
                encoding,
                result.providerName() == null ? "" : result.providerName(),
                result.providerVersion() == null ? "" : result.providerVersion(),
                counts,
                total,
                certs);
    }
}
