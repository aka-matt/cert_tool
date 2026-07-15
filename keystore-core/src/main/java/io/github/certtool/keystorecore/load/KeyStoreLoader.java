package io.github.certtool.keystorecore.load;

import io.github.certtool.domain.error.LoadFailureReason;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.load.KeyFailure;
import io.github.certtool.domain.load.KeyStoreLoadResult;
import io.github.certtool.domain.load.LoadedEntry;
import io.github.certtool.keystorecore.detection.AutoDetector;
import io.github.certtool.keystorecore.error.ErrorClassifier;
import io.github.certtool.keystorecore.input.Base64Decoder;
import io.github.certtool.keystorecore.input.InvalidBase64Exception;
import io.github.certtool.keystorecore.password.EntryPasswordRequest;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.keystorecore.password.StorePasswordRequest;
import java.io.ByteArrayInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads JKS, BCFKS, and PKCS12 keystores from raw bytes (binary or Base64).
 *
 * <p>This is the central API of the {@code keystore-core} module. It encapsulates {@link KeyStore}
 * and {@link Provider} so the UI never sees them.
 *
 * <p>Passwords flow through the supplied {@link PasswordProvider}; nothing is logged (spec §2).
 * Wrong-password vs corrupted outcomes are classified by {@link ErrorClassifier}, with the
 * indeterminate caveat surfaced in the user-facing message (spec §12).
 */
public final class KeyStoreLoader {

    private static final Logger LOG = LoggerFactory.getLogger(KeyStoreLoader.class);

    private static final String BC_PROVIDER = BouncyCastleProvider.PROVIDER_NAME;

    private final LoadOptions options;

    public KeyStoreLoader() {
        this(LoadOptions.defaults());
    }

    public KeyStoreLoader(LoadOptions options) {
        this.options = options;
    }

    /** Loads bytes that are known to be a specific container type in a specific encoding. */
    public KeyStoreLoadResult load(
            byte[] bytes, KeyStoreContainerType container, PasswordProvider passwordProvider) {
        if (bytes == null || bytes.length == 0) {
            return KeyStoreLoadResult.failure(KeyFailure.of(LoadFailureReason.EMPTY_INPUT, "Input is empty"));
        }
        char[] storePassword = passwordProvider.requestStorePassword(
                new StorePasswordRequest("bytes", 1, options.maxPasswordAttempts()));
        if (storePassword == null) {
            return KeyStoreLoadResult.failure(KeyFailure.of(LoadFailureReason.CANCELLED, "Cancelled by user"));
        }
        try {
            KeyStore ks = newInstance(container);
            ks.load(new ByteArrayInputStream(bytes), storePassword);
            return finishLoad(container, ks, passwordProvider);
        } catch (UnrecoverableKeyException e) {
            // Exception surfaced during entry enumeration → classify as entry password failure.
            return KeyStoreLoadResult.failure(buildFailure(LoadFailureReason.WRONG_ENTRY_PASSWORD, e));
        } catch (Exception e) {
            LoadFailureReason reason = ErrorClassifier.classifyStoreLoadFailure(e, container, bytes);
            return KeyStoreLoadResult.failure(buildFailure(reason, e));
        } finally {
            Arrays.fill(storePassword, '\0');
        }
    }

    /**
     * Auto-detects the container type and encoding, then loads. Used by the UI's "Open" path when
     * the user has not told us what format to expect.
     */
    public KeyStoreLoadResult loadAutoDetect(byte[] input, PasswordProvider passwordProvider) {
        if (input == null || input.length == 0) {
            return KeyStoreLoadResult.failure(KeyFailure.of(LoadFailureReason.EMPTY_INPUT, "Input is empty"));
        }
        Optional<io.github.certtool.domain.keystore.KeyStoreDescriptor> detected =
                AutoDetector.detect(input);
        if (detected.isEmpty()) {
            return KeyStoreLoadResult.failure(KeyFailure.of(
                    LoadFailureReason.UNSUPPORTED_FORMAT,
                    "Could not detect container type or Base64 encoding"));
        }
        byte[] bytes;
        if (detected.get().encoding() == io.github.certtool.domain.keystore.ContentEncoding.BASE64) {
            try {
                bytes = Base64Decoder.decode(new String(input, java.nio.charset.StandardCharsets.UTF_8));
            } catch (InvalidBase64Exception e) {
                return KeyStoreLoadResult.failure(
                        KeyFailure.of(LoadFailureReason.INVALID_BASE64, "Invalid Base64 input"));
            }
        } else {
            bytes = input;
        }
        if (detected.get().container() == KeyStoreContainerType.JKS) {
            return load(bytes, KeyStoreContainerType.JKS, passwordProvider);
        }
        return loadDerCandidates(bytes, passwordProvider);
    }

    /**
     * Attempts the DER-based containers with one store-password prompt. BCFKS and PKCS12 both
     * start with an ASN.1 SEQUENCE, so the magic-byte detector cannot distinguish them safely.
     */
    private KeyStoreLoadResult loadDerCandidates(byte[] bytes, PasswordProvider passwordProvider) {
        char[] suppliedPassword = passwordProvider.requestStorePassword(
                new StorePasswordRequest("bytes", 1, options.maxPasswordAttempts()));
        if (suppliedPassword == null) {
            return KeyStoreLoadResult.failure(KeyFailure.of(LoadFailureReason.CANCELLED, "Cancelled by user"));
        }
        try (CloningPasswordProvider candidates = new CloningPasswordProvider(passwordProvider, suppliedPassword)) {
            KeyStoreLoadResult bcfks = load(bytes, KeyStoreContainerType.BCFKS, candidates);
            KeyStoreLoadResult pkcs12 = load(bytes, KeyStoreContainerType.PKCS12, candidates);
            if (bcfks.isSuccess() && !pkcs12.isSuccess()) {
                return bcfks;
            }
            if (pkcs12.isSuccess() && !bcfks.isSuccess()) {
                return pkcs12;
            }
            if (bcfks.isSuccess()) {
                return KeyStoreLoadResult.failure(KeyFailure.of(
                        LoadFailureReason.AMBIGUOUS_CONTAINER,
                        "The keystore could be loaded as more than one container type"));
            }
            return failedDerCandidate(bcfks, pkcs12);
        } finally {
            Arrays.fill(suppliedPassword, '\0');
        }
    }

    private static KeyStoreLoadResult failedDerCandidate(KeyStoreLoadResult bcfks, KeyStoreLoadResult pkcs12) {
        if (bcfks.failure().reason() == LoadFailureReason.WRONG_STORE_PASSWORD
                || pkcs12.failure().reason() == LoadFailureReason.WRONG_STORE_PASSWORD) {
            return KeyStoreLoadResult.failure(KeyFailure.of(
                    LoadFailureReason.WRONG_STORE_PASSWORD,
                    "Wrong password (or file is corrupted — indeterminate)"));
        }
        return KeyStoreLoadResult.failure(KeyFailure.of(
                LoadFailureReason.UNSUPPORTED_FORMAT, "Unsupported keystore format"));
    }

    private KeyStoreLoadResult finishLoad(KeyStoreContainerType container, KeyStore ks, PasswordProvider pw) throws Exception {
        Provider provider = ks.getProvider();
        String providerName = provider == null ? "?" : provider.getName();
        String providerVersion = provider == null ? "?" : provider.getVersionStr();
        List<LoadedEntry> entries = enumerate(container, ks, pw);
        return KeyStoreLoadResult.success(container, providerName, providerVersion, entries);
    }

    private List<LoadedEntry> enumerate(KeyStoreContainerType container, KeyStore ks, PasswordProvider pw) throws Exception {
        List<LoadedEntry> result = new ArrayList<>();
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            Date created = ks.getCreationDate(alias);
            if (ks.isCertificateEntry(alias)) {
                Certificate cert = ks.getCertificate(alias);
                result.add(LoadedEntry.trustedCertificate(alias, cert, created));
                continue;
            }
            if (ks.isKeyEntry(alias)) {
                try {
                    LoadedEntry entry = loadKeyEntry(container, ks, alias, created, pw);
                    result.add(entry);
                } catch (UnrecoverableKeyException e) {
                    // Per-entry password failure: surface a typed error without mangling the
                    // (already-loaded) store result. The test contract treats this as a failure.
                    if (options.skipUndecryptableEntries()) {
                        result.add(LoadedEntry.unknown(alias, "Entry password rejected; skipped"));
                        continue;
                    }
                    throw e;
                }
            }
        }
        return List.copyOf(result);
    }

    private LoadedEntry loadKeyEntry(
            KeyStoreContainerType container, KeyStore ks, String alias, Date created, PasswordProvider pw) throws Exception {
        // First, try without an entry password — JKS returns the key only if store==entry.
        Key resolved = null;
        try {
            resolved = ks.getKey(alias, null);
        } catch (UnrecoverableKeyException ignored) {
            // expected; fall through to prompting
        }
        if (resolved == null) {
            // Need to prompt via the PasswordProvider.
            EntryType hint = probeEntryType(ks, alias);
            char[] entryPwd = pw.requestEntryPassword(
                    new EntryPasswordRequest(container.name(), alias, hint, 1, options.maxPasswordAttempts()));
            if (entryPwd == null) {
                if (options.skipUndecryptableEntries()) {
                    return LoadedEntry.unknown(alias, "Entry password not provided; skipped");
                }
                return LoadedEntry.unknown(alias, "Entry password not provided");
            }
            try {
                resolved = ks.getKey(alias, entryPwd);
            } catch (UnrecoverableKeyException e) {
                Arrays.fill(entryPwd, '\0');
                if (options.skipUndecryptableEntries()) {
                    return LoadedEntry.unknown(alias, "Entry password rejected; skipped");
                }
                throw e;
            } finally {
                Arrays.fill(entryPwd, '\0');
            }
        }
        Certificate[] chain = ks.getCertificateChain(alias);
        List<Certificate> chainList = chain == null ? List.of() : List.of(chain);
        Integer keySize = null;
        if (resolved instanceof java.security.interfaces.RSAKey rsa) {
            keySize = rsa.getModulus().bitLength();
        }
        return new LoadedEntry(
                alias,
                classify(resolved),
                created,
                chainList,
                resolved == null ? null : resolved.getAlgorithm(),
                keySize,
                true,
                List.of());
    }

    private static EntryType probeEntryType(KeyStore ks, String alias) {
        Certificate[] chain;
        try {
            chain = ks.getCertificateChain(alias);
        } catch (java.security.KeyStoreException e) {
            // Treat chain lookup failures as no chain — the caller will hit a stronger error.
            return EntryType.UNKNOWN;
        }
        // If there is no chain, this is likely a secret key; if there is, likely a private key.
        // The provider can still override.
        return chain == null || chain.length == 0 ? EntryType.SECRET_KEY : EntryType.PRIVATE_KEY;
    }

    private static EntryType classify(Key resolved) {
        if (resolved instanceof java.security.PrivateKey) {
            return EntryType.PRIVATE_KEY;
        }
        if (resolved instanceof javax.crypto.SecretKey) {
            return EntryType.SECRET_KEY;
        }
        return EntryType.UNKNOWN;
    }

    private static KeyStore newInstance(KeyStoreContainerType container) throws Exception {
        ensureBouncyCastleRegistered();
        return switch (container) {
            case JKS, PKCS12 -> KeyStore.getInstance(container.name());
            case BCFKS -> KeyStore.getInstance(KeyStoreContainerType.BCFKS.name(), BC_PROVIDER);
        };
    }

    /** Supplies independently zeroable store-password copies to each DER candidate load. */
    private static final class CloningPasswordProvider implements PasswordProvider, AutoCloseable {
        private final PasswordProvider delegate;
        private final char[] storePassword;

        private CloningPasswordProvider(PasswordProvider delegate, char[] storePassword) {
            this.delegate = delegate;
            this.storePassword = storePassword.clone();
        }

        @Override
        public char[] requestStorePassword(StorePasswordRequest request) {
            return storePassword.clone();
        }

        @Override
        public char[] requestEntryPassword(EntryPasswordRequest request) {
            return delegate.requestEntryPassword(request);
        }

        @Override
        public void close() {
            Arrays.fill(storePassword, '\0');
        }
    }

    private static synchronized void ensureBouncyCastleRegistered() {
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private KeyFailure buildFailure(LoadFailureReason reason, Exception cause) {
        String user = switch (reason) {
            case WRONG_STORE_PASSWORD ->
                    "Wrong password (or file is corrupted — indeterminate)";
            case WRONG_ENTRY_PASSWORD -> "Wrong entry password";
            case CORRUPTED_KEYSTORE -> "KeyStore is corrupted or in an unsupported format";
            case PROVIDER_UNAVAILABLE -> "Required cryptographic provider is unavailable";
            case CANCELLED -> "Cancelled by user";
            case EMPTY_INPUT -> "Input is empty";
            case INVALID_BASE64 -> "Invalid Base64 input";
            case UNSUPPORTED_FORMAT -> "Unsupported keystore format";
            case UNSUPPORTED_ENTRY -> "Unsupported entry type";
            case FILE_TOO_LARGE -> "File exceeds the size limit";
            case FILE_NOT_FOUND -> "File not found";
            case FILE_NOT_READABLE -> "File is not readable";
            case ENTRY_PASSWORD_REQUIRED -> "Entry requires a password";
            default -> "Could not load keystore";
        };
        KeyFailure failure = KeyFailure.of(reason, user, cause);
        // For WRONG_STORE_PASSWORD, append the indeterminate caveat to the technical reason
        // so reports and logs are honest (spec §12).
        if (reason == LoadFailureReason.WRONG_STORE_PASSWORD) {
            return new KeyFailure(
                    reason, user + " [indeterminate: wrong password vs tampering]", cause);
        }
        return failure;
    }

    // Helper to keep the file compiling without an Arrays import above.
    private static final class Arrays {
        static void fill(char[] a, char v) {
            java.util.Arrays.fill(a, v);
        }
    }
}
