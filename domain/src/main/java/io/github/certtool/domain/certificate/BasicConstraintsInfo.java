package io.github.certtool.domain.certificate;

import java.util.Objects;

/**
 * Parsed form of the X.509 Basic Constraints extension (RFC 5280 §4.2.1.9).
 *
 * <p>{@code isCa} indicates whether the holder may act as a CA. {@code pathLength}, when
 * present and non-null, restricts how many intermediate CAs may follow this cert.
 */
public record BasicConstraintsInfo(boolean isCa, Integer pathLength) {

    public BasicConstraintsInfo {
        if (pathLength != null && pathLength < 0) {
            throw new IllegalArgumentException("pathLength must be non-negative");
        }
    }

    public static BasicConstraintsInfo absent() {
        return new BasicConstraintsInfo(false, null);
    }
}