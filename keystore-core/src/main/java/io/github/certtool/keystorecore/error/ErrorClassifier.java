package io.github.certtool.keystorecore.error;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.keystorecore.detection.ContainerDetector;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.UnrecoverableKeyException;

/**
 * Classifies low-level Java security exceptions into the typed {@link LoadFailureReason} enum.
 *
 * <p>Per spec §12, this classifier never parses Provider exception text as the sole signal — it
 * cross-checks the magic header to distinguish "wrong container" (CORRUPTED_KEYSTORE /
 * UNSUPPORTED_FORMAT) from "right container, can't open" (WRONG_STORE_PASSWORD, marked
 * INDETERMINATE because the integrity check fires regardless of password correctness).
 */
public final class ErrorClassifier {

    private ErrorClassifier() {}

    public static LoadFailureReason classifyStoreLoadFailure(
            Throwable cause, KeyStoreContainerType expected, byte[] bytes) {
        if (cause instanceof UnrecoverableKeyException) {
            return LoadFailureReason.WRONG_STORE_PASSWORD;
        }
        if (cause instanceof IOException) {
            // Magic-header check: if the bytes don't match the expected container at all,
            // the file is corrupted or the wrong format was selected.
            KeyStoreContainerType detected = ContainerDetector.detectContainer(bytes);
            if (detected != null && detected != expected) {
                return LoadFailureReason.CORRUPTED_KEYSTORE;
            }
            if (detected == null) {
                // Unknown magic → caller declared some format but bytes don't match any.
                // Either corrupted bytes or wrong format was selected.
                return LoadFailureReason.CORRUPTED_KEYSTORE;
            }
            // Magic matches expected: this is the classic indeterminate case.
            // JKS raises the integrity check failure regardless of password correctness.
            return LoadFailureReason.WRONG_STORE_PASSWORD;
        }
        if (cause instanceof GeneralSecurityException) {
            return LoadFailureReason.PROVIDER_UNAVAILABLE;
        }
        return LoadFailureReason.UNKNOWN;
    }

    public static LoadFailureReason classifyEntryLoadFailure(Throwable cause) {
        if (cause instanceof UnrecoverableKeyException) {
            return LoadFailureReason.WRONG_ENTRY_PASSWORD;
        }
        if (cause instanceof GeneralSecurityException) {
            return LoadFailureReason.UNSUPPORTED_ENTRY;
        }
        return LoadFailureReason.UNKNOWN;
    }

    /** Utility to safely attempt a probe without exposing the keystore instance. */
    public static boolean keystoreIsEmpty(KeyStore ks) throws Exception {
        return ks.size() == 0;
    }
}