package io.github.certtool.keystorecore.load;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import io.github.certtool.testfixtures.security.LogCaptureExtension;
import io.github.certtool.testfixtures.security.LogCaptureExtension.LogCapture;
import java.util.Base64;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Security regression: per spec §2 the loader must NEVER log passwords, key material, full
 * Base64 keystores, or pasted sensitive input. These assertions catch accidental regressions
 * (e.g. a stack trace being logged with arguments, an exception message including the password,
 * or a debug-level keystore dump).
 */
@DisplayName("KeyStoreLoader security")
@ExtendWith(LogCaptureExtension.class)
class KeyStoreLoaderSecurityTest {

    private static final char[] STORE_PWD = "storepass".toCharArray();
    private static final char[] ENTRY_PWD = "entrypass".toCharArray();
    private static final char[] WRONG_PWD = "wrongpass".toCharArray();

    private static byte[] jksBytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jks(STORE_PWD, "k",
                        CertificateGenerator.rsaKeyPair(2048).getPrivate(),
                        ENTRY_PWD,
                        CertificateGenerator.selfSigned(
                                new X500Principal("CN=k"),
                                CertificateGenerator.rsaKeyPair(2048),
                                "SHA256withRSA",
                                java.time.Duration.ofDays(30))),
                STORE_PWD);
    }

    @Test
    @DisplayName("successful load leaks nothing sensitive")
    void successfulLoadIsClean(@LogCapture LogCaptureExtension.Capture capture) throws Exception {
        new KeyStoreLoader().load(jksBytes(), KeyStoreContainerType.JKS,
                new FixedPasswordProvider(STORE_PWD, Map.of("k", ENTRY_PWD)));

        capture.assertNoSensitiveMaterial();
    }

    @Test
    @DisplayName("wrong store password path leaks nothing sensitive")
    void wrongStorePasswordIsClean(@LogCapture LogCaptureExtension.Capture capture) throws Exception {
        new KeyStoreLoader().load(jksBytes(), KeyStoreContainerType.JKS,
                new FixedPasswordProvider(WRONG_PWD, null));

        capture.assertNoSensitiveMaterial();
    }

    @Test
    @DisplayName("wrong entry password path leaks nothing sensitive")
    void wrongEntryPasswordIsClean(@LogCapture LogCaptureExtension.Capture capture) throws Exception {
        KeyStoreLoadResult r = new KeyStoreLoader().load(jksBytes(), KeyStoreContainerType.JKS,
                new FixedPasswordProvider(STORE_PWD, Map.of("k", WRONG_PWD)));

        // Sanity check: we did hit the WRONG_ENTRY_PASSWORD branch (so the test is meaningful).
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.failure().reason()).isEqualTo(LoadFailureReason.WRONG_ENTRY_PASSWORD);
        capture.assertNoSensitiveMaterial();
    }

    @Test
    @DisplayName("Base64 auto-detection path leaks nothing sensitive")
    void base64AutoDetectIsClean(@LogCapture LogCaptureExtension.Capture capture) throws Exception {
        String b64 = Base64.getEncoder().encodeToString(jksBytes());
        new KeyStoreLoader().loadAutoDetect(b64.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                new FixedPasswordProvider(STORE_PWD, Map.of("k", ENTRY_PWD)));

        capture.assertNoSensitiveMaterial();
    }

    @Test
    @DisplayName("failure user-message does not echo store password")
    void failureUserMessageDoesNotEchoStorePassword(@LogCapture LogCaptureExtension.Capture capture)
            throws Exception {
        KeyStoreLoadResult r = new KeyStoreLoader().load(jksBytes(), KeyStoreContainerType.JKS,
                new FixedPasswordProvider(WRONG_PWD, null));

        String combined = (r.failure().userMessage() == null ? "" : r.failure().userMessage())
                + " | " + (r.failure().technicalReason() == null ? "" : r.failure().technicalReason());

        assertThat(combined)
                .doesNotContain(new String(WRONG_PWD))
                .doesNotContain(new String(STORE_PWD))
                .doesNotContain(new String(ENTRY_PWD));
    }
}
