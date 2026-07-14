package io.github.certtool.conversion.core;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import io.github.certtool.conversion.domain.plan.ConversionPlan;
import io.github.certtool.conversion.domain.plan.OverwritePolicy;
import io.github.certtool.conversion.domain.preflight.PreflightFinding;
import io.github.certtool.conversion.domain.preflight.PreflightReport;
import io.github.certtool.conversion.domain.preflight.PreflightSeverity;
import io.github.certtool.domain.keystore.EntryType;
import io.github.certtool.domain.keystore.KeyStoreContainerType;
import io.github.certtool.domain.profile.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Plan-level validation for a {@link ConversionPlan} (wizard step 4 in spec §9).
 *
 * <p>This stage does NOT load the source keystore. It checks:
 * <ul>
 *   <li>Container policy: BCFKS → JKS private-key downgrade is BLOCK under FIPS, WARN otherwise.</li>
 *   <li>Password policy: BCFKS targets reject empty passwords.</li>
 *   <li>Filesystem policy: target file existence vs {@link OverwritePolicy}.</li>
 *   <li>Entry-shape policy: secret keys cannot survive a → JKS conversion.</li>
 *   <li>Alias conflict policy + overwrite policy consistency.</li>
 * </ul>
 *
 * <p>Returned {@link PreflightReport} is purely informational. The orchestrator decides what to do
 * with BLOCK findings. The engine never throws on a block; it reports it.
 */
public final class Preflight {

    private Preflight() {}

    /**
     * Runs plan-level checks against the user-supplied decision and (already-loaded) source entries.
     *
     * @param plan       the conversion plan
     * @param sourceEntries map of alias → {@link EntryType} for entries the user opted to include
     * @param profile    the active FIPS profile (may be {@code null} for "no FIPS evaluation")
     */
    public static PreflightReport check(
            ConversionPlan plan, Map<String, EntryType> sourceEntries, Profile profile) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(sourceEntries, "sourceEntries");
        List<PreflightFinding> findings = new ArrayList<>();

        // 1) BCFKS → JKS with private keys: BLOCK under FIPS, WARN otherwise.
        if (plan.sourceContainerType() == KeyStoreContainerType.BCFKS
                && plan.targetContainerType() == KeyStoreContainerType.JKS
                && hasAny(sourceEntries, EntryType.PRIVATE_KEY)) {
            boolean fips = isFipsLikeProfile(profile);
            findings.add(new PreflightFinding(
                    fips ? PreflightSeverity.BLOCK : PreflightSeverity.WARN,
                    "BCFKS_TO_JKS_PRIVATE_KEY_DOWNGRADE",
                    null,
                    "BCFKS source contains private-key entries and target is JKS. JKS uses a "
                            + "weak integrity check, BCFKS uses AES-256 with HMAC-SHA-256."));
        }

        // 2) BCFKS target with empty password: BLOCK.
        if (plan.targetContainerType() == KeyStoreContainerType.BCFKS && isBlank(plan.targetStorePassword())) {
            findings.add(new PreflightFinding(
                    PreflightSeverity.BLOCK,
                    "BCFKS_EMPTY_PASSWORD",
                    null,
                    "BCFKS target requires a non-empty store password."));
        }

        // 3) Target file exists + FAIL_IF_EXISTS policy: BLOCK.
        if (Files.exists(Path.of(plan.targetPath()))
                && plan.overwritePolicy() == OverwritePolicy.FAIL_IF_EXISTS) {
            findings.add(new PreflightFinding(
                    PreflightSeverity.BLOCK,
                    "TARGET_FILE_EXISTS",
                    null,
                    "Target file exists and overwrite policy is FAIL_IF_EXISTS."));
        }

        // 4) Secret keys + JKS target: WARN (won't survive copy).
        if (plan.targetContainerType() == KeyStoreContainerType.JKS
                && hasAny(sourceEntries, EntryType.SECRET_KEY)) {
            String aliases = sourceEntries.entrySet().stream()
                    .filter(e -> e.getValue() == EntryType.SECRET_KEY)
                    .map(Map.Entry::getKey)
                    .toList()
                    .toString();
            findings.add(new PreflightFinding(
                    PreflightSeverity.WARN,
                    "SECRET_KEY_UNSUPPORTED_ON_JKS",
                    null,
                    "JKS does not support secret-key entries; they will be dropped: " + aliases));
        }

        // 5) Alias conflict policy + small aliases without entries.
        if (plan.aliasConflictPolicy() == AliasConflictPolicy.OVERWRITE
                && plan.overwritePolicy() == OverwritePolicy.FAIL_IF_EXISTS) {
            findings.add(new PreflightFinding(
                    PreflightSeverity.INFO,
                    "ALIAS_OVERWRITE_INTERACTION",
                    null,
                    "Alias conflict policy OVERWRITE combined with overwrite policy "
                            + "FAIL_IF_EXISTS only applies to the file, not to alias collisions."));
        }

        return new PreflightReport(findings);
    }

    private static boolean hasAny(Map<String, EntryType> entries, EntryType want) {
        for (EntryType t : entries.values()) {
            if (t == want) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlank(char[] pwd) {
        if (pwd == null || pwd.length == 0) {
            return true;
        }
        for (char c : pwd) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Heuristic that the profile is FIPS-like (we treat any profile that mandates a runtime
     * provider OR Approved-Only Mode as FIPS-like). Avoids hard coupling to a specific ProfileId —
     * a CUSTOM profile can still trip this rule by configuring {@code requiredRuntimeProvider}.
     */
    private static boolean isFipsLikeProfile(Profile profile) {
        if (profile == null) {
            return false;
        }
        return profile.requiresRuntimeProvider() || profile.requiresApprovedOnly();
    }

    // Reserved API surface for future rules (chain integrity, content encoding sanity) — no-op for now.
    @SuppressWarnings("unused")
    private static Set<String> aliasesOf(Map<String, EntryType> entries) {
        return entries.keySet();
    }

    @SuppressWarnings("unused")
    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }
}