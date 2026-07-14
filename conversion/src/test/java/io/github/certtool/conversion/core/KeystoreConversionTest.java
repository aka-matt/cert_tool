package io.github.certtool.conversion.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.password.FixedPasswordProvider;
import io.github.certtool.testfixtures.CertificateGenerator;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("KeystoreConversion")
class KeystoreConversionTest {

    private static void saveKeyStore(Path path, java.security.KeyStore ks, char[] pwd) throws Exception {
        byte[] bytes = KeyStoreGenerator.toBytes(ks, pwd);
        Files.write(path, bytes);
    }

    @Test
    @DisplayName("JKS → BCFKS end-to-end: writes new file, source unchanged, aliases preserved")
    void jksToBcfksEndToEnd(@TempDir Path dir) throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=leaf"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        char[] srcPwd = "src".toCharArray();
        java.security.KeyStore source = KeyStoreGenerator.jks(srcPwd, "a", cert);
        saveKeyStore(dir.resolve("source.jks"), source, srcPwd);

        byte[] srcBytes = Files.readAllBytes(dir.resolve("source.jks"));
        String sourceSha = sha256Hex(srcBytes);

        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                dir.resolve("source.jks").toString(),
                dir.resolve("target.bcfks").toString(),
                srcPwd, "tgt".toCharArray(),
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of());

        ConversionResult result = KeystoreConversion.execute(
                plan,
                DefaultProfiles.loadFips1403(),
                new FixedPasswordProvider(srcPwd, Map.of()));

        assertThat(Files.exists(dir.resolve("target.bcfks"))).isTrue();
        // Source file untouched.
        assertThat(sha256Hex(Files.readAllBytes(dir.resolve("source.jks")))).isEqualTo(sourceSha);
        assertThat(result.verification().matchesSource()).isTrue();
        assertThat(result.verification().sourceAliasCount()).isEqualTo(1);
        assertThat(result.verification().targetAliasCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Target exists + FAIL_IF_EXISTS: refuses to write; source unchanged")
    void refusesWhenTargetExists(@TempDir Path dir) throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=leaf"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        char[] srcPwd = "src".toCharArray();
        java.security.KeyStore source = KeyStoreGenerator.jks(srcPwd, "a", cert);
        saveKeyStore(dir.resolve("source.jks"), source, srcPwd);

        Files.createFile(dir.resolve("target.bcfks"));

        byte[] srcBefore = Files.readAllBytes(dir.resolve("source.jks"));
        String sourceSha = sha256Hex(srcBefore);

        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                dir.resolve("source.jks").toString(),
                dir.resolve("target.bcfks").toString(),
                srcPwd, "tgt".toCharArray(),
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of());

        PreflightBlockedException ex = null;
        try {
            KeystoreConversion.execute(plan, DefaultProfiles.loadFips1403(),
                    new FixedPasswordProvider(srcPwd, Map.of()));
        } catch (PreflightBlockedException e) {
            ex = e;
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
        assertThat(ex).isNotNull();
        assertThat(ex.report().findings())
                .extracting(f -> f.code())
                .contains("TARGET_FILE_EXISTS");
        // Source file unchanged.
        assertThat(sha256Hex(Files.readAllBytes(dir.resolve("source.jks")))).isEqualTo(sourceSha);
    }

    @Test
    @DisplayName("BCFKS→JKS private key + FIPS profile: preflight blocks, no write happens")
    void bcfksToJksBlockedByFips(@TempDir Path dir) throws Exception {
        X509Certificate cert = CertificateGenerator.selfSigned(
                new X500Principal("CN=leaf"),
                CertificateGenerator.rsaKeyPair(2048),
                "SHA256withRSA",
                java.time.Duration.ofDays(30));
        char[] srcPwd = "src".toCharArray();
        char[] entryPwd = new char[0]; // empty entry pwd so probeEntryTypes can recover the key
        java.security.KeyStore source = KeyStoreGenerator.bcfks(
                srcPwd, "a",
                CertificateGenerator.rsaKeyPair(2048).getPrivate(),
                entryPwd,
                cert);
        saveKeyStore(dir.resolve("source.bcfks"), source, srcPwd);

        Path target = dir.resolve("target.jks");
        ConversionPlan plan = new ConversionPlan(
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                dir.resolve("source.bcfks").toString(),
                target.toString(),
                srcPwd, "tgt".toCharArray(),
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of(entryPwd));

        Profile fips = DefaultProfiles.loadFips1403();

        PreflightBlockedException ex = null;
        try {
            KeystoreConversion.execute(plan, fips,
                    new FixedPasswordProvider(srcPwd, Map.of("a", entryPwd)));
        } catch (PreflightBlockedException e) {
            ex = e;
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception", e);
        }
        assertThat(ex).isNotNull();
        PreflightReport report = ex.report();
        assertThat(report.hasBlockers()).isTrue();
        assertThat(report.findings())
                .extracting(f -> f.code())
                .contains("BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE");
        assertThat(report.findings().stream()
                .filter(f -> f.code().equals("BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE"))
                .findFirst()
                .map(f -> f.severity())
                .orElseThrow())
                .isEqualTo(PreflightSeverity.BLOCK);
        assertThat(Files.exists(target)).isFalse();
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(bytes));
    }
}