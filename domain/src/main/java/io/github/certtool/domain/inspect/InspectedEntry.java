package io.github.certtool.domain.inspect;

import io.github.certtool.domain.keystore.EntryType;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/** One loaded entry plus its parsed certificate analyses. */
public record InspectedEntry(
        String alias,
        EntryType entryType,
        Date creationDate,
        boolean readable,
        String keyAlgorithm,
        Integer keySize,
        List<InspectedCertificate> certificates,
        List<String> warnings) {

    public InspectedEntry {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(certificates, "certificates");
        Objects.requireNonNull(warnings, "warnings");
        certificates = List.copyOf(certificates);
        warnings = List.copyOf(warnings);
    }
}
