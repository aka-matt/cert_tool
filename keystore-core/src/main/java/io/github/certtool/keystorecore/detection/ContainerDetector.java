package io.github.certtool.keystorecore.detection;

import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.Arrays;

/**
 * Detects a keystore container type from raw byte content by inspecting the leading magic bytes.
 *
 * <p>This is a cheap, password-free pre-check used by {@code KeyStoreLoader} to decide which
 * {@link java.security.KeyStore} type to instantiate and, when auto-detection is uncertain, to
 * present a more informative error.
 *
 * <p>The detector NEVER throws. It returns {@code null} on empty, too-short, or unrecognised input.
 */
public final class ContainerDetector {

    /**
     * JKS magic number (little-endian) followed by version 2.
     *
     * <p>Spec: PKCS#12-style Java keystore format. The 4-byte magic {@code 0xFEEDFEED} is the
     * historical Sun JKS marker; the next 4 bytes encode the format version (we accept any
     * version &ge; 1 to be lenient).
     */
    private static final byte[] JKS_MAGIC = {
        (byte) 0xFE, (byte) 0xED, (byte) 0xFE, (byte) 0xED
    };

    /**
     * BCFKS magic: the payload is a DER-encoded ASN.1 SEQUENCE (a PKCS#8
     * EncryptedPrivateKeyInfo wrapping the keystore contents). The first byte is {@code 0x30} and
     * the second byte is either the single-byte length or a definite long-form length indicator.
     *
     * <p>This is a necessary-but-not-sufficient condition; the auto-detector attempts a real load
     * to confirm.
     */
    private static final byte BCFKS_FIRST_BYTE = 0x30;

    private ContainerDetector() {}

    /**
     * @param bytes the keystore payload (decoded if Base64-encoded input)
     * @return the detected container type, or {@code null} if no known container is recognised
     */
    public static KeyStoreContainerType detectContainer(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if (Arrays.equals(bytes, 0, 4, JKS_MAGIC, 0, 4)) {
            return KeyStoreContainerType.JKS;
        }
        if (looksLikeBcfksDer(bytes)) {
            return KeyStoreContainerType.BCFKS;
        }
        return null;
    }

    private static boolean looksLikeBcfksDer(byte[] bytes) {
        if (bytes[0] != BCFKS_FIRST_BYTE) {
            return false;
        }
        int second = bytes[1] & 0xFF;
        // Single-byte length: 0..127
        if ((second & 0x80) == 0) {
            return true;
        }
        // DER definite long-form length. A Java byte[] can hold at most a four-byte length,
        // including the 0x83 form used by containers larger than 64 KiB.
        int lengthByteCount = second & 0x7F;
        return lengthByteCount >= 1 && lengthByteCount <= Integer.BYTES && bytes.length >= 2 + lengthByteCount;
    }
}
