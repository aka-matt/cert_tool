package io.github.certtool.certanalysis.core;

import io.github.certtool.domain.certificate.FingerprintBundle;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Computes SHA-256 and SHA-1 fingerprints of an X.509 certificate's DER encoding.
 *
 * <p>Fingerprints are deterministic for a given certificate body. Two certificates that differ
 * in any byte (including signature) produce different fingerprints.
 */
public final class Fingerprints {

    private Fingerprints() {}

    public static FingerprintBundle of(X509Certificate cert) {
        Objects.requireNonNull(cert, "cert");
        byte[] der;
        try {
            der = cert.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("Could not DER-encode certificate", e);
        }
        return new FingerprintBundle(
                hex(sha256(der)),
                hex(sha1(der)),
                colonUpper(sha256(der)),
                colonUpper(sha1(der)));
    }

    private static byte[] sha256(byte[] input) {
        return digest("SHA-256", input);
    }

    private static byte[] sha1(byte[] input) {
        return digest("SHA-1", input);
    }

    private static byte[] digest(String alg, byte[] input) {
        try {
            return MessageDigest.getInstance(alg).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(alg + " not available", e);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static String colonUpper(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }
}