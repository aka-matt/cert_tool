package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
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
import java.util.concurrent.atomic.AtomicInteger;
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
    @DisplayName("call() uniquely detects a real Base64 BCFKS keystore")
    void callUniquelyDetectsARealBase64BcfksKeyStore() throws Exception {
        char[] password = "source-password".toCharArray();
        X509Certificate certificate = CertificateGenerator.selfSigned(
                new X500Principal("CN=pasted-bcfks"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        java.security.KeyStore keyStore = KeyStoreGenerator.bcfks(password, "certificate", certificate);
        String encodedBcfks = Base64.getEncoder().encodeToString(KeyStoreGenerator.toBytes(keyStore, password));

        PasteBase64LoadTask task = new PasteBase64LoadTask(
                new KeyStoreLoader(), encodedBcfks, new FixedPasswordProvider(password, Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.BCFKS);
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
                LoadFailureReason.AMBIGUOUS_CONTAINER, "Could not determine the keystore container"));
    }

    @Test
    @DisplayName("call() reports unsupported format when neither container probe succeeds")
    void callReportsUnsupportedFormatWhenNeitherContainerProbeSucceeds() {
        PasteBase64LoadTask task = new PasteBase64LoadTask(
                (bytes, container, passwords) -> new KeyStoreLoadResult(
                        false,
                        null,
                        null,
                        null,
                        List.of(),
                        LoadFailure.of(LoadFailureReason.UNSUPPORTED_FORMAT, "unsupported")),
                Base64.getEncoder().encodeToString(new byte[] {1}),
                new FixedPasswordProvider(new char[0], Map.of()));

        KeyStoreLoadResult result = task.call();

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failure()).isEqualTo(LoadFailure.of(
                LoadFailureReason.UNSUPPORTED_FORMAT, "Could not determine the keystore container"));
    }

    @Test
    @DisplayName("call() requests the store password once and gives each probe a disposable copy")
    void callRequestsStorePasswordOnceAndGivesEachProbeADisposableCopy() {
        AtomicInteger storePasswordRequests = new AtomicInteger();
        List<char[]> suppliedPasswords = new ArrayList<>();
        io.github.certtool.keystorecore.password.PasswordProvider passwords =
                new io.github.certtool.keystorecore.password.PasswordProvider() {
                    @Override
                    public char[] requestStorePassword(
                            io.github.certtool.keystorecore.password.StorePasswordRequest request) {
                        storePasswordRequests.incrementAndGet();
                        return "store-password".toCharArray();
                    }

                    @Override
                    public char[] requestEntryPassword(
                            io.github.certtool.keystorecore.password.EntryPasswordRequest request) {
                        return null;
                    }
                };
        PasteBase64LoadTask task = new PasteBase64LoadTask(
                (bytes, container, probePasswords) -> {
                    char[] password = probePasswords.requestStorePassword(
                            new io.github.certtool.keystorecore.password.StorePasswordRequest("bytes", 1, 3));
                    suppliedPasswords.add(password);
                    java.util.Arrays.fill(password, '\0');
                    return new KeyStoreLoadResult(
                            false,
                            null,
                            null,
                            null,
                            List.of(),
                            LoadFailure.of(LoadFailureReason.UNSUPPORTED_FORMAT, "unsupported"));
                },
                Base64.getEncoder().encodeToString(new byte[] {1}),
                passwords);

        task.call();

        assertThat(storePasswordRequests).hasValue(1);
        assertThat(suppliedPasswords).hasSize(2);
        assertThat(suppliedPasswords.get(0)).isNotSameAs(suppliedPasswords.get(1));
        assertThat(suppliedPasswords).allSatisfy(password -> assertThat(password).containsOnly('\0'));
    }
}
