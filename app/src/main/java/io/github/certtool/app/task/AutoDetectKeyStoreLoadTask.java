package io.github.certtool.app.task;

import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.util.Objects;
import javafx.concurrent.Task;

/** JavaFX {@link Task} that detects and loads a keystore or truststore on a background thread. */
public final class AutoDetectKeyStoreLoadTask extends Task<KeyStoreLoadResult> {

    private final KeyStoreLoader loader;
    private final byte[] bytes;
    private final PasswordProvider passwordProvider;

    public AutoDetectKeyStoreLoadTask(KeyStoreLoader loader, byte[] bytes, PasswordProvider passwordProvider) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.passwordProvider = Objects.requireNonNull(passwordProvider, "passwordProvider");
    }

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading keystore or truststore…");
        KeyStoreLoadResult result = loader.loadAutoDetect(bytes, passwordProvider);
        tryMessage(result.isSuccess() ? "Loaded " + result.entries().size() + " entries." : "Load failed.");
        return result;
    }

    /** Updates the task message without requiring an initialized FX toolkit for headless tests. */
    private void tryMessage(String message) {
        try {
            updateMessage(message);
        } catch (IllegalStateException ignored) {
            // Toolkit not initialized — fine for headless callers.
        }
    }
}
