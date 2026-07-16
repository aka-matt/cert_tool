package io.github.certtool.app.compliance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ExtensionAnalysis;
import io.github.certtool.domain.certificate.FingerprintBundle;
import io.github.certtool.domain.certificate.KeyAlgorithm;
import io.github.certtool.domain.certificate.PublicKeyInfo;
import io.github.certtool.domain.certificate.SelfSignedStatus;
import io.github.certtool.domain.certificate.ValidityState;
import io.github.certtool.domain.certificate.ValidityWindow;
import io.github.certtool.domain.context.EntryAnalysis;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import io.github.certtool.domain.inspect.InspectedCertificate;
import io.github.certtool.domain.inspect.InspectedEntry;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.inspect.KeyStoreSummary;
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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleContextFactory")
class RuleContextFactoryTest {

    private static KeyStoreSummary summary(KeyStoreContainerType c) {
        return new KeyStoreSummary(c, ContentEncoding.BINARY, "SUN", "17",
                Map.of(EntryType.PRIVATE_KEY, 0,
                       EntryType.TRUSTED_CERTIFICATE, 1,
                       EntryType.SECRET_KEY, 0,
                       EntryType.UNKNOWN, 0),
                1, 1);
    }

    private static CertificateAnalysis leafAnalysis() {
        ValidityWindow w = new ValidityWindow(
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2027-01-01T00:00:00Z"));
        FingerprintBundle fp = new FingerprintBundle("aa", "bb", "AA", "BB");
        PublicKeyInfo pki = new PublicKeyInfo(KeyAlgorithm.RSA, 2048, null, null, null);
        ExtensionAnalysis ext = new ExtensionAnalysis(
                io.github.certtool.domain.certificate.BasicConstraintsInfo.absent(),
                io.github.certtool.domain.certificate.KeyUsageBits.empty(),
                List.of(), List.of(), List.of(),
                null, null,
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        return new CertificateAnalysis(
                "CN=leaf", "CN=root", BigInteger.ONE, "01", "1",
                3, w, ValidityState.VALID, "SHA256withRSA", "1.2.840.113549.1.1.11",
                pki, ext, fp, new SelfSignedStatus(false, false),
                "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----\n");
    }

    private static InspectedKeyStore inspected(String alias, EntryType type,
                                               CertificateAnalysis analysisOrNull) {
        List<InspectedCertificate> chain = analysisOrNull == null
                ? List.of()
                : List.of(new InspectedCertificate(0, analysisOrNull));
        InspectedEntry e = new InspectedEntry(alias, type, new Date(), true,
                type == EntryType.PRIVATE_KEY ? "RSA" : null,
                type == EntryType.PRIVATE_KEY ? 2048 : null,
                chain, List.of());
        return new InspectedKeyStore(summary(KeyStoreContainerType.JKS), List.of(e));
    }

    private static KeyStoreLoadResult load(List<LoadedEntry> entries) {
        return KeyStoreLoadResult.success(KeyStoreContainerType.JKS, "SUN", "17", entries);
    }

    private static RuntimeEnvironment runtime() {
        return new RuntimeEnvironment("Temurin", "17", "Linux", "amd64",
                List.of(new ProviderInfo("SUN", "17", "SUN", false, false)),
                Instant.parse("2026-07-15T00:00:00Z"));
    }

    @Test
    @DisplayName("maps a successful load + inspected keystore into a populated RuleContext")
    void mapsSuccessfulLoad() {
        CertificateAnalysis leaf = leafAnalysis();
        InspectedKeyStore ins = inspected("alias-1", EntryType.TRUSTED_CERTIFICATE, leaf);
        KeyStoreLoadResult res = load(List.of(
                LoadedEntry.trustedCertificate("alias-1", new DummyCert(), new Date())));
        var rt = runtime();

        var ctx = RuleContextFactory.from(res, ins, ContentEncoding.BINARY,
                "/tmp/a.jks", 1024L, rt);

        LoadedKeyStoreInfo info = ctx.loadedKeyStore();
        assertThat(info.containerType()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(info.encoding()).isEqualTo(ContentEncoding.BINARY);
        assertThat(info.sourcePath()).isEqualTo("/tmp/a.jks");
        assertThat(info.sizeBytes()).isEqualTo(1024L);
        assertThat(info.integrityCheckPassed()).isTrue();
        assertThat(info.aliases()).containsExactly("alias-1");

        List<EntryAnalysis> entries = ctx.entries();
        assertThat(entries).hasSize(1);
        EntryAnalysis ea = entries.get(0);
        assertThat(ea.alias()).isEqualTo("alias-1");
        assertThat(ea.entryType()).isEqualTo(EntryType.TRUSTED_CERTIFICATE);
        assertThat(ea.certificate()).isSameAs(leaf);
        assertThat(ea.chain()).isNull();
        assertThat(ea.publicKeySha256Hex()).isNull();
        assertThat(ctx.runtime()).isSameAs(rt);
    }

    @Test
    @DisplayName("null sourcePath is preserved for paste / in-memory keystores")
    void nullSourcePathPreserved() {
        InspectedKeyStore ins = inspected("k", EntryType.SECRET_KEY, null);
        KeyStoreLoadResult res = load(List.of(
                new LoadedEntry("k", EntryType.SECRET_KEY, new Date(),
                        List.of(), "AES", 256, true, List.of())));
        var ctx = RuleContextFactory.from(res, ins, ContentEncoding.BASE64,
                null, 0L, runtime());
        assertThat(ctx.loadedKeyStore().sourcePath()).isNull();
        assertThat(ctx.entries().get(0).certificate()).isNull();
    }

    /** Minimal X.509 stand-in — only the type matters for these factory tests. */
    private static final class DummyCert extends Certificate {
        private static final long serialVersionUID = 1L;
        DummyCert() { super("X.509"); }
        @Override public byte[] getEncoded() { return new byte[0]; }
        @Override public void verify(java.security.PublicKey key) { }
        @Override public void verify(java.security.PublicKey key, String sigProvider) { }
        @Override public java.security.PublicKey getPublicKey() { return null; }
        @Override public String toString() { return "DummyCert"; }
    }

    @Test
    @DisplayName("rejects failed load, null inspected, null runtime, null encoding")
    void rejectsBadInputs() {
        InspectedKeyStore ins = inspected("a", EntryType.TRUSTED_CERTIFICATE, leafAnalysis());
        KeyStoreLoadResult ok = load(List.of(
                LoadedEntry.trustedCertificate("a", new DummyCert(), new Date())));
        KeyStoreLoadResult bad = KeyStoreLoadResult.failure(
                io.github.certtool.domain.load.KeyFailure.of(
                        io.github.certtool.domain.error.LoadFailureReason.CORRUPTED_KEYSTORE, "x"));
        assertThatThrownBy(() -> RuleContextFactory.from(bad, ins, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(null, ins, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, null, ContentEncoding.BINARY, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, ins, null, null, 0L, runtime()))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> RuleContextFactory.from(ok, ins, ContentEncoding.BINARY, null, 0L, null))
                .isInstanceOf(NullPointerException.class);
    }
}