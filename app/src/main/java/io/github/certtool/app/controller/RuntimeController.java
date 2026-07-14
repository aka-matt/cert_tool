package io.github.certtool.app.controller;

import io.github.certtool.app.viewmodel.RuntimeViewModel;
import io.github.certtool.domain.context.RuntimeEnvironment;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Controller for the Runtime page. Pulls a {@link RuntimeEnvironment} snapshot from a
 * {@link Supplier} (the live JVM by default, a fake in tests) and pushes it into the view-model.
 *
 * <p>Pure logic — no JavaFX dependency in the controller body. The view binds to
 * {@link RuntimeViewModel}'s read-only properties.
 */
public final class RuntimeController {

    private final RuntimeViewModel viewModel;
    private final Supplier<RuntimeEnvironment> environmentSource;

    /** Production constructor — pulls the environment from the live JVM. */
    public RuntimeController(RuntimeViewModel viewModel) {
        this(viewModel, RuntimeController::captureLiveEnvironment);
    }

    /** Test seam — caller supplies the environment snapshot. */
    public RuntimeController(RuntimeViewModel viewModel, Supplier<RuntimeEnvironment> environmentSource) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.environmentSource = Objects.requireNonNull(environmentSource, "environmentSource");
    }

    /** Re-runs the source and replaces the view-model contents. */
    public void refresh() {
        RuntimeEnvironment env = environmentSource.get();
        viewModel.setEnvironment(env);
    }

    public RuntimeViewModel viewModel() { return viewModel; }

    /** Captures the live JVM environment — delegates to a static so production code is trivial. */
    private static RuntimeEnvironment captureLiveEnvironment() {
        return io.github.certtool.app.platform.RuntimeInspector.capture();
    }
}
