package io.github.certtool.compliance.loader;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.certtool.compliance.loader.DefaultProfiles;
import io.github.certtool.compliance.loader.ProfileLoader;
import io.github.certtool.domain.profile.JksPrivateKeyPolicy;
import io.github.certtool.domain.profile.Profile;
import io.github.certtool.domain.profile.ProfileId;
import io.github.certtool.domain.profile.Sha1Policy;
import io.github.certtool.domain.profile.Standard;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProfileLoader")
class ProfileLoaderTest {

    @Test
    @DisplayName("loads the FIPS 140-3 default profile from resources")
    void loadFips1403() throws IOException {
        try (InputStream in = DefaultProfiles.fips1403()) {
            Profile p = ProfileLoader.fromJson(in);
            assertThat(p.id()).isEqualTo(ProfileId.FIPS_140_3_ASSESSMENT);
            assertThat(p.targetStandard()).isEqualTo(Standard.FIPS_140_3);
            assertThat(p.minimumKeySize("RSA")).isEqualTo(3072);
            assertThat(p.sha1Policy()).isEqualTo(Sha1Policy.DISALLOW);
            assertThat(p.jksPrivateKeyPolicy()).isEqualTo(JksPrivateKeyPolicy.FAIL);
            assertThat(p.requiredRuntimeProvider()).isEqualTo("BCFIPS");
            assertThat(p.approvedOnlyRequired()).isTrue();
        }
    }

    @Test
    @DisplayName("loads the FIPS 140-2 legacy default profile from resources")
    void loadFips1402() throws IOException {
        try (InputStream in = DefaultProfiles.fips1402Legacy()) {
            Profile p = ProfileLoader.fromJson(in);
            assertThat(p.id()).isEqualTo(ProfileId.FIPS_140_2_LEGACY_ASSESSMENT);
            assertThat(p.targetStandard()).isEqualTo(Standard.FIPS_140_2);
            assertThat(p.minimumKeySize("RSA")).isEqualTo(2048);
            assertThat(p.sha1Policy()).isEqualTo(Sha1Policy.WARN);
        }
    }

    @Test
    @DisplayName("rejects unknown profile id")
    void rejectsUnknownId() {
        String json = "{"
                + "\"id\":\"UNKNOWN_PROFILE\","
                + "\"name\":\"x\","
                + "\"targetStandard\":\"FIPS_140_3\","
                + "\"schemaVersion\":\"2026-01-01\","
                + "\"allowedAlgorithms\":[],"
                + "\"disallowedAlgorithms\":[],"
                + "\"minimumKeySizes\":{},"
                + "\"allowedCurves\":[],"
                + "\"sha1Policy\":\"DISALLOW\","
                + "\"expirationPolicy\":\"FAIL\","
                + "\"unknownAlgorithmPolicy\":\"NOT_ASSESSABLE\","
                + "\"jksPrivateKeyPolicy\":\"FAIL\","
                + "\"requiredRuntimeProvider\":\"BCFIPS\","
                + "\"approvedOnlyRequired\":true,"
                + "\"references\":[]"
                + "}";
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> {
            try (InputStream in = new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                ProfileLoader.fromJson(in);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}