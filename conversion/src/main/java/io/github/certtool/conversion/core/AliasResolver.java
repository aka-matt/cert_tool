package io.github.certtool.conversion.core;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves alias conflicts between incoming source entries and existing target entries.
 *
 * <p>This is a pure function over aliases — it does NOT touch the actual keystore. The orchestrator
 * calls it to produce a final mapping from "source alias" → "target alias" (under RENAME) or to
 * drop / overwrite (under SKIP / OVERWRITE).
 *
 * <p>The naming scheme under RENAME is {@code alias}, {@code alias-1}, {@code alias-2}, …
 */
public final class AliasResolver {

    private AliasResolver() {}

    /**
     * Returns the resolved target aliases for the given incoming aliases, given existing target
     * aliases.
     *
     * @param policy          the user's chosen conflict policy
     * @param incomingAliases aliases carried from the source
     * @param targetExisting  aliases that already exist on the target
     * @return the ordered list of aliases to write to the target
     */
    public static List<String> resolve(
            AliasConflictPolicy policy, List<String> incomingAliases, Set<String> targetExisting) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(incomingAliases, "incomingAliases");
        Objects.requireNonNull(targetExisting, "targetExisting");

        List<String> out = new ArrayList<>(incomingAliases.size());
        for (String alias : incomingAliases) {
            switch (policy) {
                case SKIP -> {
                    if (!targetExisting.contains(alias)) {
                        out.add(alias);
                    }
                }
                case RENAME -> out.add(rename(alias, targetExisting));
                case OVERWRITE -> out.add(alias);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Returns the renamed alias ({@code alias}, {@code alias-1}, …) that doesn't collide with
     * {@code taken}.
     */
    static String rename(String alias, Set<String> taken) {
        if (!taken.contains(alias)) {
            return alias;
        }
        for (int i = 1; i < 10_000; i++) {
            String candidate = alias + "-" + i;
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
        // Pathological fallback — extremely unlikely to reach in practice.
        return alias + "-" + System.nanoTime();
    }
}