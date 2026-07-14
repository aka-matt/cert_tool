package io.github.certtool.app.password;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.keystorecore.password.EntryPasswordRequest;
import io.github.certtool.keystorecore.password.StorePasswordRequest;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("JavaFxPasswordProvider")
class JavaFxPasswordProviderTest {

    @Test
    @DisplayName("returns the password from the dialog factory for store password")
    void returnsStorePassword() {
        AtomicReference<String> seenTitle = new AtomicReference<>();
        AtomicReference<String> seenHeader = new AtomicReference<>();
        JavaFxPasswordProvider p = new JavaFxPasswordProvider((title, header) -> {
            seenTitle.set(title);
            seenHeader.set(header);
            return "secret".toCharArray();
        });
        char[] got = p.requestStorePassword(new StorePasswordRequest("source.jks", 1, 3));
        assertThat(got).isEqualTo("secret".toCharArray());
        assertThat(seenTitle.get()).isEqualTo("KeyStore Password");
        assertThat(seenHeader.get()).contains("source.jks");
        assertThat(seenHeader.get()).contains("attempt 1 of 3");
    }

    @Test
    @DisplayName("returns the password from the dialog factory for entry password")
    void returnsEntryPassword() {
        AtomicReference<String> seenHeader = new AtomicReference<>();
        JavaFxPasswordProvider p = new JavaFxPasswordProvider((title, header) -> {
            seenHeader.set(header);
            return "entry".toCharArray();
        });
        char[] got = p.requestEntryPassword(new EntryPasswordRequest(
                "source.jks", "aliasA", EntryType.PRIVATE_KEY, 2, 3));
        assertThat(got).isEqualTo("entry".toCharArray());
        assertThat(seenHeader.get()).contains("aliasA");
        assertThat(seenHeader.get()).contains("PRIVATE_KEY");
        assertThat(seenHeader.get()).contains("attempt 2 of 3");
    }

    @Test
    @DisplayName("returns null when the dialog factory returns null (user cancel)")
    void cancelReturnsNull() {
        JavaFxPasswordProvider p = new JavaFxPasswordProvider((title, header) -> null);
        assertThat(p.requestStorePassword(new StorePasswordRequest("src.jks", 1, 1))).isNull();
        assertThat(p.requestEntryPassword(new EntryPasswordRequest(
                "src.jks", "a", EntryType.PRIVATE_KEY, 1, 1))).isNull();
    }

    @Test
    @DisplayName("zero() wipes a non-null char[]; null is a no-op")
    void zeroWipesCharArray() {
        char[] pwd = "hunter2".toCharArray();
        JavaFxPasswordProvider.zero(pwd);
        for (char c : pwd) {
            assertThat(c).isEqualTo('\0');
        }
        JavaFxPasswordProvider.zero(null); // must not throw
    }

    @Test
    @DisplayName("does not log the password value (only the cancel decision)")
    void doesNotLeakPassword() {
        // Smoke check: provider must not throw or expose any output we can observe here.
        // The actual log scrubbing is asserted in LoggerNoSecretsTest (security suite).
        JavaFxPasswordProvider p = new JavaFxPasswordProvider((t, h) -> "x".toCharArray());
        p.requestStorePassword(new StorePasswordRequest("src", 1, 1));
        p.requestEntryPassword(new EntryPasswordRequest("src", "a", EntryType.PRIVATE_KEY, 1, 1));
    }
}