package io.github.certtool.app.task;

import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import javafx.concurrent.Task;

/**
 * JavaFX {@link Task} that loads a keystore on a background thread.
 *
 * <p>The loader runs {@link KeyStoreLoader#load} which may invoke the {@link PasswordProvider} to
 * prompt the user — that prompt must run on the FX Application Thread (it's a modal dialog). The
 * loader handles that internally; the task itself never blocks the FX thread.
 */
public final class LoadKeyStoreTask extends Task<KeyStoreLoadResult> {

    private final KeyStoreLoader loader;
    private final byte[] bytes;
    private final KeyStoreContainerType container;
    private final PasswordProvider passwordProvider;

    public LoadKeyStoreTask(
            KeyStoreLoader loader,
            byte[] bytes,
            KeyStoreContainerType container,
            PasswordProvider passwordProvider) {
        this.loader = loader;
        this.bytes = bytes;
        this.container = container;
        this.passwordProvider = passwordProvider;
    }

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading keystore…");
        KeyStoreLoadResult r = loader.load(bytes, container, passwordProvider);
        if (r.isSuccess()) {
            tryMessage("Loaded " + r.entries().size() + " entries.");
        } else {
            tryMessage("Load failed.");
        }
        return r;
    }

    /**
     * Updates the task message, swallowing the "Toolkit not initialized" exception that fires
     * when {@code call()} is invoked from a headless test without booting the FX runtime. The
     * Task is still usable; only the message broadcast is skipped.
     */
    private void tryMessage(String msg) {
        try {
            updateMessage(msg);
        } catch (IllegalStateException ignored) {
            // toolkit not initialised — fine for headless callers
        }
    }
}