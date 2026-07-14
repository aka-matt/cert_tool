package io.github.certtool.testfixtures.security;

import java.util.regex.Pattern;

/**
 * Heuristic scanner that detects sensitive shapes in arbitrary text.
 *
 * <p>This is the second line of defence behind domain-level redaction (per ADR-0007). It is used
 * by tests to assert that log output, error messages, and report fragments do not leak passwords,
 * private keys, or full keystore blobs.
 *
 * <p>The patterns are deliberately conservative — they target high-confidence shapes only, not
 * arbitrary "looks like a secret." A determined adversary could craft a payload that slips past;
 * that is acceptable because the domain-level redaction is the real guarantee.
 */
public final class SecretScrubber {

    /** Minimum base64 length to count as "full keystore blob." */
    private static final int LONG_BASE64_THRESHOLD = 256;

    private static final Pattern PEM_PRIVATE_KEY =
            Pattern.compile("-----BEGIN (?:RSA |EC |ENCRYPTED |)?PRIVATE KEY-----");

    private static final Pattern LONG_BASE64 =
            Pattern.compile("[A-Za-z0-9+/=\\r\\n]{" + LONG_BASE64_THRESHOLD + ",}");

    private static final Pattern PASSWORD_KV =
            Pattern.compile(
                    "\\b(?:password|storePassword|keyPassword|store_password|key_password)\\s*=\\s*([^\\s,;}]+)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern PEM_PRIVATE_KEY_FULL =
            Pattern.compile(
                    "-----BEGIN (?:RSA |EC |ENCRYPTED |)?PRIVATE KEY-----[\\s\\S]*?-----END (?:RSA |EC |ENCRYPTED |)?PRIVATE KEY-----");

    private SecretScrubber() {}

    /**
     * @return {@code true} if the input contains a recognisable sensitive shape.
     */
    public static boolean containsSensitiveMaterial(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return PEM_PRIVATE_KEY.matcher(input).find()
                || LONG_BASE64.matcher(input.replaceAll("\\s", "")).find()
                || matchesPasswordPair(input);
    }

    private static boolean matchesPasswordPair(String input) {
        var matcher = PASSWORD_KV.matcher(input);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value != null && !value.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a copy of {@code input} with recognised sensitive material replaced by
     * {@code [REDACTED:type]}.
     */
    public static String scrub(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String result = input;
        if (PEM_PRIVATE_KEY_FULL.matcher(result).find()) {
            result = PEM_PRIVATE_KEY_FULL.matcher(result)
                    .replaceAll("[REDACTED:pem-private-key-block]");
        } else if (PEM_PRIVATE_KEY.matcher(result).find()) {
            result = PEM_PRIVATE_KEY.matcher(result).replaceAll("[REDACTED:pem-header]");
        }
        return result;
    }
}