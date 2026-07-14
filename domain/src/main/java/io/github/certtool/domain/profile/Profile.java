package io.github.certtool.domain.profile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable rule profile used by the assessment engine to drive FIPS compatibility checks.
 *
 * <p>A profile bundles:
 * <ul>
 *   <li>Identification (id, name, target standard, schema version).</li>
 *   <li>Algorithm posture: allowed/disallowed name lists, minimum key sizes per algorithm,
 *       allowed named curves, SHA-1 policy.</li>
 *   <li>Time posture: expiry policy.</li>
 *   <li>Container posture: JKS private-key handling.</li>
 *   <li>Runtime posture: required runtime provider name (e.g. {@code BCFIPS}) and whether
 *       Approved-Only Mode must be confirmed.</li>
 *   <li>Behaviour for unknown algorithms.</li>
 *   <li>External references (NIST publications, etc.).</li>
 * </ul>
 *
 * <p>Profiles are pure data — they carry no I/O or cryptographic behaviour. The
 * {@code compliance-engine} module evaluates them against a {@code RuleContext} to produce
 * findings.
 */
public record Profile(
        ProfileId id,
        String name,
        Standard targetStandard,
        String schemaVersion,
        List<String> allowedAlgorithms,
        List<String> disallowedAlgorithms,
        Map<String, Integer> minimumKeySizes,
        List<String> allowedCurves,
        Sha1Policy sha1Policy,
        ExpirationPolicy expirationPolicy,
        UnknownAlgorithmPolicy unknownAlgorithmPolicy,
        JksPrivateKeyPolicy jksPrivateKeyPolicy,
        String requiredRuntimeProvider,
        boolean approvedOnlyRequired,
        List<String> references) {

    public Profile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(targetStandard, "targetStandard");
        Objects.requireNonNull(schemaVersion, "schemaVersion");
        Objects.requireNonNull(allowedAlgorithms, "allowedAlgorithms");
        Objects.requireNonNull(disallowedAlgorithms, "disallowedAlgorithms");
        Objects.requireNonNull(minimumKeySizes, "minimumKeySizes");
        Objects.requireNonNull(allowedCurves, "allowedCurves");
        Objects.requireNonNull(sha1Policy, "sha1Policy");
        Objects.requireNonNull(expirationPolicy, "expirationPolicy");
        Objects.requireNonNull(unknownAlgorithmPolicy, "unknownAlgorithmPolicy");
        Objects.requireNonNull(jksPrivateKeyPolicy, "jksPrivateKeyPolicy");
        Objects.requireNonNull(references, "references");
        allowedAlgorithms = List.copyOf(allowedAlgorithms);
        disallowedAlgorithms = List.copyOf(disallowedAlgorithms);
        allowedCurves = List.copyOf(allowedCurves);
        references = List.copyOf(references);
        minimumKeySizes = Map.copyOf(minimumKeySizes);
    }

    /** Returns the minimum key size required for {@code algorithmName}, or null if not configured. */
    public Integer minimumKeySize(String algorithmName) {
        if (algorithmName == null) {
            return null;
        }
        return minimumKeySizes.get(algorithmName);
    }

    /** True iff this profile demands Approved-Only Mode be confirmed in the runtime provider. */
    public boolean requiresApprovedOnly() {
        return approvedOnlyRequired;
    }

    /** True iff this profile requires a specific runtime provider (e.g. {@code BCFIPS}). */
    public boolean requiresRuntimeProvider() {
        return requiredRuntimeProvider != null && !requiredRuntimeProvider.isBlank();
    }
}