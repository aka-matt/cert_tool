package io.github.certtool.conversion.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EntryCopy")
class EntryCopyTest {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static X509Certificate cert() {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        return CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));
    }

    private static KeyStore newTarget(KeyStoreContainerType type, char[] pwd) throws Exception {
        KeyStore ks;
        if (type == KeyStoreContainerType.BCFKS) {
            ks = KeyStore.getInstance(type.name(), BouncyCastleProvider.PROVIDER_NAME);
        } else {
            ks = KeyStore.getInstance(type.name());
        }
        ks.load(null, pwd);
        return ks;
    }

    private static ConversionPlan plan(
            KeyStoreContainerType src,
            KeyStoreContainerType tgt,
            char[] srcPwd,
            char[] tgtPwd,
            List<String> aliases,
            List<char[]> entryPwds) {
        return new ConversionPlan(
                src, ContentEncoding.BINARY,
                tgt, ContentEncoding.BINARY,
                "/tmp/dummy-src", "/tmp/dummy-tgt",
                srcPwd, tgtPwd,
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                aliases, entryPwds);
    }

    @Test
    @DisplayName("Trusted-cert entries: copy alias + certificate matches exactly")
    void trustedCertsCopied() throws Exception {
        X509Certificate cert = cert();
        char[] srcPwd = "src".toCharArray();
        KeyStore source = KeyStoreGenerator.jks(srcPwd, "a", cert);

        char[] tgtPwd = "tgt".toCharArray();
        KeyStore target = newTarget(KeyStoreContainerType.JKS, tgtPwd);

        ConversionPlan p = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.JKS,
                srcPwd, tgtPwd,
                List.of("a"), List.of());
        EntryCopy.copy(source, target, p, new FixedPasswordProvider(srcPwd, Map.of()), List.of("a"));

        assertThat(Collections.list(target.aliases())).containsExactly("a");
        Certificate restored = target.getCertificate("a");
        assertThat(restored).isNotNull();
        assertThat(Arrays.equals(restored.getEncoded(), cert.getEncoded())).isTrue();
    }

    @Test
    @DisplayName("JKS → BCFKS preserves private key + certificate")
    void jksToBcfksPreservesPrivateKey() throws Exception {
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate leaf = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));

        char[] srcPwd = "src".toCharArray();
        char[] entryPwd = "entry".toCharArray();
        KeyStore source = KeyStoreGenerator.jksBuilder(srcPwd)
                .addPrivateKey("k", kp.getPrivate(), entryPwd, java.util.List.of(leaf))
                .build();

        char[] tgtPwd = "tgt".toCharArray();
        KeyStore target = newTarget(KeyStoreContainerType.BCFKS, tgtPwd);

        ConversionPlan p = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS,
                srcPwd, tgtPwd,
                List.of("k"),
                List.of(entryPwd));
        EntryCopy.copy(source, target, p, new FixedPasswordProvider(srcPwd, Map.of("k", entryPwd)), List.of("k"));

        assertThat(Collections.list(target.aliases())).containsExactly("k");
        Key restoredKey = target.getKey("k", tgtPwd.clone());
        assertThat(restoredKey.getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
    }

    @Test
    @DisplayName("JKS → BCFKS: store-password-only entry password still copies private key")
    void jksToBcfksWithEmptyPerEntryPasswordFallsBackToStorePassword() throws Exception {
        // Reproduces the wizard path: the user supplies only the source STORE password; no
        // per-entry override is set, so plan.entryPasswords() contains an empty char[].
        // JKS uses store==entry convention; EntryCopy must use the store password to recover the
        // private key for type detection AND for the actual setKeyEntry call.
        KeyPair kp = CertificateGenerator.rsaKeyPair(2048);
        X509Certificate leaf = CertificateGenerator.selfSigned(
                new X500Principal("CN=test"), kp, "SHA256withRSA", Duration.ofDays(30));

        char[] sharedPwd = "secret".toCharArray();
        KeyStore source = KeyStoreGenerator.jksBuilder(sharedPwd)
                .addPrivateKey("k", kp.getPrivate(), sharedPwd, java.util.List.of(leaf))
                .build();

        char[] tgtPwd = "tgt".toCharArray();
        KeyStore target = newTarget(KeyStoreContainerType.BCFKS, tgtPwd);

        ConversionPlan p = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS,
                sharedPwd, tgtPwd,
                List.of("k"),
                List.of(new char[0])); // wizard path: empty per-entry override
        EntryCopy.CopyResult result = EntryCopy.copy(
                source, target, p,
                new FixedPasswordProvider(sharedPwd, Map.of()), List.of("k"));

        assertThat(Collections.list(target.aliases())).containsExactly("k");
        Key restoredKey = target.getKey("k", tgtPwd.clone());
        assertThat(restoredKey.getEncoded()).isEqualTo(kp.getPrivate().getEncoded());
        assertThat(result.copied()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(0);
    }

    @Test
    @DisplayName("Included aliases only: excluded entries are not copied")
    void excludedEntriesNotCopied() throws Exception {
        X509Certificate cert = cert();
        char[] srcPwd = "src".toCharArray();
        KeyStore source = KeyStoreGenerator.jksBuilder(srcPwd)
                .addTrustedCertificate("keep", cert)
                .addTrustedCertificate("drop", cert)
                .build();

        char[] tgtPwd = "tgt".toCharArray();
        KeyStore target = newTarget(KeyStoreContainerType.JKS, tgtPwd);

        ConversionPlan p = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.JKS,
                srcPwd, tgtPwd,
                List.of("keep"), List.of());
        EntryCopy.copy(source, target, p, new FixedPasswordProvider(srcPwd, Map.of()), List.of("keep"));

        assertThat(Collections.list(target.aliases())).containsExactly("keep");
    }
}