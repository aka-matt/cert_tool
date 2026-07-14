package io.github.certtool.domain.certificate;

import java.util.Objects;

/**
 * Cryptographic fingerprints of an X.509 certificate.
 *
 * <p>Per spec §5, every parsed certificate exposes SHA-256 and SHA-1 fingerprints. We store the
 * hex form ({@code sha256}, {@code sha1}) and a colon-separated uppercase form
 * ({@code sha256Formatted}, {@code sha1Formatted}) for UI display.
 *
 * <p>Fingerprints are public information derived from the certificate body; they are not
 * secrets and may safely appear in logs and reports.
 */
public record FingerprintBundle(String sha256, String sha1, String sha256Formatted, String sha1Formatted) {

    public FingerprintBundle {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(sha1, "sha1");
        Objects.requireNonNull(sha256Formatted, "sha256Formatted");
        Objects.requireNonNull(sha1Formatted, "sha1Formatted");
    }
}