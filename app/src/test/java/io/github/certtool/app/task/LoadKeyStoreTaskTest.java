package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LoadKeyStoreTask")
class LoadKeyStoreTaskTest {

    @Test
    @DisplayName("call() returns a successful result for a valid JKS")
    void callReturnsSuccess() throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=leaf"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        char[] pwd = "src".toCharArray();
        java.security.KeyStore ks = KeyStoreGenerator.jks(pwd, "a", cert);
        byte[] bytes = KeyStoreGenerator.toBytes(ks, pwd);

        LoadKeyStoreTask task = new LoadKeyStoreTask(
                new KeyStoreLoader(),
                bytes,
                KeyStoreContainerType.JKS,
                new FixedPasswordProvider(pwd, Map.of()));
        KeyStoreLoadResult result = task.call();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(result.entries()).hasSize(1);
        // Message broadcast is best-effort; we don't assert on getMessage() in headless tests
        // because Task.updateMessage needs the FX toolkit. The task body still runs and the
        // result is returned to the caller.
    }

    @Test
    @DisplayName("call() reports failure for empty bytes")
    void callReportsEmptyFailure() throws Exception {
        LoadKeyStoreTask task = new LoadKeyStoreTask(
                new KeyStoreLoader(),
                new byte[0],
                KeyStoreContainerType.JKS,
                new FixedPasswordProvider("x".toCharArray(), Map.of()));
        KeyStoreLoadResult result = task.call();
        assertThat(result.isSuccess()).isFalse();
    }
}