package io.github.certtool.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SanitizationGuard")
class SanitizationGuardTest {

    @Test
    @DisplayName("returns empty list when no forbidden patterns are present")
    void cleanOutputPasses() {
        String safe = "Assessment Report — generated 2026-07-14\nProfile: FIPS_140_3_ASSESSMENT\n";
        assertThat(SanitizationGuard.scan(safe, "test-source")).isEmpty();
    }

    @Test
    @DisplayName("flags a password-shaped substring (password=...)")
    void flagsPasswordEquals() {
        String poisoned = "evidence: user supplied password=hunter2 to keystore";
        List<SanitizationViolation> v = SanitizationGuard.scan(poisoned, "fingerprint-X");
        assertThat(v).isNotEmpty();
        assertThat(v).anySatisfy(found -> assertThat(found.match()).isEqualTo("password=hunter2"));
    }

    @Test
    @DisplayName("flags a password-shaped substring (password: ...)")
    void flagsPasswordColon() {
        String poisoned = "summary: password: hunter2 supplied";
        assertThat(SanitizationGuard.scan(poisoned, "src"))
                .anySatisfy(v -> assertThat(v.match()).isEqualTo("password: hunter2"));
    }

    @Test
    @DisplayName("flags BEGIN PRIVATE KEY marker (PEM private key)")
    void flagsBeginPrivateKey() {
        String poisoned = "excerpt:\n-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----\n";
        assertThat(SanitizationGuard.scan(poisoned, "src"))
                .anySatisfy(v -> assertThat(v.match()).isEqualTo("-----BEGIN PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("flags BEGIN RSA PRIVATE KEY marker")
    void flagsBeginRsaPrivateKey() {
        String poisoned = "-----BEGIN RSA PRIVATE KEY-----\n";
        assertThat(SanitizationGuard.scan(poisoned, "src"))
                .anySatisfy(v -> assertThat(v.match()).isEqualTo("-----BEGIN RSA PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("flags BEGIN ENCRYPTED PRIVATE KEY marker")
    void flagsBeginEncryptedPrivateKey() {
        String poisoned = "-----BEGIN ENCRYPTED PRIVATE KEY-----";
        assertThat(SanitizationGuard.scan(poisoned, "src"))
                .anySatisfy(v -> assertThat(v.match()).isEqualTo("-----BEGIN ENCRYPTED PRIVATE KEY-----"));
    }

    @Test
    @DisplayName("flags phrases that would falsely claim FIPS certification")
    void flagsCertificationClaims() {
        for (String forbidden : List.of(
                "FIPS Certification",
                "Official FIPS Validation",
                "NIST Certified",
                "正式认证结论")) {
            List<SanitizationViolation> v = SanitizationGuard.scan(forbidden, "src");
            assertThat(v)
                    .as("expected guard to flag: %s", forbidden)
                    .isNotEmpty();
        }
    }

    @Test
    @DisplayName("scan() is case-insensitive for the password marker")
    void scanIsCaseInsensitiveForPassword() {
        String poisoned = "evidence: Password=Hunter2 supplied";
        assertThat(SanitizationGuard.scan(poisoned, "src")).isNotEmpty();
    }

    @Test
    @DisplayName("enforce() throws RenderException listing every violation")
    void enforceThrowsOnViolation() {
        String poisoned = "password=hunter2 and -----BEGIN PRIVATE KEY-----\n";
        assertThatThrownBy(() -> SanitizationGuard.enforce(poisoned, "fingerprint-Y"))
                .isInstanceOf(RenderException.class)
                .hasMessageContaining("Sanitization guard rejected output")
                .hasMessageContaining("fingerprint-Y");
    }

    @Test
    @DisplayName("enforce() is silent on clean output")
    void enforceSilentOnClean() {
        String safe = "clean report body without any secrets";
        SanitizationGuard.enforce(safe, "src"); // must not throw
    }

    @Test
    @DisplayName("SanitizationViolation exposes matched substring and source label")
    void violationExposesDetails() {
        List<SanitizationViolation> v = SanitizationGuard.scan("password=hunter2", "src-123");
        assertThat(v).hasSize(1);
        SanitizationViolation first = v.get(0);
        assertThat(first.source()).isEqualTo("src-123");
        assertThat(first.match()).isEqualTo("password=hunter2");
        assertThat(first.offset()).isGreaterThanOrEqualTo(0);
    }
}