package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PasteBase64LoadTask")
class PasteBase64LoadTaskTest {

    @Test
    @DisplayName("call() decodes Base64 and loads the only matching container")
    void callDecodesBase64AndLoadsTheOnlyMatchingContainer() throws Exception {
        char[] password = "source-password".toCharArray();
        X509Certificate certificate = CertificateGenerator.selfSigned(
                new X500Principal("CN=pasted"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        java.security.KeyStore keyStore = KeyStoreGenerator.jks(password, "certificate", certificate);
        String encodedJks = Base64.getEncoder().encodeToString(KeyStoreGenerator.toBytes(keyStore, password));

        PasteBase64LoadTask task = new PasteBase64LoadTask(
                new KeyStoreLoader(), encodedJks, new FixedPasswordProvider(password, Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.JKS);
    }

    @Test
    @DisplayName("call() rejects invalid Base64 without echoing the input")
    void callRejectsInvalidBase64WithoutEchoingTheInput() {
        PasteBase64LoadTask task = new PasteBase64LoadTask(
                new KeyStoreLoader(), "not@base64", new FixedPasswordProvider(new char[0], Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(result.isSuccess()).isFalse();
        assertThat(task.getMessage()).doesNotContain("not@base64");
    }

    @Test
    @DisplayName("call() reports an indeterminate result when both container probes succeed")
    void callReportsIndeterminateResultWhenBothContainerProbesSucceed() {
        List<KeyStoreContainerType> probedContainers = new ArrayList<>();
        PasteBase64LoadTask task = new PasteBase64LoadTask(
                (bytes, container, passwords) -> {
                    probedContainers.add(container);
                    return KeyStoreLoadResult.success(container, "test", "1", List.of());
                },
                Base64.getEncoder().encodeToString(new byte[] {1}),
                new FixedPasswordProvider(new char[0], Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(probedContainers).containsExactly(KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(LoadFailure.of(
                LoadFailureReason.UNSUPPORTED_FORMAT, "Could not determine the keystore container"));
    }
}
