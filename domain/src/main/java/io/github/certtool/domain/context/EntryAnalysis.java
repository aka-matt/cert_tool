package io.github.certtool.domain.context;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ChainAnalysis;
import io.github.certtool.domain.keystore.EntryType;
import java.util.Objects;

/**
 * Bundles everything a rule needs to evaluate a single keystore entry: its alias, type, the
 * parsed certificate (when applicable), the chain analysis (when applicable), and the public-key
 * fingerprint (SHA-256, hex, lower-case) for evidence strings.
 *
 * <p>For {@link EntryType#SECRET_KEY} and {@link EntryType#UNKNOWN}, the certificate / chain /
 * fingerprint fields will be {@code null}.
 */
public record EntryAnalysis(
        String alias,
        EntryType entryType,
        CertificateAnalysis certificate,
        ChainAnalysis chain,
        String publicKeySha256Hex) {

    public EntryAnalysis {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(entryType, "entryType");
    }

    /** True iff this entry is a private-key entry (regardless of container). */
    public boolean isPrivateKey() {
        return entryType == EntryType.PRIVATE_KEY;
    }

    /** True iff this entry is a trusted certificate entry. */
    public boolean isTrustedCertificate() {
        return entryType == EntryType.TRUSTED_CERTIFICATE;
    }

    /** True iff this entry is a secret-key entry. */
    public boolean isSecretKey() {
        return entryType == EntryType.SECRET_KEY;
    }
}