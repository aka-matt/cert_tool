package io.github.certtool.app.password;

import io.github.certtool.keystorecore.password.EntryPasswordRequest;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.keystorecore.password.StorePasswordRequest;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JavaFX-dialog-backed {@link PasswordProvider}.
 *
 * <p>The dialog-callback seam ({@code dialogFactory}) is a {@code BiFunction<String, String,
 * char[]>} that takes (title, header) and returns the entered password or null on cancel. Tests
 * supply a fake factory; production wires {@code (t, h) -> new PasswordDialog(t, h, owner).showAndWait()}.
 */
public final class JavaFxPasswordProvider implements PasswordProvider {

    private static final Logger LOG = LoggerFactory.getLogger(JavaFxPasswordProvider.class);

    private final BiFunction<String, String, char[]> dialogFactory;
    private final FxThreadDispatcher fxThreadDispatcher;

    public JavaFxPasswordProvider(BiFunction<String, String, char[]> dialogFactory) {
        this(dialogFactory, JavaFxPasswordProvider::callOnFxThread);
    }

    JavaFxPasswordProvider(
            BiFunction<String, String, char[]> dialogFactory, FxThreadDispatcher fxThreadDispatcher) {
        this.dialogFactory = Objects.requireNonNull(dialogFactory, "dialogFactory");
        this.fxThreadDispatcher = Objects.requireNonNull(fxThreadDispatcher, "fxThreadDispatcher");
    }

    @Override
    public char[] requestStorePassword(StorePasswordRequest request) {
        Objects.requireNonNull(request, "request");
        String title = "KeyStore Password";
        String header = buildHeader(request.sourceDescription(), "store password",
                request.attemptNumber(), request.maxAttempts());
        return showDialog(title, header);
    }

    @Override
    public char[] requestEntryPassword(EntryPasswordRequest request) {
        Objects.requireNonNull(request, "request");
        String title = "Entry Password";
        String header = buildHeader(
                request.sourceDescription() + " — alias: " + request.alias(),
                "entry password ("
                        + (request.entryType() == null ? "key" : request.entryType().name())
                        + ")",
                request.attemptNumber(),
                request.maxAttempts());
        char[] pwd = showDialog(title, header);
        if (pwd == null) {
            LOG.debug("User cancelled entry-password prompt for alias {}", request.alias());
        }
        return pwd;
    }

    private static String buildHeader(String source, String kind, int attempt, int maxAttempts) {
        return source + "\nEnter " + kind + " (attempt " + attempt + " of " + maxAttempts + ").";
    }

    private char[] showDialog(String title, String header) {
        return fxThreadDispatcher.call(() -> dialogFactory.apply(title, header));
    }

    private static char[] callOnFxThread(Supplier<char[]> action) {
        if (Platform.isFxApplicationThread()) {
            return action.get();
        }
        AtomicReference<char[]> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                completed.countDown();
            }
        });
        try {
            completed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for password dialog", e);
        }
        Throwable thrown = failure.get();
        if (thrown instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (thrown instanceof Error error) {
            throw error;
        }
        if (thrown != null) {
            throw new IllegalStateException("Password dialog failed", thrown);
        }
        return result.get();
    }

    @FunctionalInterface
    interface FxThreadDispatcher {
        char[] call(Supplier<char[]> action);
    }

    /** Zeroes a password array in place. Safe to call with null. */
    public static void zero(char[] pwd) {
        if (pwd != null) {
            Arrays.fill(pwd, '\0');
        }
    }

    /**
     * Builds a JavaFX dialog-backed provider anchored to the given owner {@code stage}. Calling
     * before the stage is showing will produce errors when the dialog tries to attach.
     */
    public static JavaFxPasswordProvider dialogProvider(javafx.stage.Window owner) {
        return new JavaFxPasswordProvider((title, header) ->
                PasswordDialog.show(owner, title, header));
    }
}
