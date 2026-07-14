package io.github.certtool.domain.inspect;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.domain.certificate.BasicConstraintsInfo;
import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.KeyUsageBits;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.math.BigInteger;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("InspectedKeyStore")
class InspectedKeyStoreTest {

    @Test
    @DisplayName("records are immutable and lists are defensively copied")
    void immutability() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        InspectedEntry entry = new InspectedEntry(
                "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                null, null, List.of(), List.of());
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> inspected.entries().add(entry));
    }

    @Test
    @DisplayName("empty-chain entry has zero certificates and carries the warnings list")
    void emptyChainEntry() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        InspectedEntry entry = new InspectedEntry(
                "k", EntryType.SECRET_KEY, new Date(), true,
                "AES", 256, List.of(), List.of("no certs"));
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        assertThat(inspected.entries()).hasSize(1);
        assertThat(inspected.entries().get(0).certificates()).isEmpty();
        assertThat(inspected.entries().get(0).warnings()).containsExactly("no certs");
    }

    @Test
    @DisplayName("entry with one certificate exposes that certificate's CertificateAnalysis")
    void singleCertEntry() {
        KeyStoreSummary summary = KeyStoreSummary.from(
                KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", List.of()),
                ContentEncoding.BINARY);
        ValidityWindow v = new ValidityWindow(Instant.now().minusSeconds(60), Instant.now().plusSeconds(60));
        CertificateAnalysis ca = new CertificateAnalysis(
                "CN=a", "CN=a", BigInteger.ONE, "01", "1",
                3, v, ValidityState.VALID, "SHA256withRSA", "1.2.3.4.5",
                new PublicKeyInfo(KeyAlgorithm.UNKNOWN, null, null, null, null),
                new ExtensionAnalysis(
                        BasicConstraintsInfo.absent(), KeyUsageBits.empty(),
                        List.of(), List.of(), List.of(),
                        null, null,
                        List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of()),
                new FingerprintBundle("", "", "", ""),
                new SelfSignedStatus(false, false),
                "");
        InspectedCertificate cert = new InspectedCertificate(0, ca);
        InspectedEntry entry = new InspectedEntry(
                "a", EntryType.TRUSTED_CERTIFICATE, new Date(), true,
                null, null, List.of(cert), List.of());
        InspectedKeyStore inspected = new InspectedKeyStore(summary, List.of(entry));

        assertThat(inspected.entries()).hasSize(1);
        assertThat(inspected.entries().get(0).certificates()).hasSize(1);
        assertThat(inspected.entries().get(0).certificates().get(0).chainIndex()).isZero();
        assertThat(inspected.entries().get(0).certificates().get(0).analysis()).isSameAs(ca);
    }

    /** Needed only to satisfy the LoadedEntry.trustedCertificate signature in non-test code paths. */
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

    private static LoadedEntry trustedCert(String alias, Certificate cert) {
        return LoadedEntry.trustedCertificate(alias, cert, new Date());
    }
}
