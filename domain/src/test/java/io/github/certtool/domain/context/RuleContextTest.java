package io.github.certtool.domain.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.domain.certificate.CertificateAnalysis;
import io.github.certtool.domain.certificate.ChainAnalysis;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuleContext")
class RuleContextTest {

    private static EntryAnalysis entry(String alias, EntryType type) {
        return new EntryAnalysis(alias, type, (CertificateAnalysis) null, (ChainAnalysis) null, null);
    }

    private static RuntimeEnvironment runtime() {
        return new RuntimeEnvironment(
                "Eclipse Adoptium",
                "17.0.11",
                "Linux",
                "amd64",
                List.of(new ProviderInfo("BCFIPS", "2.0.0", "FIPS", true, true)),
                Instant.parse("2026-07-14T00:00:00Z"));
    }

    @Test
    @DisplayName("constructs with all required fields")
    void constructs() {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS,
                ContentEncoding.BINARY,
                "/tmp/x.jks",
                1024L,
                false,
                List.of("a"));
        RuleContext ctx = new RuleContext(ks, List.of(entry("a", EntryType.TRUSTED_CERTIFICATE)), runtime());
        assertThat(ctx.loadedKeyStore().containerType()).isEqualTo(KeyStoreContainerType.JKS);
        assertThat(ctx.entries()).hasSize(1);
        assertThat(ctx.runtime().bcfipsDetected()).isTrue();
    }

    @Test
    @DisplayName("rejects null runtime")
    void rejectsNullRuntime() {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY, "/tmp/x.jks", 0L, false, List.of());
        assertThatThrownBy(() -> new RuleContext(ks, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("runtime");
    }

    @Test
    @DisplayName("hasPrivateKeys true if any entry is a private key")
    void hasPrivateKeysDerives() {
        LoadedKeyStoreInfo ks = new LoadedKeyStoreInfo(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY, "/tmp/x.jks", 0L, true, List.of("a", "b"));
        RuleContext ctx = new RuleContext(
                ks,
                List.of(entry("a", EntryType.PRIVATE_KEY), entry("b", EntryType.TRUSTED_CERTIFICATE)),
                runtime());
        assertThat(ctx.hasPrivateKeys()).isTrue();
    }
}