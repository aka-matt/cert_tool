package io.github.certtool.domain.security;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wrapper around a sensitive byte payload (typically a decoded keystore or its Base64
 * encoding).
 *
 * <p>Per spec §2, full keystore bytes and full Base64 keystore blobs MUST NOT appear in logs,
 * error messages, reports, or settings. {@link Base64Blob} enforces this by redacting
 * {@link #toString()} to a fixed marker that exposes only the byte length.
 */
public final class Base64Blob {

    private static final String REDACTED_PREFIX = "<bytes len=";

    private final byte[] bytes;

    public Base64Blob(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        this.bytes = Arrays.copyOf(bytes, bytes.length);
    }

    /** Length in bytes. */
    public int length() {
        return bytes.length;
    }

    /** Returns a defensive copy of the wrapped bytes. */
    public byte[] toBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public String toString() {
        return REDACTED_PREFIX + bytes.length + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Base64Blob other)) {
            return false;
        }
        return Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}