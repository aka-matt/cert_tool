package io.github.certtool.domain.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Base64Blob}.
 *
 * <p>A {@code Base64Blob} wraps a byte array (typically a decoded keystore payload) so that the
 * bytes cannot accidentally appear in a {@link Object#toString()} or in serialized output.
 */
@DisplayName("Base64Blob")
class Base64BlobTest {

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("wraps a defensive copy of the input array")
        void wrapsDefensiveCopy() {
            byte[] input = {0x01, 0x02, 0x03};

            Base64Blob b = new Base64Blob(input);

            input[0] = (byte) 0xFF;

            assertThat(b.toBytes()).containsExactly(0x01, 0x02, 0x03);
        }

        @Test
        @DisplayName("rejects null")
        void rejectsNull() {
            assertThatThrownBy(() -> new Base64Blob(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("bytes");
        }

        @Test
        @DisplayName("accepts an empty array")
        void acceptsEmpty() {
            Base64Blob b = new Base64Blob(new byte[0]);

            assertThat(b.length()).isZero();
            assertThat(b.toBytes()).isEmpty();
        }
    }

    @Nested
    @DisplayName("redaction")
    class Redaction {

        @Test
        @DisplayName("toString never contains decoded bytes as readable ASCII")
        void toStringIsRedacted() {
            // ASCII bytes form "PASSWORD123" — must not appear in toString().
            byte[] ascii = "PASSWORD123".getBytes();

            Base64Blob b = new Base64Blob(ascii);

            assertThat(b.toString())
                    .doesNotContain("PASSWORD123")
                    .doesNotContain("PASSWORD")
                    .doesNotContain("123");
        }

        @Test
        @DisplayName("toString is stable across distinct contents")
        void toStringIsStable() {
            Base64Blob a = new Base64Blob(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
            Base64Blob b = new Base64Blob(new byte[] {9, 9, 9, 9, 9, 9, 9, 9});

            assertThat(a.toString()).isEqualTo(b.toString());
        }

        @Test
        @DisplayName("toString indicates the byte length but not the contents")
        void toStringShowsLength() {
            Base64Blob b = new Base64Blob(new byte[42]);

            assertThat(b.toString()).contains("42");
        }
    }

    @Nested
    @DisplayName("equality")
    class Equality {

        @Test
        @DisplayName("two Base64Blobs with the same bytes are equal")
        void equalByBytes() {
            Base64Blob a = new Base64Blob(new byte[] {1, 2, 3});
            Base64Blob b = new Base64Blob(new byte[] {1, 2, 3});

            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("two Base64Blobs with different bytes are not equal")
        void notEqualByBytes() {
            Base64Blob a = new Base64Blob(new byte[] {1, 2, 3});
            Base64Blob b = new Base64Blob(new byte[] {4, 5, 6});

            assertThat(a).isNotEqualTo(b);
        }
    }

    @Nested
    @DisplayName("accessors")
    class Accessors {

        @Test
        @DisplayName("toBytes returns a defensive copy")
        void toBytesIsDefensiveCopy() {
            Base64Blob b = new Base64Blob(new byte[] {0x0A, 0x0B, 0x0C});

            byte[] first = b.toBytes();
            first[0] = (byte) 0xFF;

            byte[] second = b.toBytes();
            assertThat(second).containsExactly(0x0A, 0x0B, 0x0C);
        }
    }
}