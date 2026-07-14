package io.github.certtool.domain.context;

import java.util.List;
import java.util.Objects;

/**
 * The complete input to a single rule evaluation. A rule reads from a {@code RuleContext}, never
 * from the live keystore or system. This isolates rules from cryptography APIs (spec §7 boundary).
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code loadedKeyStore} — container facts (type, encoding, size, alias list).</li>
 *   <li>{@code entries} — per-alias analyses including parsed certificates and chain analysis.</li>
 *   <li>{@code runtime} — JVM/OS/provider facts for Runtime Provider rules.</li>
 * </ul>
 */
public record RuleContext(
        LoadedKeyStoreInfo loadedKeyStore,
        List<EntryAnalysis> entries,
        RuntimeEnvironment runtime) {

    public RuleContext {
        Objects.requireNonNull(loadedKeyStore, "loadedKeyStore");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(runtime, "runtime");
        entries = List.copyOf(entries);
    }

    /** True iff any entry is a private-key entry. */
    public boolean hasPrivateKeys() {
        return entries.stream().anyMatch(EntryAnalysis::isPrivateKey);
    }

    /** True iff any entry is a trusted certificate entry. */
    public boolean hasTrustedCertificates() {
        return entries.stream().anyMatch(EntryAnalysis::isTrustedCertificate);
    }

    /** True iff any entry is a secret-key entry. */
    public boolean hasSecretKeys() {
        return entries.stream().anyMatch(EntryAnalysis::isSecretKey);
    }
}