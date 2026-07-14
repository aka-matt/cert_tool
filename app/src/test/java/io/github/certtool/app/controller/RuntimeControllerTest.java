package io.github.certtool.app.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.app.viewmodel.RuntimeViewModel;
import io.github.certtool.domain.context.ProviderInfo;
import io.github.certtool.domain.context.RuntimeEnvironment;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RuntimeController")
class RuntimeControllerTest {

    private static RuntimeEnvironment sampleEnv() {
        return new RuntimeEnvironment(
                "Adoptium", "17.0.11+9",
                "Linux", "amd64",
                List.of(
                        new ProviderInfo("SUN", "1.0.0", "SUN JCE Provider", false, false),
                        new ProviderInfo("BCFIPS", "1.0.0", "BC FIPS Provider", true, true)),
                Instant.parse("2026-07-14T00:00:00Z"));
    }

    @Test
    @DisplayName("refresh() captures the current runtime so the view-model exposes non-null fields")
    void refreshPopulatesViewModel() {
        RuntimeViewModel vm = new RuntimeViewModel();
        RuntimeController c = new RuntimeController(vm, RuntimeControllerTest::sampleEnv);
        c.refresh();
        assertThat(vm.getEnvironment()).isNotNull();
        assertThat(vm.getJvmVendor()).isEqualTo("Adoptium");
        assertThat(vm.getJvmVersion()).isEqualTo("17.0.11+9");
        assertThat(vm.providers()).hasSize(2);
        assertThat(vm.bcfipsDetected()).isTrue();
        assertThat(vm.approvedOnlyConfirmed()).isTrue();
    }

    @Test
    @DisplayName("approvedOnlyConfirmed is false when no provider confirms Approved-Only Mode")
    void approvedOnlyFalseWithoutConfirmation() {
        RuntimeEnvironment env = new RuntimeEnvironment(
                "Adoptium", "17",
                "Linux", "amd64",
                List.of(new ProviderInfo("SUN", "1.0.0", "SUN JCE Provider", false, false)),
                Instant.now());
        RuntimeViewModel vm = new RuntimeViewModel();
        RuntimeController c = new RuntimeController(vm, () -> env);
        c.refresh();
        assertThat(vm.bcfipsDetected()).isFalse();
        assertThat(vm.approvedOnlyConfirmed()).isFalse();
    }

    @Test
    @DisplayName("providerVersions returns the formatted 'name:version' list")
    void providerVersions() {
        RuntimeViewModel vm = new RuntimeViewModel();
        RuntimeController c = new RuntimeController(vm, RuntimeControllerTest::sampleEnv);
        c.refresh();
        assertThat(vm.providerVersions()).containsExactly("SUN:1.0.0", "BCFIPS:1.0.0");
    }
}
