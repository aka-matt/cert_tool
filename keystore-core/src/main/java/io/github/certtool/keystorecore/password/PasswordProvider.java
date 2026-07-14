package io.github.certtool.keystorecore.password;

/**
 * Abstract source of keystore and entry passwords.
 *
 * <p>The UI layer supplies a JavaFX-dialog-backed implementation; tests supply a fake such as
 * {@link FixedPasswordProvider}. Per spec §4, returning {@code null} from either method signals
 * that the user cancelled the prompt.
 */
public interface PasswordProvider {

    /**
     * Asks the user (or the fake) for the keystore's store password.
     *
     * @return the password, or {@code null} if the user cancelled
     */
    char[] requestStorePassword(StorePasswordRequest request);

    /**
     * Asks for the password of a single private-key or secret-key entry.
     *
     * @return the password, or {@code null} if the user cancelled
     */
    char[] requestEntryPassword(EntryPasswordRequest request);
}