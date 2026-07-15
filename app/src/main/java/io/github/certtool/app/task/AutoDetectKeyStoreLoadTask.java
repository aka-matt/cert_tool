package io.github.certtool.app.task;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.load.KeyFailure;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import javafx.concurrent.Task;

/** JavaFX {@link Task} that detects and loads a keystore or truststore on a background thread. */
public final class AutoDetectKeyStoreLoadTask extends Task<KeyStoreLoadResult> {

    private final KeyStoreLoader loader;
    private final byte[] bytes;
    private final Path path;
    private final PasswordProvider passwordProvider;

    public AutoDetectKeyStoreLoadTask(KeyStoreLoader loader, byte[] bytes, PasswordProvider passwordProvider) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.path = null;
        this.passwordProvider = Objects.requireNonNull(passwordProvider, "passwordProvider");
    }

    /** Creates a task that reads the selected file only when the background task executes. */
    public AutoDetectKeyStoreLoadTask(KeyStoreLoader loader, Path path, PasswordProvider passwordProvider) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.bytes = null;
        this.path = Objects.requireNonNull(path, "path");
        this.passwordProvider = Objects.requireNonNull(passwordProvider, "passwordProvider");
    }

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading keystore or truststore…");
        if (path != null) {
            try {
                return load(Files.readAllBytes(path));
            } catch (NoSuchFileException e) {
                return readFailure(LoadFailureReason.FILE_NOT_FOUND);
            } catch (IOException | SecurityException e) {
                return readFailure(LoadFailureReason.FILE_NOT_READABLE);
            }
        }
        return load(bytes);
    }

    private KeyStoreLoadResult load(byte[] input) {
        KeyStoreLoadResult result = loader.loadAutoDetect(input, passwordProvider);
        tryMessage(result.isSuccess() ? "Loaded " + result.entries().size() + " entries." : "Load failed.");
        return result;
    }

    private KeyStoreLoadResult readFailure(LoadFailureReason reason) {
        tryMessage("Load failed.");
        return KeyStoreLoadResult.failure(KeyFailure.of(reason, "Could not read selected file"));
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
