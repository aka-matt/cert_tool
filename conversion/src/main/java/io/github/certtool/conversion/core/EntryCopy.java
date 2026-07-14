package io.github.certtool.conversion.core;

import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.keystorecore.password.PasswordProvider;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies entries from a source keystore into a target keystore, driven by {@link ConversionPlan}.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Iterates {@code plan.includedAliases()}; the resolved target aliases are passed in as
 *       {@code targetAliases} (same length, produced upstream by {@link AliasResolver}).</li>
 *   <li>For trusted-certificate entries: copies the certificate bytes to the target.</li>
 *   <li>For private-key entries: fetches the entry password from {@code sourcePasswords}, copies
 *       the key (and chain) to the target using {@code plan.targetStorePassword()} as the entry
 *       password. This is the standard JKS/BCFKS convention; per-entry target passwords are not
 *       part of the wizard model.</li>
 *   <li>For secret-key entries: copies the key with {@code plan.targetStorePassword()} as its
 *       password.</li>
 *   <li>UNKNOWN entries are skipped (the wizard would have flagged them already in preflight).</li>
 * </ul>
 *
 * <p>Passwords are zeroed after use per spec §2.
 */
public final class EntryCopy {

    private static final Logger LOG = LoggerFactory.getLogger(EntryCopy.class);

    private EntryCopy() {}

    /**
     * Copies entries into {@code target}. Returns a {@link CopyResult} with counts and warnings.
     *
     * @param source         the loaded source keystore
     * @param target         the loaded target keystore (will be mutated)
     * @param plan           the wizard plan
     * @param sourcePasswords password provider that knows source store + per-entry passwords
     * @param targetAliases  parallel list to {@code plan.includedAliases()}; alias at index i is the
     *                       resolved target alias for the source's i-th alias
     */
    public static CopyResult copy(
            KeyStore source,
            KeyStore target,
            ConversionPlan plan,
            PasswordProvider sourcePasswords,
            List<String> targetAliases) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourcePasswords, "sourcePasswords");
        Objects.requireNonNull(targetAliases, "targetAliases");
        if (targetAliases.size() != plan.includedAliases().size()) {
            throw new IllegalArgumentException(
                    "targetAliases.size() must equal plan.includedAliases().size()");
        }

        // Build a parallel map so we know the source entry password per alias. The plan's
        // entryPasswords list is optional and may be shorter than includedAliases — we treat any
        // missing entry as "no override, ask the password provider".
        Map<String, char[]> entryPwds = new HashMap<>();
        List<String> aliases = plan.includedAliases();
        List<char[]> planEntryPwds = plan.entryPasswords();
        for (int i = 0; i < aliases.size() && i < planEntryPwds.size(); i++) {
            char[] p = planEntryPwds.get(i);
            if (p != null) {
                entryPwds.put(aliases.get(i), p);
            }
        }

        int copied = 0;
        int skipped = 0;
        for (int i = 0; i < aliases.size(); i++) {
            String srcAlias = aliases.get(i);
            String tgtAlias = targetAliases.get(i);
            EntryType type = detect(source, srcAlias, entryPwds.get(srcAlias));
            if (type == null) {
                LOG.warn("Entry {}: cannot determine entry type; skipping", srcAlias);
                skipped++;
                continue;
            }
            try {
                switch (type) {
                    case TRUSTED_CERTIFICATE -> {
                        Certificate cert = source.getCertificate(srcAlias);
                        if (cert == null) {
                            skipped++;
                            continue;
                        }
                        target.setCertificateEntry(tgtAlias, cert);
                        copied++;
                    }
                    case PRIVATE_KEY -> {
                        char[] entryPwd = sourceEntryPassword(
                                sourcePasswords, srcAlias, entryPwds.get(srcAlias));
                        if (entryPwd == null) {
                            LOG.warn("Entry {}: source entry password unavailable; skipping", srcAlias);
                            skipped++;
                            continue;
                        }
                        Key key = source.getKey(srcAlias, entryPwd.clone());
                        Certificate[] chain = source.getCertificateChain(srcAlias);
                        if (key == null) {
                            skipped++;
                            continue;
                        }
                        char[] targetPwd = plan.targetStorePassword().clone();
                        target.setKeyEntry(tgtAlias, key, targetPwd, chain);
                        Arrays.fill(targetPwd, '\0');
                        copied++;
                    }
                    case SECRET_KEY -> {
                        char[] entryPwd = sourceEntryPassword(
                                sourcePasswords, srcAlias, entryPwds.get(srcAlias));
                        if (entryPwd == null) {
                            LOG.warn("Entry {}: source entry password unavailable; skipping", srcAlias);
                            skipped++;
                            continue;
                        }
                        Key key = source.getKey(srcAlias, entryPwd.clone());
                        if (key == null) {
                            skipped++;
                            continue;
                        }
                        char[] targetPwd = plan.targetStorePassword().clone();
                        target.setKeyEntry(tgtAlias, key, targetPwd, null);
                        Arrays.fill(targetPwd, '\0');
                        copied++;
                    }
                    case UNKNOWN -> {
                        LOG.warn("Entry {}: type UNKNOWN; skipping", srcAlias);
                        skipped++;
                    }
                }
            } catch (java.security.GeneralSecurityException e) {
                LOG.warn("Entry {}: copy failed: {}", srcAlias, e.toString());
                skipped++;
            } catch (RuntimeException e) {
                LOG.warn("Entry {}: unexpected failure: {}", srcAlias, e.toString());
                skipped++;
            }
        }
        return new CopyResult(copied, skipped);
    }

    /** Returns the source entry password, preferring the plan-supplied value over the prompt. */
    private static char[] sourceEntryPassword(PasswordProvider sourcePasswords, String alias, char[] fromPlan) {
        if (fromPlan != null && fromPlan.length > 0) {
            return fromPlan;
        }
        char[] pwd = sourcePasswords.requestEntryPassword(
                new io.github.certtool.keystorecore.password.EntryPasswordRequest(
                        "conversion-source", alias, EntryType.PRIVATE_KEY, 1, 1));
        return pwd == null ? null : pwd.clone();
    }

    /** Probes the entry type without throwing on bad-password keys. */
    private static EntryType detect(KeyStore ks, String alias, char[] entryPwd) {
        try {
            if (ks.isCertificateEntry(alias)) {
                return EntryType.TRUSTED_CERTIFICATE;
            }
            if (ks.isKeyEntry(alias)) {
                // Distinguish private vs secret by recovery.
                try {
                    char[] probe = entryPwd != null ? entryPwd.clone() : new char[0];
                    Key key = ks.getKey(alias, probe);
                    Arrays.fill(probe, '\0');
                    if (key == null) {
                        return EntryType.UNKNOWN;
                    }
                    String algorithm = key.getAlgorithm().toUpperCase();
                    return switch (algorithm) {
                        case "RSA", "EC", "DSA", "ED25519", "ED448" -> EntryType.PRIVATE_KEY;
                        default -> EntryType.SECRET_KEY;
                    };
                } catch (java.security.GeneralSecurityException e) {
                    return EntryType.UNKNOWN;
                }
            }
        } catch (java.security.KeyStoreException e) {
            // Fall through to UNKNOWN — caller will skip.
        }
        return EntryType.UNKNOWN;
    }

    /** Counts and per-entry warnings from a {@link #copy} run. */
    public record CopyResult(int copied, int skipped) {}
}