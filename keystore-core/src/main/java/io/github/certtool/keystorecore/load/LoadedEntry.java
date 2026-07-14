package io.github.certtool.keystorecore.load;

import io.github.certtool.domain.keystore.EntryType;
import java.security.Key;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Result of loading a single entry from a keystore.
 *
 * <p>Carries only non-sensitive metadata plus the {@link Certificate} chain (certificates are
 * public). Private keys, secret keys, and entry passwords are NOT carried — those never leave
 * the loader's password-scoped code path.
 */
public record LoadedEntry(
        String alias,
        EntryType entryType,
        Date creationDate,
        List<Certificate> certificateChain,
        String keyAlgorithm,
        Integer keySize,
        boolean readable,
        List<String> warnings) {

    public LoadedEntry {
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(entryType, "entryType");
        Objects.requireNonNull(warnings, "warnings");
        certificateChain = certificateChain == null ? List.of() : List.copyOf(certificateChain);
        warnings = List.copyOf(warnings);
    }

    /**
     * @return the entry's key algorithm, or {@code null} when the entry is a trusted-certificate
     *         entry (no key present) or when the key was not decryptable.
     */
    public String keyAlgorithm(Key resolvedKey) {
        return resolvedKey == null ? null : resolvedKey.getAlgorithm();
    }

    /**
     * @return the entry's key size in bits, or {@code null} if the key is not decryptable or not
     *         an {@link java.security.interfaces.RSAKey}.
     */
    public Integer keySize(Key resolvedKey) {
        if (resolvedKey instanceof java.security.interfaces.RSAKey rsa) {
            return rsa.getModulus().bitLength();
        }
        return null;
    }

    /** Convenience factory for a trusted-certificate entry. */
    public static LoadedEntry trustedCertificate(String alias, Certificate cert, Date creationDate) {
        return new LoadedEntry(
                alias, EntryType.TRUSTED_CERTIFICATE, creationDate, List.of(cert), null, null, true, List.of());
    }

    /** Convenience factory for an entry that we could not decrypt. */
    public static LoadedEntry unknown(String alias, String warning) {
        return new LoadedEntry(alias, EntryType.UNKNOWN, null, List.of(), null, null, false, List.of(warning));
    }
}