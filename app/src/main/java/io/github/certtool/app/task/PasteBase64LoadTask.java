package io.github.certtool.app.task;

import io.github.certtool.domain.error.LoadFailure;
import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.keystorecore.input.Base64Decoder;
import io.github.certtool.keystorecore.input.InvalidBase64Exception;
import io.github.certtool.keystorecore.load.KeyStoreLoadResult;
import io.github.certtool.keystorecore.load.KeyStoreLoader;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.concurrent.Task;

/**
 * JavaFX {@link Task} that decodes pasted Base64 and loads the matching keystore container.
 *
 * <p>The pasted text and decoded bytes stay in memory and are never included in task messages or
 * failure details.
 */
public final class PasteBase64LoadTask extends Task<KeyStoreLoadResult> {

    private static final String LOAD_FAILURE_MESSAGE = "Could not determine the keystore container";

    private final KeyStoreLoader loader;
    private final String input;
    private final PasswordProvider passwordProvider;

    public PasteBase64LoadTask(KeyStoreLoader loader, String input, PasswordProvider passwordProvider) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.input = Objects.requireNonNull(input, "input");
        this.passwordProvider = Objects.requireNonNull(passwordProvider, "passwordProvider");
    }

    @Override
    protected KeyStoreLoadResult call() {
        tryMessage("Loading pasted keystore…");
        byte[] bytes;
        try {
            bytes = Base64Decoder.decode(input);
        } catch (InvalidBase64Exception e) {
            return failed(LoadFailureReason.INVALID_BASE64, "Invalid Base64 input");
        }

        try {
            KeyStoreLoadResult jks = loader.load(bytes, KeyStoreContainerType.JKS, passwordProvider);
            if (jks.isSuccess()) {
                return loaded(jks);
            }
            KeyStoreLoadResult bcfks = loader.load(bytes, KeyStoreContainerType.BCFKS, passwordProvider);
            if (bcfks.isSuccess()) {
                return loaded(bcfks);
            }
            return failed(LoadFailureReason.UNSUPPORTED_FORMAT, LOAD_FAILURE_MESSAGE);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private KeyStoreLoadResult loaded(KeyStoreLoadResult result) {
        tryMessage("Loaded " + result.entries().size() + " entries.");
        return result;
    }

    private KeyStoreLoadResult failed(LoadFailureReason reason, String message) {
        tryMessage("Load failed.");
        return new KeyStoreLoadResult(false, null, null, null, List.of(), LoadFailure.of(reason, message));
    }

    /** Updates the status when the JavaFX toolkit is available. */
    private void tryMessage(String message) {
        try {
            updateMessage(message);
        } catch (RuntimeException ignored) {
            // Headless calls do not initialize the JavaFX toolkit.
        }
    }
}
