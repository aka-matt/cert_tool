package io.github.certtool.keystorecore.password;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.EntryType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link PasswordProvider} abstraction and the {@link FixedPasswordProvider} helper.
 *
 * <p>Spec §4 requires the loader to depend on an abstract password source so the UI layer is not
 * coupled to the loader, and so tests can supply a fake.
 */
@DisplayName("PasswordProvider")
class PasswordProviderTest {

    @Test
    @DisplayName("FixedPasswordProvider returns the configured store password")
    void fixedStore() {
        PasswordProvider p = new FixedPasswordProvider("storepwd".toCharArray(), null);

        char[] got = p.requestStorePassword(
                new StorePasswordRequest("test.jks", 1, 3));

        assertThat(got).isEqualTo("storepwd".toCharArray());
    }

    @Test
    @DisplayName("FixedPasswordProvider returns the configured entry password (per-alias)")
    void fixedEntry() {
        PasswordProvider p = new FixedPasswordProvider("storepwd".toCharArray(), java.util.Map.of(
                "leaf", "leafpwd".toCharArray()));

        char[] got = p.requestEntryPassword(
                new EntryPasswordRequest("test.jks", "leaf", EntryType.PRIVATE_KEY, 1, 3));

        assertThat(got).isEqualTo("leafpwd".toCharArray());
    }

    @Test
    @DisplayName("FixedPasswordProvider returns null when entry password is unknown — signals cancel")
    void fixedEntryUnknown() {
        PasswordProvider p = new FixedPasswordProvider("storepwd".toCharArray(), java.util.Map.of());

        char[] got = p.requestEntryPassword(
                new EntryPasswordRequest("test.jks", "unknown", EntryType.PRIVATE_KEY, 1, 3));

        assertThat(got).isNull();
    }

    @Test
    @DisplayName("FixedPasswordProvider returns null when store password is null — signals cancel")
    void fixedStoreNull() {
        PasswordProvider p = new FixedPasswordProvider(null, null);

        assertThat(p.requestStorePassword(new StorePasswordRequest("x", 1, 3))).isNull();
    }

    @Test
    @DisplayName("request records carry the source description and alias for UI rendering")
    void requestRecordsCarryContext() {
        StorePasswordRequest sp = new StorePasswordRequest("foo.jks", 2, 3);
        EntryPasswordRequest ep = new EntryPasswordRequest("foo.jks", "leaf", EntryType.PRIVATE_KEY, 2, 3);

        assertThat(sp.sourceDescription()).isEqualTo("foo.jks");
        assertThat(sp.attemptNumber()).isEqualTo(2);
        assertThat(sp.maxAttempts()).isEqualTo(3);
        assertThat(ep.alias()).isEqualTo("leaf");
        assertThat(ep.entryType()).isEqualTo(EntryType.PRIVATE_KEY);
    }
}