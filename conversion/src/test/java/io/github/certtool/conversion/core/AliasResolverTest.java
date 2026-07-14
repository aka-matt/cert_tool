package io.github.certtool.conversion.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.conversion.domain.plan.AliasConflictPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AliasResolver")
class AliasResolverTest {

    @Test
    @DisplayName("SKIP policy: existing alias is left out of the resolved list")
    void skipExistingAlias() {
        Set<String> targetExisting = new HashSet<>(List.of("a"));
        List<String> incoming = List.of("a", "b");
        List<String> resolved = AliasResolver.resolve(
                AliasConflictPolicy.SKIP, incoming, targetExisting);
        assertThat(resolved).containsExactly("b");
    }

    @Test
    @DisplayName("RENAME policy: existing alias is suffixed -1, -2, …")
    void renamesConflictingAlias() {
        Set<String> targetExisting = new HashSet<>(List.of("a", "a-1"));
        List<String> incoming = List.of("a");
        List<String> resolved = AliasResolver.resolve(
                AliasConflictPolicy.RENAME, incoming, targetExisting);
        assertThat(resolved).containsExactly("a-2");
    }

    @Test
    @DisplayName("OVERWRITE policy: alias stays unchanged even if it exists on target")
    void overwriteAliasIsKept() {
        Set<String> targetExisting = new HashSet<>(List.of("a"));
        List<String> incoming = List.of("a", "b");
        List<String> resolved = AliasResolver.resolve(
                AliasConflictPolicy.OVERWRITE, incoming, targetExisting);
        assertThat(resolved).containsExactly("a", "b");
    }

    @Test
    @DisplayName("Non-conflicting aliases are returned unchanged under any policy")
    void nonConflictingUnchanged() {
        Set<String> targetExisting = new HashSet<>(List.of("existing"));
        for (AliasConflictPolicy policy : AliasConflictPolicy.values()) {
            List<String> resolved = AliasResolver.resolve(
                    policy, List.of("fresh-1", "fresh-2"), targetExisting);
            assertThat(resolved).containsExactly("fresh-1", "fresh-2");
        }
    }
}