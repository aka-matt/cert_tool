package io.github.certtool.testfixtures;

import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * Utility for building JKS and BCFKS keystores in tests.
 *
 * <p>Supports multi-alias keystores with distinct entry passwords, trusted-certificate entries,
 * private-key entries, and secret-key entries. Round-trip serialization to binary and Base64 is
 * exposed for use by later phases' conversion and report tests.
 */
public final class KeyStoreGenerator {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private KeyStoreGenerator() {}

    /** Returns a JKS keystore containing a single trusted-certificate entry. */
    public static KeyStore jks(char[] storePassword, String alias, X509Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.JKS.name());
        ks.load(null, storePassword);
        ks.setCertificateEntry(alias, cert);
        return ks;
    }

    /** Returns a JKS keystore containing a single private-key entry with the given entry password. */
    public static KeyStore jks(
            char[] storePassword, String alias, PrivateKey privateKey, char[] entryPassword, X509Certificate cert)
            throws Exception {
        return jksBuilder(storePassword)
                .addPrivateKey(alias, privateKey, entryPassword, List.of(cert))
                .build();
    }

    /** Returns a JKS keystore containing a single secret-key entry. */
    public static KeyStore jks(char[] storePassword, String alias, Key secret, char[] entryPassword) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.JKS.name());
        ks.load(null, storePassword);
        ks.setKeyEntry(alias, secret, entryPassword, null);
        return ks;
    }

    /** Returns a BCFKS keystore containing a single trusted-certificate entry. */
    public static KeyStore bcfks(char[] storePassword, String alias, X509Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.BCFKS.name(), BouncyCastleProvider.PROVIDER_NAME);
        ks.load(null, storePassword);
        ks.setCertificateEntry(alias, cert);
        return ks;
    }

    /** Returns a PKCS12 keystore containing a single trusted-certificate entry. */
    public static KeyStore pkcs12(char[] storePassword, String alias, X509Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.PKCS12.name());
        ks.load(null, storePassword);
        ks.setCertificateEntry(alias, cert);
        return ks;
    }

    /** Returns a BCFKS keystore containing a single private-key entry with the given entry password. */
    public static KeyStore bcfks(
            char[] storePassword, String alias, PrivateKey privateKey, char[] entryPassword, X509Certificate cert)
            throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.BCFKS.name(), BouncyCastleProvider.PROVIDER_NAME);
        ks.load(null, storePassword);
        ks.setKeyEntry(alias, privateKey, entryPassword, new Certificate[]{cert});
        return ks;
    }

    /** Returns a BCFKS keystore containing a single secret-key entry. */
    public static KeyStore bcfks(char[] storePassword, String alias, Key secret, char[] entryPassword) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.BCFKS.name(), BouncyCastleProvider.PROVIDER_NAME);
        ks.load(null, storePassword);
        ks.setKeyEntry(alias, secret, entryPassword, null);
        return ks;
    }

    /** Starts a fluent JKS builder. */
    public static JksBuilder jksBuilder(char[] storePassword) {
        return new JksBuilder(storePassword);
    }

    /** Writes the keystore to bytes using the given store password. */
    public static byte[] toBytes(KeyStore keystore, char[] storePassword) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keystore.store(out, storePassword);
        return out.toByteArray();
    }

    /** Writes the keystore to a Base64-encoded text (MIME-style with line breaks). */
    public static String toBase64(KeyStore keystore, char[] storePassword) throws Exception {
        return Base64.getMimeEncoder().encodeToString(toBytes(keystore, storePassword));
    }

    /** Loads a keystore from bytes. */
    public static KeyStore loadBytes(byte[] bytes, KeyStoreContainerType type, char[] storePassword) throws Exception {
        KeyStore ks = newInstance(type);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            ks.load(in, storePassword);
        }
        return ks;
    }

    /** Loads a keystore from Base64 text. */
    public static KeyStore loadBase64(String base64, KeyStoreContainerType type, char[] storePassword) throws Exception {
        return loadBytes(Base64.getMimeDecoder().decode(base64), type, storePassword);
    }

    private static KeyStore newInstance(KeyStoreContainerType type) throws Exception {
        return switch (type) {
            case JKS, PKCS12 -> KeyStore.getInstance(type.name());
            case BCFKS -> KeyStore.getInstance(
                    KeyStoreContainerType.BCFKS.name(), BouncyCastleProvider.PROVIDER_NAME);
        };
    }

    /**
     * Detects the user-visible {@link EntryType} of an alias in a keystore.
     *
     * <p>For {@code PRIVATE_KEY} entries the entry password is required; pass {@code null} only if
     * you are certain the alias is not a private-key entry.
     */
    public static EntryType detectEntryType(KeyStore ks, String alias, char[] entryPassword) throws Exception {
        if (ks.isCertificateEntry(alias)) {
            return EntryType.TRUSTED_CERTIFICATE;
        }
        if (ks.isKeyEntry(alias)) {
            try {
                Key k = ks.getKey(alias, entryPassword);
                if (k instanceof PrivateKey) {
                    return EntryType.PRIVATE_KEY;
                }
                if (k instanceof javax.crypto.SecretKey) {
                    return EntryType.SECRET_KEY;
                }
            } catch (UnrecoverableKeyException e) {
                return EntryType.UNKNOWN;
            }
        }
        return EntryType.UNKNOWN;
    }

    /** Fluent builder for multi-alias JKS keystores. */
    public static final class JksBuilder {
        private final char[] storePassword;
        private final Set<String> aliases = new HashSet<>();
        private final List<PendingEntry> pending = new ArrayList<>();

        private JksBuilder(char[] storePassword) {
            this.storePassword = storePassword.clone();
        }

        public JksBuilder addTrustedCertificate(String alias, X509Certificate cert) {
            checkAlias(alias);
            pending.add(new PendingEntry(alias, cert, null, null, null));
            return this;
        }

        public JksBuilder addPrivateKey(
                String alias, PrivateKey key, char[] entryPassword, List<X509Certificate> chain) {
            checkAlias(alias);
            pending.add(new PendingEntry(alias, null, key, entryPassword, chain));
            return this;
        }

        public KeyStore build() throws Exception {
            KeyStore ks = KeyStore.getInstance(KeyStoreContainerType.JKS.name());
            ks.load(null, storePassword);
            for (PendingEntry e : pending) {
                if (e.cert != null && e.key == null) {
                    ks.setCertificateEntry(e.alias, e.cert);
                } else if (e.key != null) {
                    Certificate[] chain = e.chain == null ? null : e.chain.toArray(new Certificate[0]);
                    ks.setKeyEntry(e.alias, e.key, e.entryPassword, chain);
                }
            }
            return ks;
        }

        private void checkAlias(String alias) {
            if (!aliases.add(alias)) {
                throw new IllegalArgumentException("Duplicate alias: " + alias);
            }
        }

        private static final class PendingEntry {
            final String alias;
            final X509Certificate cert;
            final PrivateKey key;
            final char[] entryPassword;
            final List<X509Certificate> chain;

            PendingEntry(
                    String alias,
                    X509Certificate cert,
                    PrivateKey key,
                    char[] entryPassword,
                    List<X509Certificate> chain) {
                this.alias = alias;
                this.cert = cert;
                this.key = key;
                this.entryPassword = entryPassword == null ? null : entryPassword.clone();
                this.chain = chain;
            }
        }
    }
}
