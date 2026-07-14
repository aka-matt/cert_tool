package io.github.certtool.app.task;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.input.Base64Decoder;
import io.github.certtool.keystorecore.input.InvalidBase64Exception;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.concurrent.Task;

/** Decodes and loads a pasted value using the container selected after an ambiguous probe. */
public final class SelectedBase64LoadTask extends Task<KeyStoreLoadResult> {

    private final KeyStoreProbe loader;
    private final String input;
    private final KeyStoreContainerType container;
    private final PasswordProvider passwordProvider;

    public SelectedBase64LoadTask(
            KeyStoreLoader loader,
            String input,
            KeyStoreContainerType container,
            PasswordProvider passwordProvider) {
        this(Objects.requireNonNull(loader, "loader")::load, input, container, passwordProvider);
    }

    SelectedBase64LoadTask(
            KeyStoreProbe loader,
            String input,
            KeyStoreContainerType container,
            PasswordProvider passwordProvider) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.input = Objects.requireNonNull(input, "input");
        this.container = Objects.requireNonNull(container, "container");
        this.passwordProvider = Objects.requireNonNull(passwordProvider, "passwordProvider");
    }

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading pasted keystore…");
        byte[] bytes;
        try {
            bytes = Base64Decoder.decode(input);
        } catch (InvalidBase64Exception e) {
            return failed(LoadFailureReason.INVALID_BASE64);
        }
        try {
            KeyStoreLoadResult result = loader.load(bytes, container, passwordProvider);
            tryMessage(result.isSuccess() ? "Loaded " + result.entries().size() + " entries." : "Load failed.");
            return result;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private KeyStoreLoadResult failed(LoadFailureReason reason) {
        tryMessage("Load failed.");
        return new KeyStoreLoadResult(false, null, null, null, List.of(), LoadFailure.of(reason, "Could not load pasted keystore"));
    }

    private void tryMessage(String message) {
        try {
            updateMessage(message);
        } catch (IllegalStateException ignored) {
            // Direct headless calls do not initialize the JavaFX toolkit.
        } catch (RuntimeException e) {
            if (!"No toolkit found".equals(e.getMessage())) {
                throw e;
            }
        }
    }

    @FunctionalInterface
    interface KeyStoreProbe {
        KeyStoreLoadResult load(byte[] bytes, KeyStoreContainerType container, PasswordProvider passwordProvider);
    }
}
