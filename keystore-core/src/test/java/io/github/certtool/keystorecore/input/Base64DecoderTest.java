package io.github.certtool.keystorecore.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Base64Decoder")
class Base64DecoderTest {

    private static byte[] decode(String s) {
        return Base64Decoder.decode(s);
    }

    @Nested
    @DisplayName("standard Base64")
    class StandardBase64 {

        @Test
        @DisplayName("decodes the canonical Base64 alphabet")
        void canonical() {
            assertThat(decode("aGVsbG8=")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("decodes unpadded Base64")
        void unpadded() {
            assertThat(decode("aGVsbG8")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("MIME Base64 (with newlines)")
    class MimeBase64 {

        @Test
        @DisplayName("accepts line breaks every 76 chars")
        void lineBreaks() {
            String many = "A".repeat(80);
            String b64 = java.util.Base64.getMimeEncoder().encodeToString(many.getBytes(StandardCharsets.UTF_8));

            assertThat(decode(b64)).isEqualTo(many.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("whitespace handling")
    class WhitespaceHandling {

        @Test
        @DisplayName("strips leading and trailing whitespace")
        void stripEnds() {
            assertThat(decode("  aGVsbG8=  ")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("ignores embedded spaces")
        void embeddedSpaces() {
            assertThat(decode("aGVs bG8=")).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("accepts CRLF and LF line endings mixed")
        void mixedLineEndings() {
            String b64 = "aGVs\r\nbG8=";
            assertThat(decode(b64)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("optional BEGIN/END wrappers")
    class PemWrappers {

        @Test
        @DisplayName("strips -----BEGIN JKS----- / -----END JKS----- wrappers")
        void jksWrappers() {
            String wrapped =
                    "-----BEGIN JKS-----\n"
                            + "aGVsbG8=\n"
                            + "-----END JKS-----";

            assertThat(decode(wrapped)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("strips -----BEGIN BCFKS----- / -----END BCFKS----- wrappers")
        void bcfksWrappers() {
            String wrapped =
                    "-----BEGIN BCFKS-----\n"
                            + "aGVsbG8=\n"
                            + "-----END BCFKS-----";

            assertThat(decode(wrapped)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("accepts a custom wrapper label")
        void customLabel() {
            String wrapped =
                    "-----BEGIN MY-KEYSTORE-----\n"
                            + "aGVsbG8=\n"
                            + "-----END MY-KEYSTORE-----";

            assertThat(decode(wrapped)).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nested
    @DisplayName("error cases")
    class ErrorCases {

        @Test
        @DisplayName("rejects empty input")
        void empty() {
            assertThatThrownBy(() -> decode(""))
                    .isInstanceOf(InvalidBase64Exception.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects whitespace-only input")
        void whitespaceOnly() {
            assertThatThrownBy(() -> decode("   \n  \t  "))
                    .isInstanceOf(InvalidBase64Exception.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("rejects illegal characters")
        void illegalChar() {
            assertThatThrownBy(() -> decode("aGVs*bG8="))
                    .isInstanceOf(InvalidBase64Exception.class);
        }

        @Test
        @DisplayName("rejects truncated data (1-char input cannot form a valid Base64 unit)")
        void truncated() {
            assertThatThrownBy(() -> decode("a"))
                    .isInstanceOf(InvalidBase64Exception.class);
        }

        @Test
        @DisplayName("rejects mismatched BEGIN/END labels")
        void mismatchedLabels() {
            String wrapped = "-----BEGIN JKS-----\n" + "aGVsbG8=\n" + "-----END BCFKS-----";

            assertThatThrownBy(() -> decode(wrapped))
                    .isInstanceOf(InvalidBase64Exception.class);
        }
    }

    @Nested
    @DisplayName("size cap")
    class SizeCap {

        @Test
        @DisplayName("rejects input whose decoded length exceeds the cap")
        void exceedsCap() {
            String huge = "A".repeat(300); // 300 base64 chars => 225 bytes

            assertThatThrownBy(() -> Base64Decoder.decodeWithMaxBytes(huge, 100))
                    .isInstanceOf(InvalidBase64Exception.class)
                    .hasMessageContaining("too large");
        }
    }
}