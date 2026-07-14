package io.github.certtool.domain.context;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import java.util.Objects;

/**
 * Summary facts about a successfully loaded keystore. Distinct from
 * {@link io.github.certtool.domain.keystore.KeyStoreDescriptor} which only describes the
 * user-visible form (container × encoding).
 *
 * <p>Used by rules to assess container posture (e.g. private-key entries in JKS) and to enumerate
 * aliases for the Findings table. Path may be {@code null} for in-memory keystores (e.g. a
 * Base64 paste); other fields are always populated.
 */
public record LoadedKeyStoreInfo(
        KeyStoreContainerType containerType,
        ContentEncoding encoding,
        String sourcePath,
        long sizeBytes,
        boolean integrityCheckPassed,
        List<String> aliases) {

    public LoadedKeyStoreInfo {
        Objects.requireNonNull(containerType, "containerType");
        Objects.requireNonNull(encoding, "encoding");
        Objects.requireNonNull(aliases, "aliases");
        aliases = List.copyOf(aliases);
    }
}