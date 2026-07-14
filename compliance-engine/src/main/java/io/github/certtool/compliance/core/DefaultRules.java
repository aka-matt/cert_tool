package io.github.certtool.compliance.core;

import io.github.certtool.compliance.rules.algorithm.AlgorithmRules;
import io.github.certtool.compliance.rules.certificate.CertificateRules;
import io.github.certtool.compliance.rules.container.ContainerRules;
import io.github.certtool.compliance.rules.runtime.RuntimeProviderRules;
import java.util.List;

/**
 * Aggregator that returns the canonical set of built-in rules (spec §7).
 *
 * <p>Used by the composition root to wire the default {@link RuleRegistry}. Tests and the UI may
 * also pass a smaller, custom registry to subset behaviour.
 */
public final class DefaultRules {

    private DefaultRules() {}

    /** Returns all built-in rules across every category in a deterministic order. */
    public static List<Rule> all() {
        return List.of(
                // CONTAINER
                ContainerRules.all(),
                // CERTIFICATE
                CertificateRules.all(),
                // ALGORITHM
                AlgorithmRules.all(),
                // RUNTIME PROVIDER
                RuntimeProviderRules.all())
                .stream()
                .flatMap(List::stream)
                .toList();
    }

    /** Convenience: builds a {@link RuleRegistry} containing all built-in rules. */
    public static RuleRegistry registry() {
        return RuleRegistry.of(all());
    }
}