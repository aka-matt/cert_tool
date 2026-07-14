package io.github.certtool.conversion.domain.plan;

import io.github.certtool.domain.keystore.ContentEncoding;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import java.util.List;
import java.util.Objects;

/**
 * Complete description of a conversion the user wants to perform. Holds every wizard-step decision
 * so the engine is fully driven by data — no UI state reaches the engine.
 *
 * <p>Passwords are stored as {@code char[]} for zero-on-demand scrubbing per spec §2.
 * Alias-include and per-entry password lists are parallel arrays: {@code includedAliases.get(i)}
 * corresponds to {@code entryPasswords.get(i)}. {@code entryPasswords} may be empty when
 * {@code includedAliases} contains only trusted-certificate entries (which have no key password).
 */
public record ConversionPlan(
        KeyStoreContainerType sourceContainerType,
        ContentEncoding sourceEncoding,
        KeyStoreContainerType targetContainerType,
        ContentEncoding targetEncoding,
        String sourcePath,
        String targetPath,
        char[] sourceStorePassword,
        char[] targetStorePassword,
        AliasConflictPolicy aliasConflictPolicy,
        OverwritePolicy overwritePolicy,
        List<String> includedAliases,
        List<char[]> entryPasswords) {

    public ConversionPlan {
        Objects.requireNonNull(sourceContainerType, "sourceContainerType");
        Objects.requireNonNull(sourceEncoding, "sourceEncoding");
        Objects.requireNonNull(targetContainerType, "targetContainerType");
        Objects.requireNonNull(targetEncoding, "targetEncoding");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(targetPath, "targetPath");
        Objects.requireNonNull(sourceStorePassword, "sourceStorePassword");
        Objects.requireNonNull(targetStorePassword, "targetStorePassword");
        Objects.requireNonNull(aliasConflictPolicy, "aliasConflictPolicy");
        Objects.requireNonNull(overwritePolicy, "overwritePolicy");
        Objects.requireNonNull(includedAliases, "includedAliases");
        Objects.requireNonNull(entryPasswords, "entryPasswords");
        includedAliases = List.copyOf(includedAliases);
        entryPasswords = List.copyOf(entryPasswords);
    }
}