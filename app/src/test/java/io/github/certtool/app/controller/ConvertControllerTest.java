package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.viewmodel.ConvertViewModel;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ConvertController")
class ConvertControllerTest {

    private static LoadedKeyStoreInfo sampleSource() {
        return new LoadedKeyStoreInfo(KeyStoreContainerType.JKS, ContentEncoding.BINARY,
                "src.jks", 1024L, true, List.of("alpha", "beta", "gamma"));
    }

    @Test
    @DisplayName("onSourceSelected seeds the view-model with source path and pre-selects all aliases")
    void sourceSelectedSeedsAliases() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);
        c.onSourceSelected(sampleSource());

        assertThat(c.viewModel().getSourcePath()).isEqualTo("src.jks");
        assertThat(c.viewModel().selectedAliases())
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("onTargetChosen updates the target path")
    void targetChosen() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);
        Path target = Path.of("/tmp/out.jks");
        c.onTargetChosen(target);
        // Compare via Path.toString() on both sides so the assertion is platform-neutral:
        // Path.of("/tmp/...").toString() normalises to backslashes on Windows but keeps
        // forward slashes on Linux, matching what the controller stores.
        assertThat(c.viewModel().getTargetPath()).isEqualTo(target.toString());
    }

    @Test
    @DisplayName("onAliasToggle removed alias disappears from the selection")
    void aliasToggle() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);
        c.onSourceSelected(sampleSource());
        c.onAliasToggle("beta", false);
        assertThat(c.viewModel().selectedAliases()).containsExactly("alpha", "gamma");
    }

    @Test
    @DisplayName("onAliasToggle re-adds an alias")
    void aliasToggleAdd() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);
        c.onSourceSelected(sampleSource());
        c.onAliasToggle("beta", false);
        c.onAliasToggle("beta", true);
        assertThat(c.viewModel().selectedAliases()).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("onPreflightProduced stores the report so the wizard can render it")
    void preflightStored() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);
        PreflightReport report = new PreflightReport(List.of(
                new PreflightFinding(PreflightSeverity.WARN, "downgrade", null,
                        "JKS target weakens BCFKS source")));
        c.onPreflightProduced(report);
        assertThat(c.viewModel().getPreflightReport()).isSameAs(report);
        assertThat(c.viewModel().getPreflightReport().warningCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("alias conflict and overwrite policies default to safe values and can be changed")
    void policiesDefaultAndChanged() {
        ConvertViewModel vm = new ConvertViewModel();
        ConvertController c = new ConvertController(vm);

        assertThat(c.viewModel().getAliasConflictPolicy())
                .isEqualTo(AliasConflictPolicy.RENAME);
        assertThat(c.viewModel().getOverwritePolicy())
                .isEqualTo(OverwritePolicy.FAIL_IF_EXISTS);

        c.onAliasConflictPolicyChanged(AliasConflictPolicy.SKIP);
        c.onOverwritePolicyChanged(OverwritePolicy.OVERWRITE);
        assertThat(c.viewModel().getAliasConflictPolicy()).isEqualTo(AliasConflictPolicy.SKIP);
        assertThat(c.viewModel().getOverwritePolicy()).isEqualTo(OverwritePolicy.OVERWRITE);
    }
}
