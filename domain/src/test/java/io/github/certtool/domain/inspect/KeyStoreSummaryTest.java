package io.github.certtool.domain.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.security.cert.Certificate;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KeyStoreSummary")
class KeyStoreSummaryTest {

    private static LoadedEntry trustedCert(String alias, Certificate cert) {
        return LoadedEntry.trustedCertificate(alias, cert, new Date());
    }

    private static LoadedEntry secretKey(String alias) {
        return new LoadedEntry(alias, EntryType.SECRET_KEY, new Date(),
                List.of(), "AES", 256, true, List.of());
    }

    @Test
    @DisplayName("counts entries by type and certificates across all chains")
    void countsByType() {
        Certificate c1 = new DummyCert();
        Certificate c2 = new DummyCert();
        Certificate c3 = new DummyCert();
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(trustedCert("a", c1), trustedCert("b", c2), secretKey("k")));

        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BINARY);

        assertThat(s.containerType()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(s.encoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(s.providerName()).isEqualTo("SUN");
        assertThat(s.providerVersion()).isEqualTo("17");
        assertThat(s.totalEntries()).isEqualTo(3);
        assertThat(s.totalCertificates()).isEqualTo(2);
        assertThat(s.entryCountsByType()).containsEntry(EntryType.TRUSTED_CERTIFICATE, 2)
                .containsEntry(EntryType.SECRET_KEY, 1)
                .containsEntry(EntryType.PRIVATE_KEY, 0)
                .containsEntry(EntryType.UNKNOWN, 0);
    }

    @Test
    @DisplayName("empty result produces zero counts and an empty counts map")
    void emptyResult() {
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.BCFKS, "BCFIPS", "1.0", List.of());

        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BASE64);

        assertThat(s.totalEntries()).isZero();
        assertThat(s.totalCertificates()).isZero();
        assertThat(s.entryCountsByType()).hasSize(4)
                .containsValues(0, 0, 0, 0);
    }

    @Test
    @DisplayName("entry counts map is immutable")
    void countsMapIsImmutable() {
        KeyStoreLoadResult result = KeyStoreLoadResult.success(
                KeyStoreContainerType.JKS, "SUN", "17",
                List.of(trustedCert("a", new DummyCert())));
        KeyStoreSummary s = KeyStoreSummary.from(result, ContentEncoding.BINARY);

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> s.entryCountsByType().put(EntryType.PRIVATE_KEY, 99));
    }

    /** Minimal Certificate stand-in; never inspected. */
    private static final class DummyCert extends Certificate {
        DummyCert() {
            super("X.509");
        }

        @Override public byte[] getEncoded() { return new byte[0]; }
        @Override public java.security.PublicKey getPublicKey() { return null; }
        @Override public void verify(java.security.PublicKey key) { }
        @Override public void verify(java.security.PublicKey key, String sigProvider) { }
        @Override public String toString() { return "DummyCert"; }
        @Override public int hashCode() { return 0; }
        @Override public boolean equals(Object other) { return other == this; }
    }
}
