package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
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

@DisplayName("AutoDetectKeyStoreLoadTask")
class AutoDetectKeyStoreLoadTaskTest {

    @Test
    @DisplayName("call() detects and loads BCFKS truststore bytes")
    void callDetectsAndLoadsBcfksTruststoreBytes() throws Exception {
        char[] password = "trustpass".toCharArray();
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=trust"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.bcfks(password, "root", cert), password);

        KeyStoreLoadResult result = new AutoDetectKeyStoreLoadTask(
                        new KeyStoreLoader(), bytes, new FixedPasswordProvider(password, Map.of()))
                .call();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.BCFKS);
        assertThat(result.entries()).singleElement().extracting(LoadedEntry::entryType).isEqualTo(EntryType.TRUSTED_CERTIFICATE);
    }

    @Test
    @DisplayName("call() detects and loads JKS truststore bytes")
    void callDetectsAndLoadsJksTruststoreBytes() throws Exception {
        char[] password = "trustpass".toCharArray();
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=jks-trust"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                Duration.ofDays(30));
        byte[] bytes = KeyStoreGenerator.toBytes(KeyStoreGenerator.jks(password, "root", cert), password);

        KeyStoreLoadResult result = new AutoDetectKeyStoreLoadTask(
                        new KeyStoreLoader(), bytes, new FixedPasswordProvider(password, Map.of()))
                .call();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.container()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(result.entries()).singleElement().extracting(LoadedEntry::entryType).isEqualTo(EntryType.TRUSTED_CERTIFICATE);
    }
}
