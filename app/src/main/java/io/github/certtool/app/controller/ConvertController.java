package io.github.certtool.app.controller;

import io.github.certtool.app.viewmodel.ConvertViewModel;
import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.domain.context.LoadedKeyStoreInfo;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Pure logic for the Convert wizard. Holds no JavaFX references; the views bind to the
 * {@link ConvertViewModel} this controller mutates.
 *
 * <p>The controller does NOT execute conversions — that runs in a background
 * {@code ConvertTask} which feeds {@link #onPreflightProduced(PreflightReport)} and the
 * eventual result back here.
 */
public final class ConvertController {

    private final ConvertViewModel viewModel;

    public ConvertController(ConvertViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /** Seeds the view-model with a freshly loaded source keystore. Pre-selects every alias. */
    public void onSourceSelected(LoadedKeyStoreInfo info) {
        if (info == null) {
            viewModel.setSource(null);
            viewModel.sourceAliases().clear();
            viewModel.selectedAliases().clear();
            return;
        }
        viewModel.setSource(info);
        viewModel.setSourcePath(info.sourcePath());
        viewModel.sourceAliases().setAll(info.aliases());
        viewModel.selectedAliases().setAll(info.aliases());
    }

    /** Sets the target output path. */
    public void onTargetChosen(Path path) {
        viewModel.setTargetPath(path == null ? "" : path.toString());
    }

    /**
     * Adds or removes an alias from the selection. Re-adding restores the alias to its position in
     * the source's original ordering so the list stays sorted the way the inspector shows it.
     */
    public void onAliasToggle(String alias, boolean included) {
        if (alias == null) {
            return;
        }
        if (included) {
            if (!viewModel.selectedAliases().contains(alias)) {
                int idx = viewModel.sourceAliases().indexOf(alias);
                if (idx >= 0 && idx < viewModel.selectedAliases().size()) {
                    viewModel.selectedAliases().add(idx, alias);
                } else {
                    viewModel.selectedAliases().add(alias);
                }
            }
        } else {
            viewModel.selectedAliases().remove(alias);
        }
    }

    /** Stores the preflight report so the wizard can render findings before step 5. */
    public void onPreflightProduced(PreflightReport report) {
        viewModel.setPreflightReport(report);
    }

    /** Updates the conflict resolution policy. */
    public void onAliasConflictPolicyChanged(AliasConflictPolicy policy) {
        if (policy != null) {
            viewModel.setAliasConflictPolicy(policy);
        }
    }

    /** Updates the on-disk overwrite policy. */
    public void onOverwritePolicyChanged(OverwritePolicy policy) {
        if (policy != null) {
            viewModel.setOverwritePolicy(policy);
        }
    }

    public ConvertViewModel viewModel() { return viewModel; }
}
