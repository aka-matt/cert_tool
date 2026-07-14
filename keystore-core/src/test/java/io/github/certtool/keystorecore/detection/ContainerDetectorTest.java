package io.github.certtool.keystorecore.detection;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.time.Duration;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ContainerDetector")
class ContainerDetectorTest {

    /** JKS magic number (little-endian) followed by version 2. */
    private static byte[] jksMagicHeader() {
        return new byte[] {
            (byte) 0xFE, (byte) 0xED, (byte) 0xFE, (byte) 0xED, 0x02, 0x00, 0x00, 0x00
        };
    }

    /** Bytes that don't match any keystore magic. */
    private static byte[] garbageBytes() {
        return new byte[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07};
    }

    @Test
    @DisplayName("detects JKS by magic number")
    void detectsJks() {
        assertThat(ContainerDetector.detectContainer(jksMagicHeader())).isEqualTo(KeyStoreContainerType.JKS);
    }

    @Test
    @DisplayName("detects BCFKS by magic number")
    void detectsBcfks() throws Exception {
        KeyStore bcfks =
                KeyStoreGenerator.bcfks("x".toCharArray(), "t", CertificateGenerator.selfSigned(
                        new X500Principal("CN=t"), CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA", Duration.ofDays(30)));
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        bcfks.store(sink, "x".toCharArray());

        assertThat(ContainerDetector.detectContainer(sink.toByteArray()))
                .isEqualTo(KeyStoreContainerType.BCFKS);
    }

    @Test
    @DisplayName("returns null for bytes that match no known container")
    void noMatch() {
        assertThat(ContainerDetector.detectContainer(garbageBytes())).isNull();
    }

    @Test
    @DisplayName("returns null for empty input")
    void empty() {
        assertThat(ContainerDetector.detectContainer(new byte[0])).isNull();
    }

    @Test
    @DisplayName("returns null for input shorter than the magic-header length")
    void tooShort() {
        assertThat(ContainerDetector.detectContainer(new byte[] {0x01, 0x02})).isNull();
    }

    @Test
    @DisplayName("rejects null input")
    void nullInput() {
        assertThat(ContainerDetector.detectContainer(null)).isNull();
    }

    @Test
    @DisplayName("does not mis-detect ASCII text (e.g., a plain-text file) as JKS")
    void rejectsAsciiText() {
        byte[] ascii = "This is plain text".getBytes(StandardCharsets.UTF_8);
        assertThat(ContainerDetector.detectContainer(ascii)).isNull();
    }
}