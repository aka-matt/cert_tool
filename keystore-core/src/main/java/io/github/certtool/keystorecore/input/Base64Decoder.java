package io.github.certtool.keystorecore.input;

import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tolerant Base64 decoder for the keystore tool.
 *
 * <p>Per spec §3, the decoder must accept:
 *
 * <ul>
 *   <li>Standard Base64 (with or without padding)
 *   <li>MIME Base64 with embedded line breaks (LF or CRLF)
 *   <li>Leading and trailing whitespace
 *   <li>Optional {@code -----BEGIN <label>-----} / {@code -----END <label>-----} wrappers
 * </ul>
 *
 * <p>It must produce explicit errors for empty input, illegal characters, truncation, and
 * mismatched BEGIN/END labels. The exception messages never include the offending input.
 */
public final class Base64Decoder {

    /** Default cap on decoded bytes (spec §2 default 100 MB). */
    public static final int DEFAULT_MAX_BYTES = 100 * 1024 * 1024;

    private static final Pattern WRAPPER_PATTERN =
            Pattern.compile(
                    "^\\s*-----BEGIN ([A-Z0-9_-]+)-----\\s*(.*?)\\s*-----END \\1-----\\s*$",
                    Pattern.DOTALL);

    private Base64Decoder() {}

    /** Decodes {@code input} using the default size cap. */
    public static byte[] decode(String input) {
        return decodeWithMaxBytes(input, DEFAULT_MAX_BYTES);
    }

    /**
     * Decodes {@code input} and rejects results longer than {@code maxBytes}. A negative or zero
     * {@code maxBytes} disables the cap.
     */
    public static byte[] decodeWithMaxBytes(String input, int maxBytes) {
        if (input == null || input.strip().isEmpty()) {
            throw new InvalidBase64Exception("Input is empty");
        }
        String unwrapped = stripWrapper(input);
        String stripped = unwrapped.replaceAll("\\s+", "");
        if (stripped.isEmpty()) {
            throw new InvalidBase64Exception("Input is empty");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(stripped);
        } catch (IllegalArgumentException e) {
            throw new InvalidBase64Exception("Invalid Base64 data", e);
        }
        if (maxBytes > 0 && decoded.length > maxBytes) {
            throw new InvalidBase64Exception(
                    "Decoded payload too large (" + decoded.length + " bytes; cap " + maxBytes + ")");
        }
        return decoded;
    }

    private static String stripWrapper(String input) {
        Matcher m = WRAPPER_PATTERN.matcher(input);
        if (m.matches()) {
            return m.group(2);
        }
        // Also accept a "loose" wrapper where labels don't match (rejected below).
        if (input.contains("-----BEGIN") || input.contains("-----END")) {
            // Looks like a wrapper but doesn't conform. Try without, then if there's any
            // non-whitespace content we report it as malformed.
            throw new InvalidBase64Exception("PEM wrapper labels do not match");
        }
        return input;
    }
}