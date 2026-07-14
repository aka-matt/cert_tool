package io.github.certtool.keystorecore.password;

import java.util.Collections;
import java.util.Map;

/**
 * Test-only {@link PasswordProvider} that returns a preconfigured password without prompting.
 *
 * <p>The constructor takes a store password and an optional per-alias entry password map. Returning
 * {@code null} from any of the configuration values makes the corresponding request return
 * {@code null} — which the loader interprets as a user cancel.
 */
public final class FixedPasswordProvider implements PasswordProvider {

    private final char[] storePassword;
    private final Map<String, char[]> entryPasswords;

    public FixedPasswordProvider(char[] storePassword, Map<String, char[]> entryPasswords) {
        this.storePassword = storePassword == null ? null : storePassword.clone();
        Map<String, char[]> copy = new java.util.HashMap<>();
        if (entryPasswords != null) {
            entryPasswords.forEach((k, v) -> copy.put(k, v == null ? null : v.clone()));
        }
        this.entryPasswords = Collections.unmodifiableMap(copy);
    }

    @Override
    public char[] requestStorePassword(StorePasswordRequest request) {
        return storePassword == null ? null : storePassword.clone();
    }

    @Override
    public char[] requestEntryPassword(EntryPasswordRequest request) {
        char[] pw = entryPasswords.get(request.alias());
        return pw == null ? null : pw.clone();
    }
}