package io.github.certtool.domain.keystore;

import java.util.Objects;

/** Cross-product of container type and content encoding — describes the user-visible form. */
public record KeyStoreDescriptor(KeyStoreContainerType container, ContentEncoding encoding) {
    public KeyStoreDescriptor {
        Objects.requireNonNull(container, "container");
        Objects.requireNonNull(encoding, "encoding");
    }
}