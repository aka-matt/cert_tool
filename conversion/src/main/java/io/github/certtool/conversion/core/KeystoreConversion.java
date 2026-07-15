package io.github.certtool.conversion.core;

import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.result.ConversionResult;
import io.github.certtool.conversion.domain.result.ReloadVerification;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.keystorecore.password.PasswordProvider;
import io.github.certtool.testfixtures.KeyStoreGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Security;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the conversion engine: load source → preflight → entry copy → atomic write →
 * reload verification (spec §9 wizard step 5).
 *
 * <p>This is the seam the UI calls. It is intentionally synchronous and exception-driven so the
 * wizard can show progress; long-running work belongs on a background thread upstream.
 */
public final class KeystoreConversion {

    private static final Logger LOG = LoggerFactory.getLogger(KeystoreConversion.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private KeystoreConversion() {}

    /** Creates an empty target keystore of the requested container type, unlocked with the password. */
    private static KeyStore createEmpty(KeyStoreContainerType type, char[] pwd) throws GeneralSecurityException, IOException {
        KeyStore ks = switch (type) {
            case JKS, PKCS12 -> KeyStore.getInstance(type.name());
            case BCFKS -> KeyStore.getInstance(type.name(), BouncyCastleProvider.PROVIDER_NAME);
        };
        ks.load(null, pwd);
        return ks;
    }

    /**
     * Runs the conversion described by {@code plan} and returns a {@link ConversionResult}.
     *
     * @throws PreflightBlockedException if preflight reports one or more BLOCK findings
     * @throws IOException               on source-read, atomic-write, or verify failures
     * @throws GeneralSecurityException  on keystore errors
     */
    public static ConversionResult execute(
            ConversionPlan plan, Profile profile, PasswordProvider sourcePasswords)
            throws IOException, GeneralSecurityException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourcePasswords, "sourcePasswords");

        // 1. Load source keystore from its file (delegated to test-fixtures helper which throws
        //    the broad Exception; we catch and rethrow as GeneralSecurityException or IOException
        //    so callers don't need to declare catch-all).
        Path sourcePath = Path.of(plan.sourcePath());
        Path targetPath = Path.of(plan.targetPath());
        byte[] sourceBytes = Files.readAllBytes(sourcePath);
        KeyStore source;
        try {
            source = KeyStoreGenerator.loadBytes(
                    sourceBytes, plan.sourceContainerType(), plan.sourceStorePassword().clone());
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to load source keystore", e);
        }

        // 2. Probe entry types for preflight.
        Map<String, EntryType> sourceEntries = probeEntryTypes(source);

        // 3. Preflight.
        PreflightReport preflight = Preflight.check(plan, sourceEntries, profile);
        if (preflight.hasBlockers()) {
            throw new PreflightBlockedException(preflight);
        }

        // 4. Resolve target aliases (target is empty for first-time writes).
        Set<String> targetExisting = Collections.emptySet();
        List<String> targetAliases = AliasResolver.resolve(
                plan.aliasConflictPolicy(), plan.includedAliases(), targetExisting);

        // 5. Create empty target keystore.
        KeyStore target = createEmpty(plan.targetContainerType(), plan.targetStorePassword().clone());

        // 6. Copy entries.
        EntryCopy.copy(source, target, plan, sourcePasswords, targetAliases);

        // 7. Serialize target to bytes.
        byte[] targetBytes;
        try {
            targetBytes = KeyStoreGenerator.toBytes(target, plan.targetStorePassword().clone());
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to serialize target keystore", e);
        }

        // 8. Atomic write.
        long written = AtomicWriter.write(targetPath, targetBytes);

        // 9. Reload and verify.
        ReloadVerification verification = reloadAndVerify(source, targetPath, plan);

        return new ConversionResult(plan, preflight, verification, targetPath.toString(), written);
    }

    private static Map<String, EntryType> probeEntryTypes(KeyStore source) throws GeneralSecurityException {
        Map<String, EntryType> out = new HashMap<>();
        Enumeration<String> aliases = source.aliases();
        while (aliases.hasMoreElements()) {
            String a = aliases.nextElement();
            try {
                if (source.isCertificateEntry(a)) {
                    out.put(a, EntryType.TRUSTED_CERTIFICATE);
                } else if (source.isKeyEntry(a)) {
                    Key key = source.getKey(a, new char[0]);
                    if (key == null) {
                        out.put(a, EntryType.UNKNOWN);
                        continue;
                    }
                    String alg = key.getAlgorithm().toUpperCase();
                    out.put(a, switch (alg) {
                        case "RSA", "EC", "DSA", "ED25519", "ED448" -> EntryType.PRIVATE_KEY;
                        default -> EntryType.SECRET_KEY;
                    });
                } else {
                    out.put(a, EntryType.UNKNOWN);
                }
            } catch (GeneralSecurityException e) {
                out.put(a, EntryType.UNKNOWN);
            }
        }
        return out;
    }

    private static ReloadVerification reloadAndVerify(
            KeyStore source, Path targetPath, ConversionPlan plan)
            throws IOException, GeneralSecurityException {
        byte[] writtenTargetBytes = Files.readAllBytes(targetPath);
        KeyStore reloaded;
        try {
            reloaded = KeyStoreGenerator.loadBytes(
                    writtenTargetBytes, plan.targetContainerType(), plan.targetStorePassword().clone());
        } catch (java.io.IOException e) {
            throw e;
        } catch (Exception e) {
            throw new GeneralSecurityException("Failed to reload written target keystore", e);
        }

        int sourceAliasCount = count(source);
        int targetAliasCount = count(reloaded);
        int sourceCertCount = certificateCount(source);
        int targetCertCount = certificateCount(reloaded);
        boolean fingerprints = fingerprintsMatch(source, reloaded);

        return new ReloadVerification(true, sourceAliasCount, targetAliasCount,
                sourceCertCount, targetCertCount, fingerprints);
    }

    private static int count(KeyStore ks) throws GeneralSecurityException {
        int n = 0;
        Enumeration<String> e = ks.aliases();
        while (e.hasMoreElements()) {
            e.nextElement();
            n++;
        }
        return n;
    }

    private static int certificateCount(KeyStore ks) throws GeneralSecurityException {
        int n = 0;
        Enumeration<String> e = ks.aliases();
        while (e.hasMoreElements()) {
            String alias = e.nextElement();
            Certificate cert = ks.getCertificate(alias);
            if (cert != null) {
                n++;
            }
            Certificate[] chain = ks.getCertificateChain(alias);
            if (chain != null) {
                n += chain.length - 1; // avoid double-counting the leaf
            }
        }
        return n;
    }

    private static boolean fingerprintsMatch(KeyStore a, KeyStore b) {
        try {
            MessageDigest mdA = MessageDigest.getInstance("SHA-256");
            MessageDigest mdB = MessageDigest.getInstance("SHA-256");
            Set<String> aFps = new HashSet<>();
            Set<String> bFps = new HashSet<>();
            Enumeration<String> ea = a.aliases();
            while (ea.hasMoreElements()) {
                String alias = ea.nextElement();
                Certificate cert = a.getCertificate(alias);
                if (cert != null) {
                    aFps.add(toHex(mdA.digest(cert.getEncoded())));
                }
            }
            Enumeration<String> eb = b.aliases();
            while (eb.hasMoreElements()) {
                String alias = eb.nextElement();
                Certificate cert = b.getCertificate(alias);
                if (cert != null) {
                    bFps.add(toHex(mdB.digest(cert.getEncoded())));
                }
            }
            return aFps.equals(bFps);
        } catch (Exception e) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Surfaces a helper used by orchestration tests and keystore-core alike.
    @SuppressWarnings("unused")
    private static List<String> aliasesOf(KeyStore ks) throws GeneralSecurityException {
        List<String> list = new ArrayList<>();
        Enumeration<String> e = ks.aliases();
        while (e.hasMoreElements()) {
            list.add(e.nextElement());
        }
        return list;
    }

    // Reserved: provider selection currently delegates to KeyStoreGenerator; helper stays to keep
    // the API surface stable when we move selection into this module.
    @SuppressWarnings("unused")
    private static String providerFor(KeyStoreContainerType type) {
        return type == KeyStoreContainerType.BCFKS
                ? BouncyCastleProvider.PROVIDER_NAME
                : "SUN";
    }
}
