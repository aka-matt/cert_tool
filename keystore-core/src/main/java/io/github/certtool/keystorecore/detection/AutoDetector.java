package io.github.certtool.keystorecore.detection;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.keystore.KeyStoreDescriptor;
import io.github.certtool.keystorecore.input.Base64Decoder;
import io.github.certtool.keystorecore.input.InvalidBase64Exception;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Combines content-encoding detection (binary vs Base64) with container-type detection to produce a
 * {@link KeyStoreDescriptor}.
 *
 * <p>Per spec §3, encoding is decided first by trying a Base64 decode and checking whether the
 * result has a known keystore magic. If the raw bytes match a magic, encoding is BINARY; otherwise
 * we attempt a Base64 decode and try again on the decoded bytes.
 */
public final class AutoDetector {

    private AutoDetector() {}

    /**
     * Detects the user-visible {@link KeyStoreDescriptor} for the given input.
     *
     * @param input the raw bytes the user supplied (file content, paste, drag-and-drop)
     * @return the descriptor, or empty if neither binary nor Base64 magic could be matched
     */
    public static Optional<KeyStoreDescriptor> detect(byte[] input) {
        if (input == null || input.length == 0) {
            return Optional.empty();
        }

        // 1. Try binary detection first (cheap).
        KeyStoreContainerType direct = ContainerDetector.detectContainer(input);
        if (direct != null) {
            return Optional.of(new KeyStoreDescriptor(direct, ContentEncoding.BINARY));
        }

        // 2. Try interpreting the input as ASCII / UTF-8 text and Base64-decoding it.
        String asText = new String(input, StandardCharsets.UTF_8);
        if (looksLikeBase64(asText)) {
            try {
                byte[] decoded = Base64Decoder.decode(asText);
                KeyStoreContainerType fromDecoded = ContainerDetector.detectContainer(decoded);
                if (fromDecoded != null) {
                    return Optional.of(new KeyStoreDescriptor(fromDecoded, ContentEncoding.BASE64));
                }
            } catch (InvalidBase64Exception ignored) {
                // Fall through.
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeBase64(String text) {
        if (text.isBlank()) {
            return false;
        }
        // Allow BEGIN/END wrapper lines.
        String stripped = text.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        if (stripped.isEmpty()) {
            return false;
        }
        // All characters must be in the Base64 alphabet.
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (!isBase64Char(c)) {
                return false;
            }
        }
        // Length must be a multiple of 4 (with optional padding) or have valid padding shape.
        int mod = stripped.length() % 4;
        return mod == 0 || mod == 2 || mod == 3;
    }

    private static boolean isBase64Char(char c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '+'
                || c == '/'
                || c == '=';
    }

    /** Convenience: whether a base64-encoded text would round-trip through {@link Base64}. */
    public static boolean isBase64Text(String text) {
        return looksLikeBase64(text);
    }
}