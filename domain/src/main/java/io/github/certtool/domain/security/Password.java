package io.github.certtool.domain.security;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable wrapper around a password stored as {@code char[]}.
 *
 * <p>Per spec §2, passwords MUST be held as {@code char[]} (not {@link String}) and zeroed when no
 * longer needed. Use try-with-resources to ensure the buffer is wiped:
 *
 * <pre>{@code
 * try (Password p = new Password("secret".toCharArray())) {
 *     // pass p.toCharArray() to a KeyStore API
 * }
 * // internal buffer is now zeroed
 * }</pre>
 *
 * <p><b>Caveat.</b> The JVM may have copied the buffer during its lifetime (e.g., during GC
 * compaction). This type guarantees only that the buffer it owns is zeroed; it cannot guarantee
 * no other copies exist anywhere in the JVM. Callers must keep the lifetime of a password short and
 * avoid any logging or string concatenation that touches the buffer.
 *
 * <p>{@link #toString()} returns a fixed redacted marker so accidental logging cannot leak the
 * characters. {@link #equals(Object)} and {@link #hashCode()} are content-based.
 */
public final class Password implements AutoCloseable {

    /** Stable, content-free marker used by {@link #toString()}. */
    private static final String REDACTED = "<password>";

    private char[] chars;

    public Password(char[] chars) {
        Objects.requireNonNull(chars, "chars");
        // Defensive copy so external mutation cannot affect us.
        this.chars = Arrays.copyOf(chars, chars.length);
    }

    /** Length in characters. */
    public int length() {
        return chars.length;
    }

    /**
     * Returns a defensive copy of the underlying characters. Callers are expected to zero this
     * copy when done, although the in-memory buffer cannot be fully guaranteed to be wiped.
     *
     * <p>After {@link #close()}, this still returns a same-length copy, but every position is
     * {@code '\0'}. The internal buffer is never nulled so callers may safely observe the wiped
     * state (e.g. from a test) and so that downstream APIs that demand a {@code char[]} never see
     * an array of unexpected length.
     */
    public char[] toCharArray() {
        return Arrays.copyOf(chars, chars.length);
    }

    /** Zeroes every position of the internal buffer. Idempotent. */
    @Override
    public void close() {
        Arrays.fill(chars, '\0');
    }

    @Override
    public String toString() {
        return REDACTED;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Password other)) {
            return false;
        }
        return Arrays.equals(this.chars, other.chars);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(chars);
    }
}