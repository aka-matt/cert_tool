package io.github.certtool.keystorecore.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import io.github.certtool.testfixtures.CertificateGenerator;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.UnrecoverableKeyException;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorClassifier")
class ErrorClassifierTest {

    private static final char[] PWD = "storepass".toCharArray();

    private static byte[] jksBytes() throws Exception {
        return KeyStoreGenerator.toBytes(
                KeyStoreGenerator.jks(PWD, "a", CertificateGenerator.selfSigned(
                        new X500Principal("CN=a"),
                        CertificateGenerator.rsaKeyPair(2048),
                        "SHA256withRSA",
                        java.time.Duration.ofDays(30))),
                PWD);
    }

    @Nested
    @DisplayName("classifyStoreLoadFailure")
    class Store {

        @Test
        @DisplayName("UnrecoverableKeyException → WRONG_STORE_PASSWORD (JKS integrity check)")
        void unrecoverableIsWrongStorePassword() throws Exception {
            LoadFailureReason r = ErrorClassifier.classifyStoreLoadFailure(
                    new UnrecoverableKeyException("nope"),
                    KeyStoreContainerType.JKS, jksBytes());
            assertThat(r).isEqualTo(LoadFailureReason.WRONG_STORE_PASSWORD);
        }

        @Test
        @DisplayName("Unknown magic on claimed JKS → CORRUPTED_KEYSTORE")
        void unknownMagicIsCorrupted() {
            byte[] garbage = new byte[] {0, 1, 2, 3, 4, 5};
            LoadFailureReason r = ErrorClassifier.classifyStoreLoadFailure(
                    new IOException("bad magic"),
                    KeyStoreContainerType.JKS, garbage);
            assertThat(r).isEqualTo(LoadFailureReason.CORRUPTED_KEYSTORE);
        }

        @Test
        @DisplayName("Magic matches expected container + IOException → WRONG_STORE_PASSWORD")
        void magicMatchesIsWrongStorePassword() throws Exception {
            LoadFailureReason r = ErrorClassifier.classifyStoreLoadFailure(
                    new IOException("integrity check failed"),
                    KeyStoreContainerType.JKS, jksBytes());
            assertThat(r).isEqualTo(LoadFailureReason.WRONG_STORE_PASSWORD);
        }

        @Test
        @DisplayName("Magic says BCFKS but caller claimed JKS → CORRUPTED_KEYSTORE")
        void magicMismatchIsCorrupted() throws Exception {
            byte[] bcfks = KeyStoreGenerator.toBytes(
                    KeyStoreGenerator.bcfks(PWD, "a", CertificateGenerator.selfSigned(
                            new X500Principal("CN=a"),
                            CertificateGenerator.rsaKeyPair(2048),
                            "SHA256withRSA",
                            java.time.Duration.ofDays(30))),
                    PWD);
            LoadFailureReason r = ErrorClassifier.classifyStoreLoadFailure(
                    new IOException("provider mismatch"),
                    KeyStoreContainerType.JKS, bcfks);
            assertThat(r).isEqualTo(LoadFailureReason.CORRUPTED_KEYSTORE);
        }

        @Test
        @DisplayName("GeneralSecurityException → PROVIDER_UNAVAILABLE")
        void gseIsProviderUnavailable() {
            LoadFailureReason r = ErrorClassifier.classifyStoreLoadFailure(
                    new GeneralSecurityException("provider missing"),
                    KeyStoreContainerType.JKS, new byte[] {1, 2, 3, 4});
            assertThat(r).isEqualTo(LoadFailureReason.PROVIDER_UNAVAILABLE);
        }
    }

    @Nested
    @DisplayName("classifyEntryLoadFailure")
    class Entry {

        @Test
        @DisplayName("UnrecoverableKeyException → WRONG_ENTRY_PASSWORD")
        void unrecoverableIsWrongEntryPassword() {
            LoadFailureReason r = ErrorClassifier.classifyEntryLoadFailure(
                    new UnrecoverableKeyException("entry pwd"));
            assertThat(r).isEqualTo(LoadFailureReason.WRONG_ENTRY_PASSWORD);
        }

        @Test
        @DisplayName("Other GeneralSecurityException → UNSUPPORTED_ENTRY")
        void otherGseIsUnsupportedEntry() {
            LoadFailureReason r = ErrorClassifier.classifyEntryLoadFailure(
                    new GeneralSecurityException("weird key"));
            assertThat(r).isEqualTo(LoadFailureReason.UNSUPPORTED_ENTRY);
        }
    }
}
