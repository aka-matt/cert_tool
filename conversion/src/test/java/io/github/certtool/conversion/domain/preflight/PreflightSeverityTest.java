package io.github.certtool.conversion.domain.preflight;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.conversion.domain.result.ReloadVerification;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Preflight and plan types")
class PreflightSeverityTest {

    @Test
    @DisplayName("ConversionPlan: constructs with required fields")
    void constructsPlan() {
        char[] src = "src".toCharArray();
        char[] tgt = "tgt".toCharArray();
        ConversionPlan p = new ConversionPlan(
                KeyStoreContainerType.JKS,
                ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS,
                ContentEncoding.BINARY,
                "/tmp/source.jks",
                "/tmp/target.bcfks",
                src,
                tgt,
                AliasConflictPolicy.RENAME,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a", "b"),
                List.of());
        assertThat(p.aliasConflictPolicy()).isEqualTo(AliasConflictPolicy.RENAME);
        assertThat(p.includedAliases()).containsExactly("a", "b");
    }

    @Test
    @DisplayName("ConversionPlan: rejects null target path")
    void rejectsNullTargetPath() {
        char[] src = "src".toCharArray();
        char[] tgt = "tgt".toCharArray();
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "/tmp/src.jks", null,
                src, tgt,
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of(), List.of()));
    }

    @Test
    @DisplayName("PreflightFinding: constructs with severity, code, alias, message")
    void findingConstructs() {
        PreflightFinding f = new PreflightFinding(
                PreflightSeverity.BLOCK,
                "BCFKS_TO_JKS_DOWNGRADE",
                "a",
                "BCFKS source with private keys cannot be converted to JKS under FIPS profile.");
        assertThat(f.severity()).isEqualTo(PreflightSeverity.BLOCK);
        assertThat(f.code()).isEqualTo("BCFKS_TO_JKS_DOWNGRADE");
        assertThat(f.alias()).isEqualTo("a");
    }

    @Test
    @DisplayName("PreflightReport: blockerCount counts only BLOCK findings")
    void blockerCount() {
        PreflightReport r = new PreflightReport(List.of(
                new PreflightFinding(PreflightSeverity.BLOCK, "X", null, "block"),
                new PreflightFinding(PreflightSeverity.WARN, "Y", null, "warn"),
                new PreflightFinding(PreflightSeverity.WARN, "Z", null, "warn")));
        assertThat(r.blockerCount()).isEqualTo(1);
        assertThat(r.warningCount()).isEqualTo(2);
        assertThat(r.hasBlockers()).isTrue();
    }

    @Test
    @DisplayName("PreflightReport: no blockers")
    void noBlockers() {
        PreflightReport r = new PreflightReport(List.of(
                new PreflightFinding(PreflightSeverity.WARN, "X", null, "warn")));
        assertThat(r.hasBlockers()).isFalse();
        assertThat(r.blockerCount()).isZero();
    }

    @Test
    @DisplayName("ConversionResult: constructs with all fields")
    void resultConstructs() {
        ConversionPlan p = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "/tmp/s.jks", "/tmp/t.bcfks",
                "s".toCharArray(), "t".toCharArray(),
                AliasConflictPolicy.SKIP,
                OverwritePolicy.FAIL_IF_EXISTS,
                List.of(), List.of());
        PreflightReport pre = new PreflightReport(List.of());
        ReloadVerification v = new ReloadVerification(true, 5, 5, 5, 5, true);
        ConversionResult r = new ConversionResult(p, pre, v, "/tmp/t.bcfks", 12345L);
        assertThat(r.targetPath()).isEqualTo("/tmp/t.bcfks");
        assertThat(r.verification().reloadSucceeded()).isTrue();
        assertThat(r.writtenBytes()).isEqualTo(12345L);
    }
}