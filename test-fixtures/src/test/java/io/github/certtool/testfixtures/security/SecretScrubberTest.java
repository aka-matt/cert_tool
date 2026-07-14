package io.github.certtool.testfixtures.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SecretScrubber}.
 *
 * <p>The scrubber is the second line of defence behind domain-level redaction: it scans arbitrary
 * strings (log lines, error messages, report fragments) and reports whether they contain
 * recognisable sensitive shapes.
 */
@DisplayName("SecretScrubber")
class SecretScrubberTest {

    @Nested
    @DisplayName("PEM private-key blocks")
    class PemBlocks {

        @Test
        @DisplayName("flags a BEGIN PRIVATE KEY block")
        void flagsPrivateKeyBlock() {
            String line = "About to write -----BEGIN RSA PRIVATE KEY-----";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isTrue();
        }

        @Test
        @DisplayName("flags a BEGIN ENCRYPTED PRIVATE KEY block")
        void flagsEncryptedPrivateKeyBlock() {
            String line = "header\n-----BEGIN ENCRYPTED PRIVATE KEY-----\nfooter";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isTrue();
        }

        @Test
        @DisplayName("does not flag a CERTIFICATE block (not sensitive on its own)")
        void doesNotFlagCertificateBlock() {
            String line = "-----BEGIN CERTIFICATE-----";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isFalse();
        }
    }

    @Nested
    @DisplayName("long base64 blobs")
    class LongBase64 {

        @Test
        @DisplayName("flags a base64 string of 256+ chars (likely a keystore)")
        void flagsLongBase64() {
            String longBase64 = "A".repeat(512);

            assertThat(SecretScrubber.containsSensitiveMaterial(longBase64)).isTrue();
        }

        @Test
        @DisplayName("does not flag short base64 (under threshold)")
        void doesNotFlagShort() {
            String shortBase64 = "aGVsbG8="; // "hello" in base64

            assertThat(SecretScrubber.containsSensitiveMaterial(shortBase64)).isFalse();
        }
    }

    @Nested
    @DisplayName("password-shaped key=value pairs")
    class PasswordPairs {

        @Test
        @DisplayName("flags password=... with a non-empty value")
        void flagsPasswordEquals() {
            String line = "config: password=hunter2";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isTrue();
        }

        @Test
        @DisplayName("flags storePassword=...")
        void flagsStorePassword() {
            String line = "storePassword=secret";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isTrue();
        }

        @Test
        @DisplayName("does not flag empty password= value")
        void doesNotFlagEmptyValue() {
            String line = "password=";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isFalse();
        }
    }

    @Nested
    @DisplayName("clean text")
    class CleanText {

        @Test
        @DisplayName("does not flag ordinary log messages")
        void ordinaryLog() {
            String line = "Loaded keystore with 3 entries using provider BC";

            assertThat(SecretScrubber.containsSensitiveMaterial(line)).isFalse();
        }

        @Test
        @DisplayName("does not flag empty input")
        void empty() {
            assertThat(SecretScrubber.containsSensitiveMaterial("")).isFalse();
        }
    }

    @Nested
    @DisplayName("scrub (rewrite)")
    class Scrub {

        @Test
        @DisplayName("replaces a flagged PEM block with a marker")
        void replacesPemBlock() {
            String line = "data -----BEGIN PRIVATE KEY-----xxx-----END PRIVATE KEY-----";

            String scrubbed = SecretScrubber.scrub(line);

            assertThat(scrubbed).doesNotContain("BEGIN PRIVATE KEY");
            assertThat(scrubbed).contains("[REDACTED");
        }

        @Test
        @DisplayName("leaves clean text untouched")
        void leavesCleanText() {
            String line = "ordinary message";

            assertThat(SecretScrubber.scrub(line)).isEqualTo(line);
        }
    }
}