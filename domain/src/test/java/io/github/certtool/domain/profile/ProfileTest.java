package io.github.certtool.domain.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Profile")
class ProfileTest {

    @Test
    @DisplayName("constructs with all required fields")
    void constructs() {
        Profile p = new Profile(
                ProfileId.FIPS_140_3_ASSESSMENT,
                "FIPS 140-3 Compatibility Assessment",
                Standard.FIPS_140_3,
                "2026-01-01",
                List.of("RSA", "EC"),
                List.of("MD2", "MD5"),
                Map.of("RSA", 3072),
                List.of("P-256", "P-384"),
                Sha1Policy.DISALLOW,
                ExpirationPolicy.FAIL,
                UnknownAlgorithmPolicy.NOT_ASSESSABLE,
                JksPrivateKeyPolicy.FAIL,
                "BCFIPS",
                true,
                List.of("https://csrc.nist.gov/projects/fips-140-3"));

        assertThat(p.id()).isEqualTo(ProfileId.FIPS_140_3_ASSESSMENT);
        assertThat(p.minimumKeySize("RSA")).isEqualTo(3072);
        assertThat(p.requiresApprovedOnly()).isTrue();
    }

    @Test
    @DisplayName("rejects null id")
    void rejectsNullId() {
        assertThatThrownBy(() -> new Profile(
                null, "name", Standard.FIPS_140_3, "2026-01-01",
                List.of(), List.of(), Map.of(), List.of(),
                Sha1Policy.DISALLOW, ExpirationPolicy.FAIL,
                UnknownAlgorithmPolicy.NOT_ASSESSABLE,
                JksPrivateKeyPolicy.FAIL, "BCFIPS", true, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("minimumKeySize returns null for unconfigured algorithms")
    void unknownAlgorithmReturnsNull() {
        Profile p = new Profile(
                ProfileId.CUSTOM, "custom", Standard.FIPS_140_3, "2026-01-01",
                List.of(), List.of(), Map.of(), List.of(),
                Sha1Policy.DISALLOW, ExpirationPolicy.FAIL,
                UnknownAlgorithmPolicy.NOT_ASSESSABLE,
                JksPrivateKeyPolicy.FAIL, "BCFIPS", true, List.of());

        assertThat(p.minimumKeySize("RSA")).isNull();
    }
}