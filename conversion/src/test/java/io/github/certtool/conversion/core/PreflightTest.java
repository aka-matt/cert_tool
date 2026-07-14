package io.github.certtool.conversion.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Preflight")
class PreflightTest {

    private static ConversionPlan plan(
            KeyStoreContainerType srcType,
            KeyStoreContainerType tgtType,
            String srcPath,
            String tgtPath,
            OverwritePolicy overwrite,
            char[] tgtStorePwd) {
        return new ConversionPlan(
                srcType,
                ContentEncoding.BINARY,
                tgtType,
                ContentEncoding.BINARY,
                srcPath,
                tgtPath,
                "src".toCharArray(),
                tgtStorePwd,
                AliasConflictPolicy.SKIP,
                overwrite,
                List.of("a"),
                List.of());
    }

    @Test
    @DisplayName("BCFKS→JKS private key + FIPS profile: BLOCK")
    void bcfksToJksPrivateKeyFipsProfileBlocks(@TempDir Path dir) throws IOException {
        String src = dir.resolve("src.bcfks").toString();
        // Just any KeyStore path — Preflight doesn't open the source, only reads plan + entries.
        Files.createFile(Path.of(src));
        ConversionPlan plan = plan(
                KeyStoreContainerType.BCFKS, KeyStoreContainerType.JKS,
                src, dir.resolve("tgt.jks").toString(),
                OverwritePolicy.FAIL_IF_EXISTS, "target".toCharArray());
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.PRIVATE_KEY);

        Profile fips1403 = DefaultProfiles.loadFips1403();
        PreflightReport report = Preflight.check(plan, entries, fips1403);

        assertThat(report.hasBlockers()).isTrue();
        assertThat(report.findings())
                .extracting(PreflightFinding::code)
                .contains("BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE");
        assertThat(report.blockerCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("BCFKS→JKS private key + no FIPS profile: WARN (no block)")
    void bcfksToJksPrivateKeyNoProfileWarns(@TempDir Path dir) throws IOException {
        String src = dir.resolve("src.bcfks").toString();
        Files.createFile(Path.of(src));
        ConversionPlan plan = plan(
                KeyStoreContainerType.BCFKS, KeyStoreContainerType.JKS,
                src, dir.resolve("tgt.jks").toString(),
                OverwritePolicy.FAIL_IF_EXISTS, "target".toCharArray());
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.PRIVATE_KEY);

        PreflightReport report = Preflight.check(plan, entries, null);

        assertThat(report.hasBlockers()).isFalse();
        assertThat(report.findings())
                .extracting(PreflightFinding::code)
                .contains("BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE");
        assertThat(report.findings())
                .filteredOn(f -> f.code().equals("BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE"))
                .extracting(PreflightFinding::severity)
                .containsOnly(PreflightSeverity.WARN);
    }

    @Test
    @DisplayName("Empty target password + BCFKS target: BLOCK")
    void emptyTgtPasswordOnBcfksBlocks(@TempDir Path dir) throws IOException {
        String src = dir.resolve("src.bcfks").toString();
        Files.createFile(Path.of(src));
        ConversionPlan plan = plan(
                KeyStoreContainerType.BCFKS, KeyStoreContainerType.BCFKS,
                src, dir.resolve("tgt.bcfks").toString(),
                OverwritePolicy.FAIL_IF_EXISTS, new char[0]);
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.TRUSTED_CERTIFICATE);

        PreflightReport report = Preflight.check(plan, entries, null);

        assertThat(report.hasBlockers()).isTrue();
        assertThat(report.findings())
                .extracting(PreflightFinding::code)
                .contains("BCFKS_EMPTY_PASSWORD");
    }

    @Test
    @DisplayName("Target file exists + FAIL_IF_EXISTS: BLOCK")
    void targetExistsFailIfExistsBlocks(@TempDir Path dir) throws IOException {
        Path src = Files.createFile(dir.resolve("src.jks"));
        Path tgt = Files.createFile(dir.resolve("tgt.jks"));
        ConversionPlan plan = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS,
                src.toString(), tgt.toString(),
                OverwritePolicy.FAIL_IF_EXISTS, "target".toCharArray());
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.TRUSTED_CERTIFICATE);

        PreflightReport report = Preflight.check(plan, entries, null);

        assertThat(report.hasBlockers()).isTrue();
        assertThat(report.findings())
                .extracting(PreflightFinding::code)
                .contains("TARGET_FILE_EXISTS");
    }

    @Test
    @DisplayName("Secret key in source + JKS target: WARN")
    void secretKeyOnJksTargetWarns(@TempDir Path dir) throws IOException {
        Path src = Files.createFile(dir.resolve("src.bcfks"));
        Path tgt = dir.resolve("tgt.jks");
        ConversionPlan plan = plan(
                KeyStoreContainerType.BCFKS, KeyStoreContainerType.JKS,
                src.toString(), tgt.toString(),
                OverwritePolicy.FAIL_IF_EXISTS, "target".toCharArray());
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.SECRET_KEY);

        PreflightReport report = Preflight.check(plan, entries, null);

        assertThat(report.hasBlockers()).isFalse();
        assertThat(report.findings())
                .extracting(PreflightFinding::code)
                .contains("SECRET_KEY_UNSUPPORTED_ON_JKS");
    }

    @Test
    @DisplayName("Happy path: no findings")
    void happyPathNoFindings(@TempDir Path dir) throws IOException {
        Path src = Files.createFile(dir.resolve("src.jks"));
        Path tgt = dir.resolve("tgt.bcfks");
        ConversionPlan plan = plan(
                KeyStoreContainerType.JKS, KeyStoreContainerType.BCFKS,
                src.toString(), tgt.toString(),
                OverwritePolicy.FAIL_IF_EXISTS, "target".toCharArray());
        Map<String, EntryType> entries = new HashMap<>();
        entries.put("a", EntryType.TRUSTED_CERTIFICATE);

        PreflightReport report = Preflight.check(plan, entries, null);

        assertThat(report.findings()).isEmpty();
    }
}