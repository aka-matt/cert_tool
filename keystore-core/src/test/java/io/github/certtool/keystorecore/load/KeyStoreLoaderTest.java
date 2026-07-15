package io.github.certtool.keystorecore.load;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.keystorecore.password.EntryPasswordRequest;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.keystorecore.password.StorePasswordRequest;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KeyStoreLoader")
class KeyStoreLoaderTest {

    private static final char[] STORE_PWD = "storepass".toCharArray();

    private static byte[] jksTrustedCertBytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                        new X500Principal("CN=t"),
                        CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA",
                        Duration.ofDays(30))),
                STORE_PWD);
    }

    private static byte[] jksPrivateKeyBytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jks(STORE_PWD, "p",
                        CertificateGenerator.rsaKeyPair(2048).getPrivate(),
                        "pp".toCharArray(),
                        CertificateGenerator.selfSigned(
                                new X500Principal("CN=p"),
                                CertificateGenerator.rsaKeyPair(2048),
                                "SHA256withRSA",
                                Duration.ofDays(30))),
                STORE_PWD);
    }

    private static byte[] bcfksBytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.bcfks(STORE_PWD, "t", CertificateGenerator.selfSigned(
                        new X500Principal("CN=t"),
                        CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA",
                        Duration.ofDays(30))),
                STORE_PWD);
    }

    private static byte[] pkcs12Bytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.pkcs12(STORE_PWD, "root", CertificateGenerator.selfSigned(
                        new X500Principal("CN=root"),
                        CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA",
                        Duration.ofDays(30))),
                STORE_PWD);
    }

    @Nested
    @DisplayName("JKS loading")
    class JksLoading {

        @Test
        @DisplayName("loads a JKS trusted-certificate entry")
        void loadsTrustedCert() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(jksTrustedCertBytes(), KeyStoreContainerType.JKS,
                    new FixedPasswordProvider(STORE_PWD, null));

            if (!r.isSuccess()) {
                throw new AssertionError("Expected success but got: " + r.failure().reason()
                        + " / " + r.failure().userMessage() + " / cause=" + r.failure().cause().orElse(null));
            }
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.JKS);
            assertThat(r.entries()).hasSize(1);
            assertThat(r.entries().get(0).alias()).isEqualTo("t");
            assertThat(r.entries().get(0).entryType())
                    .isEqualTo(io.github.certtool.domain.keystore.EntryType.TRUSTED_CERTIFICATE);
        }

        @Test
        @DisplayName("loads a JKS private-key entry when entry password is supplied")
        void loadsPrivateKey() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(jksPrivateKeyBytes(), KeyStoreContainerType.JKS,
                    new FixedPasswordProvider(STORE_PWD, Map.of("p", "pp".toCharArray())));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.entries().get(0).entryType())
                    .isEqualTo(io.github.certtool.domain.keystore.EntryType.PRIVATE_KEY);
        }

        @Test
        @DisplayName("fails with WRONG_STORE_PASSWORD when store password is wrong")
        void wrongStorePassword() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(jksTrustedCertBytes(), KeyStoreContainerType.JKS,
                    new FixedPasswordProvider("wrong".toCharArray(), null));

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.failure().reason()).isEqualTo(LoadFailureReason.WRONG_STORE_PASSWORD);
            assertThat(r.failure().technicalReason()).containsIgnoringCase("indeterminate");
        }

        @Test
        @DisplayName("fails with WRONG_ENTRY_PASSWORD when entry password is wrong")
        void wrongEntryPassword() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(jksPrivateKeyBytes(), KeyStoreContainerType.JKS,
                    new FixedPasswordProvider(STORE_PWD, Map.of("p", "nope".toCharArray())));

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.failure().reason()).isEqualTo(LoadFailureReason.WRONG_ENTRY_PASSWORD);
        }
    }

    @Nested
    @DisplayName("BCFKS loading")
    class BcfksLoading {

        @Test
        @DisplayName("loads a BCFKS trusted-certificate entry")
        void loadsBcfks() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(bcfksBytes(), KeyStoreContainerType.BCFKS,
                    new FixedPasswordProvider(STORE_PWD, null));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.BCFKS);
        }
    }

    @Nested
    @DisplayName("error classification")
    class Errors {

        @Test
        @DisplayName("fails with CORRUPTED_KEYSTORE for random bytes that match no magic")
        void corruptedOnGarbage() {
            byte[] garbage = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(garbage, KeyStoreContainerType.JKS,
                    new FixedPasswordProvider(STORE_PWD, null));

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.failure().reason()).isEqualTo(LoadFailureReason.CORRUPTED_KEYSTORE);
        }

        @Test
        @DisplayName("fails with CANCELLED when the password provider returns null")
        void cancelledOnNullStorePassword() throws Exception {
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.load(jksTrustedCertBytes(), KeyStoreContainerType.JKS,
                    new FixedPasswordProvider(null, null));

            assertThat(r.isSuccess()).isFalse();
            assertThat(r.failure().reason()).isEqualTo(LoadFailureReason.CANCELLED);
        }
    }

    @Nested
    @DisplayName("auto-detection")
    class AutoDetect {

        @Test
        @DisplayName("auto-detects JKS from raw bytes")
        void autoJks() throws Exception {
            byte[] bytes = jksTrustedCertBytes();
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.loadAutoDetect(bytes,
                    new FixedPasswordProvider(STORE_PWD, null));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.JKS);
        }

        @Test
        @DisplayName("auto-detects BCFKS from raw bytes")
        void autoBcfks() throws Exception {
            byte[] bytes = bcfksBytes();
            KeyStoreLoader loader = new KeyStoreLoader();
            CountingPasswordProvider passwords = new CountingPasswordProvider(STORE_PWD);
            KeyStoreLoadResult r = loader.loadAutoDetect(bytes, passwords);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.BCFKS);
            assertThat(passwords.storePasswordRequests).isEqualTo(1);
        }

        @Test
        @DisplayName("auto-detects PKCS12 trusted-certificate entry from raw bytes")
        void autoPkcs12TrustedCertificate() throws Exception {
            KeyStoreLoadResult r = new KeyStoreLoader().loadAutoDetect(
                    pkcs12Bytes(), new FixedPasswordProvider(STORE_PWD, Map.of()));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.PKCS12);
            assertThat(r.entries()).singleElement()
                    .extracting(entry -> entry.entryType())
                    .isEqualTo(io.github.certtool.domain.keystore.EntryType.TRUSTED_CERTIFICATE);
        }

        @Test
        @DisplayName("auto-detects Base64-encoded JKS")
        void autoJksBase64() throws Exception {
            String b64 = Base64.getMimeEncoder().encodeToString(jksTrustedCertBytes());
            KeyStoreLoader loader = new KeyStoreLoader();
            KeyStoreLoadResult r = loader.loadAutoDetect(b64.getBytes(StandardCharsets.UTF_8),
                    new FixedPasswordProvider(STORE_PWD, null));

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.container()).isEqualTo(KeyStoreContainerType.JKS);
        }
    }

    private static final class CountingPasswordProvider implements PasswordProvider {
        private final char[] storePassword;
        private int storePasswordRequests;

        private CountingPasswordProvider(char[] storePassword) {
            this.storePassword = storePassword.clone();
        }

        @Override
        public char[] requestStorePassword(StorePasswordRequest request) {
            storePasswordRequests++;
            return storePassword.clone();
        }

        @Override
        public char[] requestEntryPassword(EntryPasswordRequest request) {
            return null;
        }
    }
}
