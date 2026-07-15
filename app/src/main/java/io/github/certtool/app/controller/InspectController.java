package io.github.certtool.app.controller;

import io.github.certtool.app.viewmodel.InspectViewModel;
import io.github.certtool.domain.inspect.InspectedKeyStore;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import java.security.cert.Certificate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure logic for the Inspect view. Holds no JavaFX references; the view binds to the
 * {@link InspectViewModel} this controller mutates.
 *
 * <p>Testable: callers can construct an {@code InspectController} with a fresh
 * {@code InspectViewModel} and exercise the selection / grouping logic without instantiating
 * JavaFX controls (see {@code InspectControllerTest}).
 */
public final class InspectController {

    private final InspectViewModel viewModel;

    public InspectController(InspectViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    /** Loads a result into the view-model. */
    public void onLoadResult(KeyStoreLoadResult result) {
        viewModel.setLoadResult(result);
    }

    /** Selects an alias — no-op if the alias is not present. */
    public void onSelectAlias(String alias) {
        if (alias == null) {
            return;
        }
        for (var node : viewModel.navNodes()) {
            if (Objects.equals(node.alias(), alias)) {
                viewModel.setSelectedAlias(alias);
                return;
            }
        }
    }

    /** Returns the selected entry's chain — view binds to this for the Chain tab. */
    public Optional<List<Certificate>> selectedChain() {
        return viewModel.selectedChain();
    }

    /** Returns the selected entry, if any. */
    public Optional<LoadedEntry> selectedEntry() {
        return viewModel.selectedEntry();
    }

    public InspectViewModel viewModel() {
        return viewModel;
    }

    /** Hands the inspection result to the view-model. Null is a no-op. */
    public void applyInspection(InspectedKeyStore inspected) {
        if (inspected == null) {
            viewModel.setInspected(null);
            return;
        }
        viewModel.setInspected(inspected);
    }
}