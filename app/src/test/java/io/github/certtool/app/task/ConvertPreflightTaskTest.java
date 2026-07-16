package io.github.certtool.app.task;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.conversion.core.Preflight;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConvertPreflightTaskTest {

    @BeforeAll
    static void initFx() {
        // Headless: same trick used by MainShellControllerTest.
        try { Platform.startup(() -> {}); } catch (IllegalStateException ignored) {}
    }

    @Test
    void callReturnsEmptyReportForSafePlan() throws Exception {
        var plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "/tmp/src.jks", "/tmp/tgt.bcfks",
                "s".toCharArray(), "t".toCharArray(),
                AliasConflictPolicy.RENAME, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of(new char[0]));
        var task = new ConvertPreflightTask(plan, Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
        PreflightReport r = task.call();
        assertThat(r.findings()).isEmpty();
        assertThat(r.hasBlockers()).isFalse();
    }

    @Test
    void callReturnsBlockerForBcfksTargetWithEmptyPassword() throws Exception {
        var plan = new ConversionPlan(
                KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                KeyStoreContainerType.BCFKS, ContentEncoding.BINARY,
                "/tmp/src.jks", "/tmp/tgt.bcfks",
                "s".toCharArray(), new char[0],
                AliasConflictPolicy.RENAME, OverwritePolicy.FAIL_IF_EXISTS,
                List.of("a"), List.of(new char[0]));
        var task = new ConvertPreflightTask(plan, Map.of("a", EntryType.TRUSTED_CERTIFICATE), null);
        PreflightReport r = task.call();
        assertThat(r.hasBlockers()).isTrue();
        assertThat(r.findings()).anyMatch(f -> "BCFKS_EMPTY_PASSWORD".equals(f.code()));
    }

    @Test
    void callDelegatesToExistingPreflightCheck() throws Exception {
        // Ensure the task is a thin wrapper — it doesn't apply private logic.
        var plan = PreflightSmokeFactory.nonTrivialPlan();
        var task = new ConvertPreflightTask(plan, Map.of("a", EntryType.PRIVATE_KEY), null);
        PreflightReport fromTask = task.call();
        PreflightReport fromEngine = Preflight.check(plan, Map.of("a", EntryType.PRIVATE_KEY),
                null);
        assertThat(fromTask.findings()).isEqualTo(fromEngine.findings());
    }
}