package io.github.certtool.compliance.core;

import io.github.certtool.compliance.rules.RuleCategory;
import java.util.List;
import java.util.Objects;

/**
 * Immutable collection of {@link Rule}s with category filtering. Constructed once by the
 * composition root and shared across assessments.
 */
public final class RuleRegistry {

    private final List<Rule> rules;

    private RuleRegistry(List<Rule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    /** Returns a new registry wrapping the supplied rules. */
    public static RuleRegistry of(List<Rule> rules) {
        return new RuleRegistry(rules);
    }

    /** Returns the registered rules in insertion order. */
    public List<Rule> rules() {
        return rules;
    }

    /** Returns the subset of rules whose {@link Rule#category()} equals {@code category}. */
    public List<Rule> inCategory(RuleCategory category) {
        Objects.requireNonNull(category, "category");
        return rules.stream().filter(r -> r.category() == category).toList();
    }
}